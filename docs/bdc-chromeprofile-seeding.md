# Booking.com Chrome profile seeding (warm session)

Runbook for keeping a durable Chrome `user-data-dir` so Selenium can use Booking.com extranet **without** solving AWS WAF captcha / SMS 2FA on every run.

Last validated: July 2026 (CRH on `botpressvm`, multi-property BDC account).

## Why this exists

Booking.com extranet login is protected by **AWS WAF** (not PerimeterX). A cold automated login often requires:

1. Visual captcha
2. SMS 2FA (and sometimes a second auth-assurance SMS)

A **warm Chrome profile** (cookies + WAF tokens) skips captcha and 2FA on subsequent opens. That is the production strategy; we do **not** automate AWS WAF via 2captcha for this flow.

VCC discovery uses Selenium + the fresa API after login:

`GET /fresa/extranet/payments/vccs_to_charge?hotel_id=…&ses=…`

Property targeting uses DB option `hbo_bdc_hotel_id` (not whatever hotel the session landed on).

## Important constraints

| Constraint | Implication |
|------------|-------------|
| Mac ≠ Linux profiles | Never copy a Mac `chromeprofile` into Ubuntu Docker. Seed **on the target OS**. |
| One profile dir ≠ four concurrent Chromes | Each processor gets its **own copy** of the profile. Sharing one mount across CRH/HSH/RMB/LSH will lock/corrupt Chrome. |
| Multi-property BDC user | One login covers all four hostels. Seed once, then **copy** the tree to the other property folders. |
| Groups home vs property home | Warm entry URL is `…/groups/home/index.html`. Bare `…/manage/home.html` (missing/stale `ses`) can return **HTTP 400**. `BookingComSeleniumScraper.doLogin` always opens/saves groups home. |
| Headless still uses the profile | Prod Chrome options use `--headless` + `user-data-dir=/app/chromeprofile`. Seeding is done headed (noVNC); runtime can stay headless. |

## Layout on the production host

Repo root (e.g. `~/littlehotelier` on `botpressvm`):

```text
chromeprofile/
  crh/    → mounted at /app/chromeprofile in crh-processor
  hsh/    → hsh-processor
  rmb/    → rmb-processor
  lsh/    → lsh-processor
```

Compose mounts (see `docker-compose.yml`):

```yaml
- ./chromeprofile/crh:/app/chromeprofile
```

(and the same pattern for hsh / rmb / lsh).

Base config (`application.properties`):

```properties
chromescraper.driver.options=user-data-dir=/app/chromeprofile --headless ...
```

Local Mac overrides (uncommitted `application-*-properties`) may point at a Mac path for desktop testing — **do not** deploy those to Docker.

## Zero-downtime seed, brief cutover

Seed in a **separate** container while prod processors keep running. Only attaching the volume (recreate processor) needs a short restart.

```text
mkdir host dirs
  → one-off seed container + noVNC
  → manual captcha / 2FA in Chrome
  → quit Chrome cleanly
  → copy profile to other properties
  → deploy compose mounts + recreate processors (brief downtime)
```

---

## Full seed procedure (Ubuntu Docker host)

### 1. Create host directories

```bash
cd ~/littlehotelier   # or wherever docker-compose.yml lives
mkdir -p chromeprofile/crh
# appuser in the image is typically uid 1000
sudo chown -R 1000:1000 chromeprofile/crh
ls -la chromeprofile/
```

Do **not** restart processors yet.

### 2. Start a one-off seed container (prod untouched)

Uses the existing `crh-processor` **image**, overrides the command so it does **not** start `RunProcessor`, installs a virtual display + noVNC, mounts the CRH profile dir.

```bash
docker compose run --rm --no-deps --user root \
  --name crh-chrome-seed \
  -v "$(pwd)/chromeprofile/crh:/app/chromeprofile" \
  -p 6080:6080 \
  -e DISPLAY=:99 \
  crh-processor \
  bash -lc '
    set -e
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y --no-install-recommends xvfb x11vnc novnc websockify openbox fonts-liberation

    Xvfb :99 -screen 0 1920x1080x24 -ac &
    sleep 1
    openbox &
    x11vnc -display :99 -forever -shared -nopw -listen 0.0.0.0 -rfbport 5900 -xkb &
    websockify --web=/usr/share/novnc 6080 localhost:5900 &

    chown -R appuser:appuser /app/chromeprofile
    echo "noVNC ready on port 6080"
    sleep infinity
  '
```

Leave this terminal running.

**Note:** Microsoft Remote Desktop is RDP, not VNC. noVNC is viewed in a **browser** (no VNC client required).

### 3. Open the desktop from your laptop

