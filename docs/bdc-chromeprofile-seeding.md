# Booking.com Chrome profile seeding (warm session)

Runbook for keeping a durable Chrome `user-data-dir` so Selenium can use Booking.com extranet with fewer AWS WAF / SMS 2FA challenges.

Last validated: July 2026 (CRH on `botpressvm`, multi-property BDC account).

## Why this exists

Booking.com extranet login is protected by **AWS WAF** (not PerimeterX). A cold automated login often requires:

1. Visual captcha
2. SMS 2FA (and sometimes a second auth-assurance SMS)

Captchas also appear frequently when **viewing credit card details** on secure-admin, even if login itself was warm.

**Production strategy (layered):**

1. **Warm Chrome profile** (cookies + WAF tokens) — preferred; skips most challenges.
2. **Automated cold recovery** — `BookingComSeleniumScraper` detects AWS WAF, solves via 2captcha (`AmazonTask` / `AmazonTaskProxyless`), then completes SMS/phone 2FA with existing `hbo_bdc_2facode`.
3. **Manual seed** (this runbook) — first-time profile creation, OS mismatch, or solver/2FA outage.

Prefer configuring `hbo_2captcha_proxy` so egress matches Chrome’s public IP (`AmazonTask`); proxyless works but has higher token-rejection risk on Booking.com.

Login captchas currently use AWS WAF **jsapi** (`awswaf-captcha` custom element + `captcha-sdk.awswaf.com/.../jsapi.js`), not only the older `#captcha-container` / `gokuProps` form. Detection and solve cover both.

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

`--entrypoint bash` is required: the image `ENTRYPOINT` (`docker-entrypoint.sh`) always drops to `appuser` via `runuser`, so `apt-get` would fail with `Permission denied` even if you pass `--user root`.

If a previous seed container still exists, remove it first: `docker rm -f crh-chrome-seed`.

```bash
docker compose run --rm --no-deps --user root \
  --entrypoint bash \
  --name crh-chrome-seed \
  -v "$(pwd)/chromeprofile/crh:/app/chromeprofile" \
  -p 6080:6080 \
  -e DISPLAY=:99 \
  crh-processor \
  -lc '
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

In a **second** SSH session on the host.

If Chrome refuses to start with *“profile appears to be in use by another Google Chrome process”*, you have stale `Singleton*` lock files from a previous container (e.g. a stopped processor). Confirm no Chrome is running, then remove them:

```bash
# Should show no real chrome process (ignore the seed bash command line)
docker exec crh-chrome-seed bash -lc 'ps aux | grep -i chrome | grep -v grep; ls -la /app/chromeprofile/Singleton*'

rm -f ~/littlehotelier/chromeprofile/crh/SingletonLock \
      ~/littlehotelier/chromeprofile/crh/SingletonSocket \
      ~/littlehotelier/chromeprofile/crh/SingletonCookie
```

Then launch Chrome:

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

- Success: groups home loads; session stays warm (fewer paid captcha solves).
- Cold login: may auto-recover via 2captcha Amazon WAF + `hbo_bdc_2facode` (allow ~3 minutes).
- Persistent failure: check 2captcha balance / `hbo_2captcha_api_key` / SMS 2FA pipeline; re-seed only if the Chrome profile is corrupted or first-time setup.

Existing `BDCVerifyLoginJob` still warms **HtmlUnit** `bdc.cookies` only — it does **not** refresh the Chrome profile.

## Code pointers

| Piece | Location |
|-------|----------|
| Selenium login + groups home | `BookingComSeleniumScraper.doLogin` |
| AWS WAF detect/solve (login + CC view) | `BookingComSeleniumScraper.isAwsWafChallenge` / `solveAwsWafChallenge` |
| 2captcha Amazon WAF API | `CaptchaSolverService.solveAmazonWaf` |
| VCC list via fresa JSON | `BookingComSeleniumScraper.getAllVCCBookingsThatCanBeCharged` |
| Property id | WP/DB option `hbo_bdc_hotel_id` |
| Chrome options / profile path | `application.properties` → `chromescraper.driver.options` |
| Volume mounts | `docker-compose.yml` |
| Keep-warm job | `BDCSeleniumVerifyLoginJob` |

## Re-seed triggers

Re-run the seed procedure when:

- First-time profile setup on a host / property
- Profile dir was deleted or a Mac profile was mistakenly copied in
- Chrome major upgrade repeatedly breaks the profile (rare; try re-seed on Linux first)
- 2captcha / 2FA automation is down and you need a temporary warm session

## Related decisions (2026)

- PerimeterX is gone from this login HAR; AWS WAF is the bot gate.
- Warm profile remains preferred; **2captcha Amazon WAF** covers cold login and card-view challenges.
- Prefer `hbo_2captcha_proxy` matching Chrome egress (`AmazonTask`) over proxyless.
- Production VCC discovery may still use Cloudbeds until Selenium path is wired into `CreatePrepaidChargeJob`; the scrape method itself is Selenium + fresa.
