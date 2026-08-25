# Booking.com Chrome profile seeding (warm session)

Runbook for keeping a durable Chrome `user-data-dir` so Selenium can use Booking.com extranet with fewer AWS WAF / SMS 2FA challenges.

Last validated: August 2026 (multi-property processors on a Linux Docker host; 2captcha egress via `proxy/` reverse tunnel).

## Why this exists

Booking.com extranet login is protected by **AWS WAF** (not PerimeterX). A cold automated login often requires:

1. Visual captcha
2. SMS 2FA (and sometimes a second auth-assurance SMS)

Captchas also appear frequently when **viewing credit card details** on secure-admin, even if login itself was warm.

**Production strategy (layered):**

1. **Warm Chrome profile** (cookies + WAF tokens) — preferred; skips most challenges.
2. **Automated cold recovery** — `BookingComSeleniumScraper` detects AWS WAF, solves via 2captcha (`AmazonTask` / `AmazonTaskProxyless`), then completes SMS/phone 2FA with existing `hbo_bdc_2facode`.
3. **Manual seed** (this runbook) — first-time profile creation, OS mismatch, or solver/2FA outage.

Prefer configuring `hbo_2captcha_proxy` so 2captcha workers egress from the **same public IP as Chrome** (`AmazonTask`). If the processor host is behind **CGNAT** (no inbound public port), the proxy cannot bind on the WAN: see [2captcha proxy (`proxy/`)](#2captcha-proxy-proxy). Proxyless (`AmazonTaskProxyless`) works but has higher token-rejection risk on Booking.com.

Login captchas currently use AWS WAF **jsapi** (`awswaf-captcha` + `captcha-sdk.awswaf.com/.../jsapi.js`) **stacked with** a silent `challenge.js` (often first-party `www.booking.com/__challenge_…/challenge.js`). Detection and 2captcha tasks should include both scripts when present.

VCC discovery uses Selenium + the fresa API after login:

`GET /fresa/extranet/payments/vccs_to_charge?hotel_id=…&ses=…`

Property targeting uses DB option `hbo_bdc_hotel_id` (not whatever hotel the session landed on).

## Important constraints

| Constraint | Implication |
|------------|-------------|
| Mac ≠ Linux profiles | Never copy a Mac `chromeprofile` into Ubuntu Docker. Seed **on the target OS**. |
| One profile dir ≠ N concurrent Chromes | Each processor gets its **own copy** of the profile. Sharing one mount across processors will lock/corrupt Chrome. |
| Multi-property BDC user | One login can cover several properties. Seed once, then **copy** the tree to the other property folders. |
| Groups home vs property home | Warm entry URL is `…/groups/home/index.html`. Bare `…/manage/home.html` (missing/stale `ses`) can return **HTTP 400**. `BookingComSeleniumScraper.doLogin` always opens/saves groups home. |
| Headless still uses the profile | Prod Chrome options use `--headless=new` + `user-data-dir=/app/chromeprofile`. Seeding is done headed (noVNC); runtime stays headless. Chrome 151 still advertises `HeadlessChrome` unless `chromescraper.driver.useragent` is set. |

## Layout on the production host

Repo root (wherever `docker-compose.yml` lives):

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
chromescraper.driver.options=user-data-dir=/app/chromeprofile --headless=new ...
chromescraper.driver.useragent=Mozilla/5.0 (X11; Linux x86_64) ... Chrome/151.0.0.0 ...
```

Local Mac overrides (uncommitted `application-*-properties`) may point at a Mac path for desktop testing — **do not** deploy those to Docker.

---

## 2captcha proxy (`proxy/`)

AWS WAF tokens are bound to the solver’s **egress IP**. 2captcha `AmazonTask` must therefore CONNECT out through a proxy whose public IP is the **same as Chrome on the processor host**.

If that host has no inbound public port (**CGNAT**), you cannot bind the proxy on the WAN. Cloudflare HTTP tunnels are not an HTTP CONNECT / SOCKS listener, so they cannot be `hbo_2captcha_proxy`. The stack under `proxy/` is a separate Compose project from the processors.

```text
2captcha worker
  → TCP PUBLIC_HOST:PROXY_PORT     (edge NAT/port-forward to the reverse-tunnel host)
  → sshd reverse bind              (autossh -R 0.0.0.0:PROXY_PORT:bdc-forward-proxy:3128)
  → 3proxy on the processor host   (HTTP CONNECT, user/pass)
  → internet                       (processor host WAN = Chrome’s IP)
```

**Public door (not a proxy):** any always-on host with a public IP (or a forwarded WAN port). SSH should be key-only for a dedicated tunnel user. If SSH is not on port 22 at the WAN, map that in the edge firewall. Forward the proxy port (Compose default **3128**) to the reverse-tunnel host’s LAN address. `sshd` needs **`GatewayPorts clientspecified`** (or `yes`) so `-R 0.0.0.0:3128` is reachable on the LAN, not only `127.0.0.1`.

**Forward proxy (the real hop):** authenticated **3proxy** HTTP CONNECT on the processor host. Debian has no `3proxy` package; `proxy/Dockerfile` builds **3proxy 0.9.5** from source. Config is `proxy/3proxy.cfg.template` (`users …:CL:…`, `auth strong`, `proxy -n -p3128 -a`). Host bind is **`127.0.0.1:3128`** only; 2captcha never talks to that loopback — it uses the public tunnel.

**Tunnel:** `proxy/Dockerfile.tunnel` + `autossh` (`ExitOnForwardFailure=yes`, `ServerAliveInterval=30`). Entrypoint scripts must be **LF** (`.gitattributes`: `*.sh` / `Dockerfile*` `eol=lf`). CRLF caused `exec … no such file`.

### Files

| Path | Role |
|------|------|
| `proxy/docker-compose.yml` | `bdc-forward-proxy` + `tunnel` on `proxy-net` |
| `proxy/Dockerfile` | 3proxy image |
| `proxy/Dockerfile.tunnel` | autossh image |
| `proxy/3proxy.cfg.template` | envsubst `PROXY_USER` / `PROXY_PASS` |
| `proxy/.env.example` | copy to `proxy/.env` (**gitignored**) |
| `proxy/docker-entrypoint.sh` | write cfg, exec 3proxy |
| `proxy/tunnel-entrypoint.sh` | `autossh -R 0.0.0.0:3128:bdc-forward-proxy:3128` |

`.env` (see `proxy/.env.example`; use a long random password, no `$` — envsubst — and avoid `:` in the password):

```bash
PROXY_USER=…
PROXY_PASS=…
TUNNEL_HOST=PUBLIC_HOST
TUNNEL_SSH_PORT=22
TUNNEL_USER=tunnel
TUNNEL_SSH_KEY=/path/to/private_key
```

### Bring-up

Same Compose for a local smoke test or production. **Only one `tunnel` at a time** — otherwise sshd on the public host rejects the second `-R` on that port.

```bash
cd proxy
cp .env.example .env        # edit secrets / TUNNEL_SSH_KEY path
docker compose up -d --build
```

Local proxy only (no reverse tunnel):

```bash
docker compose up -d --build bdc-forward-proxy
curl -x http://USER:PASS@127.0.0.1:3128 https://ifconfig.me
```

Through the public door (must match **Chrome’s** WAN on production):

```bash
curl -x http://USER:PASS@PUBLIC_HOST:3128 https://ifconfig.me
```

Healthy 3proxy logs show authenticated 2captcha `CONNECT` to `awswaf` / `account.booking.com` / `online-metrix.net`. `CONNECT` to geetest/Arkose with error `00001` is expected (ACL deny). Unauthenticated `GET http://api.ipify.org` (`user=-`, error `00004`) is usually a scanner on the public door, not 2captcha.

### WordPress / 2captcha options

Per property DB:

| Option | Value |
|--------|--------|
| `hbo_2captcha_api_key` | 2captcha client key |
| `hbo_2captcha_proxy` | `user:pass@PUBLIC_HOST:3128` |
| `hbo_2captcha_proxytype` | `http` (`AmazonTask` supports `http` / `socks4` / `socks5`) |

`CaptchaSolverService.solveAmazonWaf` uses `AmazonTask` when `hbo_2captcha_proxy` is set, else `AmazonTaskProxyless`. The Chrome session itself is **not** sent through this proxy — only the 2captcha worker.

HTTP proxy Basic auth on the public hop is **cleartext**. Treat user/pass as a secret; do not expose an open proxy.

### Ops pitfalls

| Symptom | Cause / fix |
|---------|-------------|
| `remote port forwarding failed for listen port 3128` | Stale `-R` bind on the public host (old tunnel, or laptop + production both running `tunnel`). Docker/autossh retry will **not** free it. Kill the leftover listener on the public host (`ss -tlnp` on the proxy port), then restart **one** `bdc-proxy-tunnel`. |
| Token OK from 2captcha, WAF still blocks Chrome | Tunnel still running on a **different** machine than Chrome: 2captcha egress is that machine’s WAN. Stop extra `tunnel` instances; run it only on the processor host. |
| Visual puzzle “solved” then login/captcha again | (1) Tunnel on a different machine than Chrome. (2) Local Mac test using Docker’s Linux UA (`chromescraper.driver.useragent`) — factory now skips that override unless `os.name` is Linux. (3) AmazonTask must send **either** `jsapiScript` **or** `challenge.js`, not both — both made workers solve classic `captcha.awswaf.com` while Chrome showed `captcha-sdk`. (4) jsapi solutions with no voucher must set `aws-waf-token` and reload; `awswaf-captcha.onSuccess(uuid)` does not clear Booking’s widget. |
| `exec … no such file` on entrypoint | CRLF in `*.sh`. Checkout with LF / `.gitattributes`. |
| `PROXY_PASS` mangled | `$` in the password is eaten by `envsubst`. |

---

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
cd /path/to/compose   # directory with docker-compose.yml
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
ssh -L 6080:localhost:6080 USER@PROCESSOR_HOST
```

Browser: [http://localhost:6080/vnc.html](http://localhost:6080/vnc.html) → Connect (no password).

### 4. Start Chrome with the seed profile

In a **second** SSH session on the host.

If Chrome refuses to start with *“profile appears to be in use by another Google Chrome process”*, you have stale `Singleton*` lock files from a previous container (e.g. a stopped processor). Confirm no Chrome is running, then remove them:

```bash
# Should show no real chrome process (ignore the seed bash command line)
docker exec crh-chrome-seed bash -lc 'ps aux | grep -i chrome | grep -v grep; ls -la /app/chromeprofile/Singleton*'

rm -f chromeprofile/crh/SingletonLock \
      chromeprofile/crh/SingletonSocket \
      chromeprofile/crh/SingletonCookie
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
ls -la chromeprofile/crh | head
du -sh chromeprofile/crh
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
cd /path/to/compose
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
cd /path/to/compose
du -sh chromeprofile/*

docker compose build crh-processor
docker compose up -d --force-recreate crh-processor

docker ps --filter name=crh-processor
docker exec crh-processor ls -la /app/chromeprofile | head
```

Then the same for `hsh-processor`, `rmb-processor`, `lsh-processor` when ready.

Ensure each DB has `hbo_bdc_hotel_id` set for that property before relying on `getAllVCCBookingsThatCanBeCharged`.

## Keep-warm job

`BDCSeleniumVerifyLoginJob` calls `BookingComSeleniumScraper.doLogin` against the Chrome pool. Schedule it like other verify jobs (DB `JobScheduler` / manual insert).

- Success: groups home loads; session stays warm (fewer paid captcha solves).
- Cold login: may auto-recover via 2captcha Amazon WAF + `hbo_bdc_2facode` (allow ~3 minutes).
- Persistent failure: check 2captcha balance / `hbo_2captcha_api_key` / `hbo_2captcha_proxy` (tunnel + IP match) / SMS 2FA pipeline; re-seed only if the Chrome profile is corrupted or first-time setup.

Existing `BDCVerifyLoginJob` still warms **HtmlUnit** `bdc.cookies` only — it does **not** refresh the Chrome profile.

## Code pointers

| Piece | Location |
|-------|----------|
| Selenium login + groups home | `BookingComSeleniumScraper.doLogin` |
| AWS WAF detect/solve (login + CC view) | `BookingComSeleniumScraper.isAwsWafChallenge` / `solveAwsWafChallenge` |
| 2captcha Amazon WAF API | `CaptchaSolverService.solveAmazonWaf` |
| 2captcha proxy (3proxy + autossh) | `proxy/` (separate Compose from processors) |
| VCC list via fresa JSON | `BookingComSeleniumScraper.getAllVCCBookingsThatCanBeCharged` |
| VCC refunds via fresa JSON | `BookingComSeleniumScraper.getAllVCCBookingsThatMustBeRefunded` (`/fresa/extranet/payments/vccs_to_refund`) |
| Property id | WP/DB option `hbo_bdc_hotel_id` |
| Chrome options / profile path / UA | `application.properties` → `chromescraper.driver.options`, `chromescraper.driver.useragent` |
| Hide `--enable-automation` / UA override | `LittleHotelierWebDriverFactory` |
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
- Prefer `hbo_2captcha_proxy` matching Chrome egress (`AmazonTask`) over proxyless. Under CGNAT that is **3proxy on the processor host** + **autossh `-R`** to a public reverse-tunnel host (`proxy/`), not a Cloudflare HTTP tunnel.
- Production VCC discovery may still use Cloudbeds until Selenium path is wired into `CreatePrepaidChargeJob`; the scrape method itself is Selenium + fresa (`vccs_to_charge` / `vccs_to_refund`).
