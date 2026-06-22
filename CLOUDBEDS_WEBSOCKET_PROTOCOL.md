# Cloudbeds calendar WebSocket protocol (reverse‑engineered)

This document describes the WebSocket protocol used by the Cloudbeds calendar/allocation UI at https://hotels.cloudbeds.com/. It was inferred from captured messages; use it to build a test client or to consume calendar/room-assignment data without scraping hundreds of `get_reservation` calls.

> **2026-06 update:** The `on_migrate` blob was previously believed to be "encrypted / custom binary that we don't need to decrypt." That was wrong. It is simply **raw DEFLATE (headerless zlib)** that decompresses to **JSON in a column-oriented `{keys, rows}` form**, and it is the single best source for a **full reservation snapshot** of the calendar (one frame instead of hundreds of `get_reservation` calls). Decoding details are in the [on_migrate section](#3--on_migrate-server--client-full-calendar-snapshot) below.

## Message flow (high level)

- **<<<** = server → client  
- **>>>** = client → server  

1. **<<< auth** – Server sends auth result (success, `property_id`).
2. **>>> migrate** – Client sends session/version and CSRF-style token.
3. **<<< on_migrate** – Server sends a large base64 blob in `data` (~647 KB in our capture, ~3.3 MB once decoded). It is **raw DEFLATE → JSON** in a columnar `{keys, rows}` layout and contains the **entire current calendar** (all reservations, blocked dates, out-of-service, etc.). **This is the bulk snapshot you want.**
4. **>>> get_changes** – Client sends `{"action":"get_changes","last":<unix_ts>}` to request deltas since a timestamp.
5. **<<< guarantee + payload** – Server echoes a `guarantee` token and sends a **payload** (JSON string) containing incremental calendar/room events (`action: "changes"`, `"room_assign"`, `"event_change"`, `"room_free"`, `"auto_assign"`, etc.). Client ACKs each with a **>>> guarantee** echo. This is the live-update channel (new bookings, room moves, check-ins appear without a page refresh).

So there are **two useful data sources**: the **`on_migrate` bulk snapshot** (full state, columnar) and the **guarantee/changes payloads** (incremental updates, array of objects).

---

## Message formats

### 1. <<< Auth (server → client)

```json
{
  "action": "auth",
  "success": true,
  "version": "0",
  "property_id": "17363"
}
```

Authentication is taken from the **cookies on the WS handshake** (no auth message is sent by the
client). The session is keyed on `acessa_session`, but it is only accepted while the short-lived
**`at` access-token cookie (a JWT, ~8h lifetime)** is current. On failure the server replies with
`success: false` and names the session cookie (`"name":"acessa_session"`), then closes with code 1000.

The browser avoids this by minting a fresh access token **immediately before** opening the WS:

```
POST https://hotels.cloudbeds.com/auth/session_access_token/refresh
Content-Type: application/x-www-form-urlencoded
x-property-id: <propertyId>

code=<rt cookie value>&csrf_accessa=<csrf_accessa_cookie value>
```

The response returns `{"refreshToken": ..., "accessToken": ...}` and Set-Cookie rotates both the
`at` and `rt` cookies. Note the **refresh token is one-time use** — the rotated `rt` must be kept
for the next refresh. Any WS client must replicate this refresh-then-connect sequence.

### 2. >>> Migrate (client → server)

```json
{
  "action": "migrate",
  "id": "177213896158801860",
  "__created": 1772139141588,
  "property_id": "17363",
  "version": "https://front.cloudbeds.com/mfd-root/app.js",
  "frontVersion": "18.188.1",
  "csrf_accessa": "7abc557b9b6f540f50c87e9b43f8ab70"
}
```

- `id` / `__created`: session or tab identifier (from frontend).
- `version` / `frontVersion`: app/JS version (from page).
- `csrf_accessa`: CSRF-like token (from page/cookies).

### 3. <<< on_migrate (server → client, full calendar snapshot)

```json
{
  "success": true,
  "action": "on_migrate",
  "time": 1781108500,
  "data": "<base64 string; ~647 KB; raw DEFLATE → ~3.3 MB JSON>"
}
```

**This is the full calendar snapshot, not an opaque blob.** To decode:

1. base64-decode `data`.
2. **raw-DEFLATE** decompress (headerless zlib — use a *negative* window size). It is **not** gzip and **not** zlib-wrapped, so plain `zlib.decompress(raw)` and `gzip` both fail with a header error; you must pass `-zlib.MAX_WBITS`.
3. Parse the result as JSON.

```python
import json, base64, zlib

raw = base64.b64decode(on_migrate["data"])
obj = json.loads(zlib.decompress(raw, -zlib.MAX_WBITS))   # negative wbits = raw DEFLATE
```

Java (Inflater with `nowrap=true`):

```java
byte[] raw = Base64.getDecoder().decode(dataField);
Inflater inflater = new Inflater(true); // true = nowrap = raw DEFLATE
inflater.setInput(raw);
ByteArrayOutputStream out = new ByteArrayOutputStream();
byte[] buf = new byte[65536];
while (!inflater.finished()) {
    int n = inflater.inflate(buf);
    if (n == 0 && inflater.needsInput()) break;
    out.write(buf, 0, n);
}
inflater.end();
String json = out.toString(StandardCharsets.UTF_8);
```

#### Decoded structure (column-oriented)

To save bandwidth the snapshot is **columnar**, not an array of objects:

```json
{
  "Events": {
    "keys": ["id", "property_id", "booking_id", "type", "start_date", "end_date",
             "room_id", "booking_date", "assignment_date", "identifier", "...",
             "first_name", "last_name", "status", "total", "balance_due", "..."],
    "rows": [ ["177...","17363","176834876","checked_in","2026-06-09", "..."],
              ... ]
  },
  "NonAssignedReservations": { "keys": [...], "rows": [...] }
}
```

- `Events.keys` is the list of **49 column names** (see [field table](#event-object-fields)).
- `Events.rows` is a list of value-arrays, one per event; each array lines up positionally with `keys`.
- Rebuild objects with a simple zip:

```python
keys = obj["Events"]["keys"]
events = [dict(zip(keys, row)) for row in obj["Events"]["rows"]]
```

- `NonAssignedReservations` uses the same `{keys, rows}` shape (reservations not yet assigned to a specific room/bed).

In our capture this expanded to **4,923 events**, broken down by `type`:

| `type` | count | `status` |
|--------|-------|----------|
| `booked` | 2,359 | `confirmed` |
| `checked_out` | 2,138 | `checked_out` |
| `out_of_service` | 225 | (none) |
| `checked_in` | 161 | `checked_in` |
| `blocked_dates` | 39 | (none) |
| `courtesy_hold` | 1 | (none) |

So the calendar grid is rendered directly from this one frame — guest name, dates, room, status, totals and balance are all present per event.

### 4. >>> Guarantee (client → server, heartbeat / delta ACK)

```json
{
  "action": "guarantee",
  "guarantee": "69a0b2dbbba5a4.15172558",
  "property_id": "17363",
  "version": "https://front.cloudbeds.com/mfd-root/app.js",
  "frontVersion": "18.188.1",
  "csrf_accessa": "7abc557b9b6f540f50c87e9b43f8ab70"
}
```

- `guarantee`: token (client can generate, e.g. random; server echoes it back in the response).

### 5. <<< Guarantee response (server → client)

Outer envelope:

```json
{
  "guarantee": "69a0b2dbbba5a4.15172558",
  "payload": "<JSON string, see below>"
}
```

**payload** (after parsing the inner JSON) can look like:

#### room_assign

```json
{
  "action": "room_assign",
  "data": {
    "Events": [
      {
        "id": "177213922705803971",
        "created": "2026-02-26 20:53:47",
        "last_change": "1772139227",
        "reason": "FREE... Jonathan Bell maybe?",
        "start_date": "2026-02-25",
        "end_date": "2026-02-27",
        "res_rt_id": "112621",
        "type": "blocked_dates",
        "booking_id": "0",
        "room_id": "112621-0",
        "start_js": "2026-02-25T00:00:00.000Z",
        "end_js": "2026-02-27T00:00:00.000Z",
        "nights": 2,
        "room_type_id": "112621",
        "room_group": "1",
        "room_identifier": "",
        "room_rate_total_adjustment": "0.00",
        "balance_due": "0.00",
        "is_group_booking": false
      }
    ]
  },
  "delete": { "NonAssignedReservations": [] },
  "house_keeping_conditions": [],
  "time": 1772139228
}
```

#### changes

`action: "changes"` (or `"room_assign"`) with `data.Events`. **Unlike `on_migrate`, the delta payloads use a plain array of objects** (already key/value, no `{keys, rows}` columnar form):

```json
{
  "action": "changes",
  "data": {
    "NonAssignedReservations": false,
    "Events": [
      { "id": "17708284850805693", "property_id": "17363", "booking_id": "166419128",
        "type": "booked", "start_date": "2026-07-10", "end_date": "2026-07-13",
        "room_id": "112188-39", "status": "confirmed", "first_name": "...", "...": "..." }
    ]
  }
}
```

To request deltas, send `{"action":"get_changes","last":<unix_ts>,"property_id":"17363",...}` where `last` is the `time` from the previous `on_migrate`/changes frame.

#### room_free

Removes (or adds) calendar rows by **event id** — used when a cell is freed or re-added without a full reservation object:

```json
{
  "action": "room_free",
  "data": ["177213922705803971"],
  "add": false,
  "time": 1772139241
}
```

- `data`: array of calendar **event ids** (not `booking_id`)
- `add`: `false` = remove those ids from the grid; `true` = add/free (semantics observed in captures)

#### event_change

In-place edits to **existing assigned** calendar rows — most often a bed/room move on the same reservation (same `booking_id`, new `room_id`). Observed in production when staff drag reservations on the grid; does not usually change `balance_due`.

```json
{
  "action": "event_change",
  "data": {
    "Events": [
      {
        "id": "176766409…",
        "booking_id": "176766409",
        "type": "booked",
        "status": "confirmed",
        "room_id": "111746-13",
        "…": "…"
      }
    ]
  },
  "time": 1782050853
}
```

#### auto_assign

Bulk auto-assignment cleanup. Often carries `extras=update` and deletes one or more `NonAssignedReservations` ids **without** new `Events` rows in the same frame (the assigned tiles may arrive in a subsequent `changes` / `room_assign` payload).

```json
{
  "action": "auto_assign",
  "extras": "update",
  "delete": { "NonAssignedReservations": ["230784297", "230784298"] },
  "data": { "Events": [] },
  "time": 1782021961
}
```

#### Payload fields used by this app

| Payload `action` | Meaning | Key fields |
|------------------|---------|------------|
| `changes` | General calendar/reservation delta | `data.Events`, optional `data.NonAssignedReservations`, `delete`, `rates`, … |
| `room_assign` | Assignment / grid placement | `data.Events`, often `delete.NonAssignedReservations` |
| `event_change` | In-place edit / bed move on assigned row | `data.Events` |
| `auto_assign` | Bulk auto-assign cleanup | `delete.NonAssignedReservations`, optional `extras` |
| `room_free` | Remove (or add) grid cells by id | `data` (id array), `add` |

**Removing calendar rows (cancellations / bed management):**

Cloudbeds does **not** reliably emit `status=canceled` on the calendar WebSocket. Rows disappear via:

| Mechanism | Typical signal | Notes |
|-----------|----------------|-------|
| `delete.Events` on `changes` | `delete.Events: [<event_id>]` and `removed_event_ids` | Old tile removed; may be a cancel **or** a bed move / stay split (check replacement `Events` in the same payload) |
| `room_free` with `add: false` | `removed_event_ids: [<event_id>]` | Clears a cell; often followed by `blocked_dates` / `out_of_service` rather than a guest cancel |
| `delete.NonAssignedReservations` | id list under `delete` | Almost always **assignment** (unassigned queue → grid), not a cancel |

Calendar **event ids** are opaque numeric strings — they are **not** the same as `booking_id`. Three id namespaces appear on the wire:

| Namespace | Where | Meaning |
|-----------|-------|---------|
| `Events[].id` | Assigned calendar grid rows | Calendar **event id** (used in `delete.Events` and `room_free`) |
| `NonAssignedReservations[].id` | Unassigned queue rows | **`booking_rooms.id`** (REST `get_reservation` → `booking_rooms[].id`), not a calendar event id |
| `Events[].booking_id` | Both assigned and unassigned reservation rows | Cloudbeds **reservation id** |

**Event id structure (two observed formats):**

| Format | Shape | Example |
|--------|-------|---------|
| Reservation-prefix | `{booking_id 9d}{suffix}` | `178177230140204791` → booking `178177230` |
| Timestamp-prefix | `{last_change unix 10d}{suffix 7–8d}` | `17820844265948041` = `1782084426` + `5948041` (tile created at assignment time) |

Do **not** rely on parsing the prefix of a deleted event id to recover `booking_id`. Timestamp-prefix ids can yield a plausible-looking but wrong 9-digit value (e.g. `17820844265948041` → `178208442`, which is **not** a live reservation — the actual booking was `178599456`). Prefix parsing is a last-resort fallback only.

**Resolving deletes to a booking id:**

`delete.Events` and `room_free` (`add: false`) carry only calendar event ids — never `booking_id`. To identify which reservation was cancelled (or moved):

1. Maintain an **event id → booking id cache** from the `on_migrate` snapshot and every incremental payload that includes `data.Events` (`changes`, `room_assign`, `event_change`, …). Index each row's `id` and `booking_id` as events arrive.
2. On `delete.Events` / `room_free`, look up the removed id in that cache **before** dropping it.
3. Also index `NonAssignedReservations` rows (`id` = `booking_rooms.id` → `booking_id`) so assignment flows are tracked; ids in `delete.NonAssignedReservations` are removed from that index when a booking is placed on the grid.
4. If the cache misses (e.g. WS reconnect after assignment), fall back to REST (`get_reservation`, cancellation report) — the delete payload alone is not decodable.

This app implements the cache in `CloudbedsCalendarEventRegistry` (`CloudbedsWebSocketService` calls `beginUpdate` / `commitUpdate` around listener fan-out). `CloudbedsEventIdParser` remains a best-effort prefix fallback.

**Cancel vs bed-move heuristic:** when `delete.Events` fires with `replacement_types={}` and `events=0` in the same payload, it is very likely a cancellation rather than a bed move (moves usually emit replacement `Events` with the same `booking_id` in the same or next frame).

Logged to `cloudbeds-events.log` as `UPDATE` (payload `action`, deletes, `EVENT` / `NON_ASSIGNED` rows — each includes `event_id=` — and `CANCEL_CANDIDATE` on calendar event removal) or `SNAPSHOT`. `CANCEL_CANDIDATE` lines include `booking_id`, `booking_id_source` (`CACHE`, `PARSED_PREFIX`, or `UNKNOWN`), and `replacement_types`.

**Event row `type`** (inside `Events[]`): `booked`, `checked_in`, `checked_out`, `blocked_dates`, `out_of_service`, `courtesy_hold`.

**Event row `status`** (reservations): `confirmed`, `checked_in`, `checked_out`, `canceled` (when present).

Unassigned new bookings appear in `NonAssignedReservations` (snapshot columnar; change payloads may include an array) until assigned to a `room_id`.

---

## Event object fields

The `on_migrate` snapshot ships these **49 columns** (in `Events.keys`); the `changes`/`room_assign` payloads include the same fields as object properties (plus occasional derived fields like `nights`, `start_js`, `end_js` on some event types).

| Field | Example | Notes |
|-------|---------|--------|
| `id` | `"17708284850805693"` | Row id — calendar **event id** on assigned `Events` rows; **`booking_rooms.id`** on `NonAssignedReservations` rows |
| `property_id` | `"17363"` | Property |
| `booking_id` | `"176834876"` / `"0"` | Reservation id; `0` for blocked_dates |
| `type` | `"checked_in"` | `booked`, `checked_in`, `checked_out`, `out_of_service`, `blocked_dates`, `courtesy_hold` |
| `start_date` / `end_date` | `"2026-06-09"` | Stay range |
| `room_id` | `"112564-8"` | `roomTypeId-bedId` (bed/unit `0` when whole-room) |
| `booking_date` | `"2026-02-11 16:48:02"` | When booked |
| `assignment_date` | `"2026-02-11 16:48:05"` | When assigned to room |
| `identifier` | `"7215683983530"` | Reservation/booking identifier string |
| `third_party` | `"0"` / `"1"` | OTA / third-party flag |
| `status` | `"checked_in"` | `confirmed`, `checked_in`, `checked_out`, or null |
| `assignment_type` | | Room-assignment type |
| `res_grand_total`, `total`, `room_price`, `balance_due` | `46.2200`, `0` | Financials |
| `customer_id` | | Guest/customer id |
| `first_name`, `last_name`, `email`, `phone` | `"YIXUN"`, `"WANG"` | Guest info |
| `res_rt_id` | `"112564"` | Room type id (matches `rate_ids` `0-112564` in `availability/get`) |
| `booking_rooms_id` | `"230860568"` | Booking-room link id; matches REST `get_reservation` → `booking_rooms[].id` |
| `detailed_rates` | | Per-night rate breakdown |
| `room_rate_total_adjustment` | `"0.00"` | Manual adjustment |
| `adults`, `kids` | `1`, `0` | Occupancy |
| `room_identifier` | `"1600658897034"` | Internal room/unit identifier |
| `room_group` | `"1"` | Room group |
| `package` | | Package id/info |
| `reason` | `"FREE... Jonathan Bell maybe?"` | Free-text reason (blocked_dates / OOS) |
| `hrs` | | Hours (hourly bookings) |
| `created`, `last_change`, `created_at`, `updated_at` | | Timestamps |
| `notes` | | Reservation notes |
| `grouped_rooms` | | Grouped-room info |
| `booking_estimated_arrival_time` | | ETA |
| `booking_source`, `booking_is_root_source`, `is_source` | `"458851"` | Source/channel id |
| `is_hotel_collect_payment` | | Hotel-collect vs channel-collect |
| `third_party_identifier` | | OTA reference |
| `group_profile_id`, `group_profile_name` | | Group booking profile |
| `allotment_block_id` | | Allotment/block id |
| `is_room_locked` | | Room lock flag |

> `room_id` (`112564-8`) and `res_rt_id` (`112564`) tie each event back to the room type / rate ids used by the REST calendar calls (`/connect/availability/get` with `rate_ids[]=0-112564`).

---

## How to get WebSocket URL and migrate parameters

1. Open Chrome DevTools → **Network** → filter **WS**.
2. Log in to https://hotels.cloudbeds.com/ and open the calendar/allocation view.
3. Select the WebSocket connection and copy its **Request URL** (e.g. `wss://...`).
4. From the same session you can copy the first **>>> migrate** and **>>> guarantee** messages (e.g. from your `ws_messages.txt`) to get `property_id`, `version`, `frontVersion`, `csrf_accessa`, and the shape of `id`/`__created` and `guarantee`.

The test client in this repo expects a WebSocket URL and, optionally, a JSON file or env vars for the migrate payload so it can connect and print incoming `room_assign` / `changes` events.

### Running the test client

1. **Get the WebSocket URL**  
   Chrome DevTools → Network → WS → select the Cloudbeds connection → copy the Request URL (e.g. `wss://...`).

2. **Create a migrate JSON file** (recommended)  
   From `ws_messages.txt`, copy the line that is the **>>> migrate** message (the whole line including the backticks is fine). Save it as e.g. `migrate.json`.  
   Example content (replace with your session values):
   ```json
   {"action":"migrate","id":"177213896158801860","__created":1772139141588,"property_id":"17363","version":"https://front.cloudbeds.com/mfd-root/app.js","frontVersion":"18.188.1","csrf_accessa":"7abc557b9b6f540f50c87e9b43f8ab70"}
   ```

3. **Run** (from project root):
   ```bash
   ./gradlew run --args="\"wss://YOUR_WS_URL\" migrate.json"
   ```
   Or with main class set to the test client:
   ```bash
   java -cp build/classes/java/main:$(./gradlew -q printClasspath 2>/dev/null || true) com.macbackpackers.scrapers.CloudbedsWebSocketTestClient "wss://YOUR_WS_URL" migrate.json
   ```
   Or use your IDE: run `CloudbedsWebSocketTestClient` with program arguments: `wss://...` and optionally `migrate.json`.

   If you omit the migrate file, set system properties: `cloudbeds.ws.propertyId`, `cloudbeds.ws.csrf`, and optionally `cloudbeds.ws.version`, `cloudbeds.ws.frontVersion`.

---

## Summary for a test client

1. Connect to the Cloudbeds WebSocket URL (from DevTools).
2. Wait for **<<< auth**; optionally check `success` and `property_id`.
3. Send **>>> migrate** with session/version/csrf from the same browser session.
4. **Decode <<< on_migrate** for the full snapshot: base64-decode `data` → **raw-DEFLATE** decompress (`-zlib.MAX_WBITS` / `Inflater(nowrap=true)`) → JSON → rebuild objects with `dict(zip(Events.keys, row))`. This one frame contains the entire calendar (all reservations + blocked/OOS dates).
5. Send **>>> get_changes** with `last` = the `time` from `on_migrate`, then **>>> guarantee** periodically (e.g. every 15–30 s) with a stable or random token.
6. On each **<<<** message that has `guarantee` and `payload`, parse `payload` as JSON; handle `action` values `changes`, `room_assign`, `event_change`, `room_free`, and `auto_assign` — iterate `data.Events` (a plain array of objects here) and apply them as incremental updates.

This avoids calling `get_reservation` hundreds of times: take the bulk state from `on_migrate`, then keep it current with the incremental `changes` payloads.

---

## Complementary REST calls (calendar page)

The WebSocket carries the bookings; the calendar page also fires REST calls for the availability bar and occupancy strip. These are **counts/percentages only — no guest data**:

| Call | Returns |
|------|---------|
| `POST hotels.cloudbeds.com/connect/availability/get` | Free-room **counts** per rate id (`rate_ids[]=0-112564`, …) over a date window; response keyed by rate id (`"0-112564"`). |
| `GET api.cloudbeds.com/occupancy/v1/query?...&groupBy=stay_date` | Occupancy **percentage** per stay date. |
| `GET hotels.cloudbeds.com/calculation/occupancy/getOccupancyForDateRange` | Occupancy for a date range (dashboard/overview). |

Property/room-type metadata (names, rate ids, currency, permissions) comes from the bootstrap calls `GET /connect_data/17363`, `GET /connect/17363`, and `POST /hotel/get_content`. The `res_rt_id` in each event maps to the room-type/rate ids these return.