```bash
ssh -L 6080:localhost:6080 botpressvm   # use your real host/alias
```

Browser: [http://localhost:6080/vnc.html](http://localhost:6080/vnc.html) → Connect (no password).

### 4. Start Chrome with the seed profile

In a **second** SSH session on the host:

```bash
docker exec -u appuser -e DISPLAY=:99 crh-chrome-seed \
  google-chrome-stable \
  --user-data-dir=/app/chromeprofile \
  --no-sandbox \
  --disable-dev-shm-usage \
  --start-maximized \
  "https://admin.booking.com/hotel/hoteladmin/groups/home/index.html"
```

In noVNC:

1. Log in (captcha + SMS 2FA as required).
2. Confirm you land on **groups home** (multi-property).
3. Optionally open a property and **Manage virtual cards** to confirm access.
4. **Quit Chrome fully** (menu → Quit), do not only close the tab.

### 5. Verify the profile on disk

```bash
ls -la ~/littlehotelier/chromeprofile/crh | head
du -sh ~/littlehotelier/chromeprofile/crh
```

Expect on the order of **~100MB+** (e.g. ~175MB was observed), with `Default/`, `Local State`, etc.

Optional warm check: start Chrome again with the same `docker exec` command; you should already be logged in (no captcha / 2FA). Quit Chrome again.

### 6. Stop the seed container

```bash
docker stop crh-chrome-seed
docker ps -a | grep crh-chrome-seed || echo "seed container gone"
docker ps --format '{{.Names}}' | grep processor   # prod still up
```

### 7. Copy to the other properties (same BDC user)

```bash
cd ~/littlehotelier
for p in hsh rmb lsh; do
  rm -rf "chromeprofile/$p"
  cp -a chromeprofile/crh "chromeprofile/$p"
  rm -f "chromeprofile/$p/SingletonLock" \
        "chromeprofile/$p/SingletonSocket" \
        "chromeprofile/$p/SingletonCookie"
done
rm -f chromeprofile/crh/SingletonLock \
      chromeprofile/crh/SingletonSocket \
      chromeprofile/crh/SingletonCookie

du -sh chromeprofile/*
```

Four similar-sized trees. Remove Singleton* locks whenever you copy or Chrome may refuse to start.

---

## Cutover (attach volumes to processors)

Requires `docker-compose.yml` mounts and (for new BDC Selenium jobs / fresa VCC code) a rebuilt image.

**CRH only first** (short downtime for that service):

```bash
cd ~/littlehotelier
du -sh chromeprofile/*

docker compose build crh-processor
docker compose up -d --force-recreate crh-processor

docker ps --filter name=crh-processor
docker exec crh-processor ls -la /app/chromeprofile | head
```

Then the same for `hsh-processor`, `rmb-processor`, `lsh-processor` when ready.

Ensure each DB has `hbo_bdc_hotel_id` set for that property before relying on `getAllVCCBookingsThatCanBeCharged`.

## Keep-warm job

`BDCSeleniumVerifyLoginJob` (`com.macbackpackers.jobs.BDCSeleniumVerifyLoginJob`) calls `BookingComSeleniumScraper.doLogin` against the Chrome pool. Schedule it like other verify jobs (DB `JobScheduler` / manual insert).

- Success: groups home loads; session stays warm.
- Failure (redirect to sign-in / captcha): treat as cold profile → re-run this seed runbook; do **not** expect unattended captcha solve.

Existing `BDCVerifyLoginJob` still warms **HtmlUnit** `bdc.cookies` only — it does **not** refresh the Chrome profile.

## Code pointers

| Piece | Location |
|-------|----------|
| Selenium login + groups home | `BookingComSeleniumScraper.doLogin` |
| VCC list via fresa JSON | `BookingComSeleniumScraper.getAllVCCBookingsThatCanBeCharged` |
| Property id | WP/DB option `hbo_bdc_hotel_id` |
| Chrome options / profile path | `application.properties` → `chromescraper.driver.options` |
| Volume mounts | `docker-compose.yml` |
| Keep-warm job | `BDCSeleniumVerifyLoginJob` |

## Re-seed triggers

Re-run the seed procedure when:

- Captcha or sign-in appears again in verify job / manual Chrome
- Profile dir was deleted or a Mac profile was mistakenly copied in
- Chrome major upgrade repeatedly breaks the profile (rare; try re-seed on Linux first)

## Related decisions (2026)

- PerimeterX is gone from this login HAR; AWS WAF is the bot gate.
- Prefer durable warm profile over 2captcha Amazon WAF automation.
- Production VCC discovery may still use Cloudbeds until Selenium path is wired into `CreatePrepaidChargeJob`; the scrape method itself is Selenium + fresa.
