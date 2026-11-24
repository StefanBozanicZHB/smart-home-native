# Feature gap: Flutter `smart-home-app` → native `smart-home-native`

Compared on 2026-11-19 (updated 2026-11-24) against `smart-home-app/lib` (Flutter, reference only, to be retired) and
native `main` at `18ce72e`. Firmware state from `smart-home-mcu/docs` (`nodes.md`, `mqtt_topics.md`).

Method: every MQTT topic literal and `MqttTopics` constant used by the Flutter app was diffed against
`data/mqtt/MqttTopics.kt`, then each Flutter screen, dialog and provider was checked for user-visible
actions and compared with the native screen contracts (`ui/*/*Contract.kt`).

Legend: ✅ same · ⚠️ different / partial · ❌ missing in native · 🗑️ obsolete (firmware no longer supports it)

## 1. Bugs and mismatches found (fix first)

| # | Area | Flutter | Native | Impact | Proposed fix |
|---|---|---|---|---|---|
| B1 | A/C state topics (node8) | `node8/ac/temp`, `node8/ac/fan`, `node8/ac/mode` (retained, written by the app) | — | Obsolete: `node8-2.0.0` has no IR transmitter, the A/C control is removed from the app. | Clear the retained `node8/ac/*` and `node8/acTemp|acFan|acMode` on the broker. |
| B2 | Ground-floor lights (node4) | `indoor_down_screen`: toggles `node4/dnevniBoravak/sijalica1…3`; also in GoodNight | — | Since `node4-2.0.0` Node 4 only measures: the light relays were removed and it subscribes to nothing. Stale retained `sijalicaN/stanje` can show lights as "on" forever. | Fixed in native (lights removed). Still to do: clear the retained `node4/dnevniBoravak/sijalicaN/stanje` on the broker. |
| B3 | A/C temperature codes | Gray-code table 17→`0` … 30→`B` | — | Fixed in `e79c36e` (native sent wrong codes for 19–30 °C). | Done. |

## 2. Feature comparison by screen

### Login and connection
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Email/password sign-in (Firebase Identity Toolkit REST) | ✓ | ✓ | ✅ |
| Remember credentials | SharedPreferences (password, plain text) | refresh token only, encrypted with a Keystore key; left out of backups; sign-out in settings | ✅ (better) |
| Biometric unlock | `local_auth` | `BiometricPrompt` (fingerprint, face or screen-lock PIN); the password is never put into the form | ✅ |
| Broker config from Firebase RTDB, primary + backup broker | ✓ | ✓ | ✅ |
| Connection error screen with retry | ✓ | ✓ | ✅ |
| Request legacy node state (`node1/stanje` = `s`) after connect | ✓ | — | 🗑️ no node in use answers it; every node publishes retained states |

### Home
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Temperatures (yard, ground floor, upper floor), connected/disconnected status | ✓ | ✓ | ✅ |
| Room cards with online dot | ✓ | ✓ | ✅ |
| Front door unlock / gate | `node3/vrata` (old API) | `node3/door/open` with confirmation from the node; lock state, card reader health, last access attempt (Node 3 v2 API) | ✅ (new API) |
| Shortcuts: LED strip, sprinklers, fountain, pool pump, pool heating | ✓ | ✓ (pool heating once Node 6 reports it) | ✅ |
| "Devices on" count + list dialog | ✓ | ✓ | ✅ |
| GoodNight | turns off everything incl. the front door and heating | doors/gate excluded on purpose; heating excluded since `18ce72e` | ⚠️ intentional difference |
| Heating card | tube temp, relay, main switch (old API) | flow temp, pump, main switch (Node 5 v2 API) | ✅ (new API) |

### Garden / yard (node1, node2, node6)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Yard temperature and humidity | ✓ (old topics) | temperature from `node2/temperature` (DS18B20); humidity no longer measured (`node2-2.0.0`) | ✅ (new API) |
| LED strip, lamp (`neonka`, `sijalica`) | ✓ (old topics) | `node2/neon/set`, `node2/lamp/set` with automatic switch-off (Node 2 v2 API) | ✅ (new API) |
| Fountain pump and light | ✓ | ✓, plus automatic switch-off after 30 min – 4 h (Node 1 v2 API) | ✅ (new API) |
| Sprinklers 1–6 manual, all on/off, "currently running" | ✓ (old API) | manual run of one zone or all, stop, zone/phase/time left, queued zones, last run event (Node 1 v2 API) | ✅ (new API) |
| Sprinkler schedule | old timer: start, on, off, sets, auto | "Plan zalivanja" on Node 1: days, up to 4 start times, minutes per zone, soak cycles, rain pause | ✅ (new API) |
| Pool pump, pool heating, pool temperature | ✓ (old topics) | pump via `node6/pump/set`, heating via `node6/heating/set` (independent relay, `node6-2.0.1`; hidden while the node does not report `heating`), both with automatic switch-off; water temperature from `node6/temperature` | ✅ (new API) |
| Sprinkler location map ("Pregled prskalica") | ✓ | ✓ (location dialog) | ✅ |

### House (upper floor, node8)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Temperature, humidity | `node8/temp` (−6 °C in the app), `humidity`, `gas` | `node8/temperature`, `node8/humidity` (calibrated on the node, `node8-2.0.0`); gas sensor removed | ✅ (new API) |
| A/C power, mode, fan, temperature (IR via `node8/aq`) | ✓ | removed | 🗑️ no IR transmitter in `node8-2.0.0` |
| Play melody (`node8/melody` = `1`) | music-note button | removed | 🗑️ no buzzer in `node8-2.0.0` |
| Ground-floor lights 1–3 (node4) | `indoor_down_screen` | — | 🗑️ see B2 |

### Heating (node5)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Old API: heating type, pellet level, alarm level, full/empty calibration, `glavniPrekidac`, `planogram`, one-digit hysteresis | ✓ | removed | 🗑️ replaced by `node5-2.0.0` |
| New API: main switch, per-zone workday/weekend schedule (0.1 °C, ≤ 8 segments), hysteresis, safe mode, pump events, command results by `id` | — | ✓ | native only |

Note: the native app supports only the new firmware of nodes 1–6 and 8 (`-2.0.x`); every node in
use runs it.

### Shed (node7)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Door, lights 1–3, fingerprint add/delete/list | ✓ | removed (2026-11-23) | 🗑️ node7 is **not in use** (`nodes.md`) |

### Settings and connections
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Light/dark theme | ✓ | ✓ | ✅ |
| Microcontroller overview (nodes online) | ✓ | nodes 1–6 and 8, status and heartbeat only | ✅ |
| Warning instead of a switch when the device's node is offline ("Mikrokontroler … nije povezan") | ✓ (switch replaced by a warning icon, toast) | the tile is disabled and says "čvor oflajn"; readings of an offline node are grayed with the time of their last update | ✅ |

## 3. Topic diff summary

Only in Flutter: all old Serbian topics of nodes 1–6 (intentionally removed: the `-2.0.0` firmware of
each node replaced them), `node8/ac/{temp,fan,mode}` (B1).
Only in native: Node 1, 2, 3, 5, 6 and 8 v2 topics under `node1/`, `node2/`, `node3/`, `node5/`, `node6/`, `node8/`.
Node 4 uses `node4/temperature` and `node4/humidity`; node7 topics are no longer read.

## 4. Improvements that are not Flutter parity

| # | Item | Why |
|---|---|---|
| S1 | ~~Store the Firebase password encrypted or keep only the refresh token~~ | Done 2026-11-24: only the refresh token is kept, encrypted with a Keystore key. |
| S2 | ~~Online state from `<node>/status` (retained Last Will) for migrated nodes~~ | Done: `NodeStatusTracker` (status + 3 min heartbeat timeout, unknown while the app is disconnected); the old "any live message" rule was removed with the last old-firmware node. |
| S3 | ~~Hide or mark the shed screen (node7) and drop node9 references~~ | Done: shed screen and node7 topics removed. |
| S5 | ~~Replace deprecated Compose APIs~~ | Done: the build has no compiler warnings. |

## 5. Before the Flutter app can be retired

1. ~~Fix B1 and B2~~ (done in native; broker cleanup in step 6).
2. ~~Add the melody button (node8)~~ (no buzzer in `node8-2.0.0`).
3. ~~Disable or warn on switches whose node is offline~~ (done).
4. ~~Install `node5-2.0.0` on the real Node 5~~ (installed 2026-11-22).
5. ~~Decide on the shed screen (S3)~~ (removed).
6. Clear the obsolete retained topics on the broker (old Serbian topics of nodes 1–6, old `node8/*` (`temp`, `gas`, `ac/*`), `node0/*`, `node4/…/sijalicaN/stanje`, `node8/acTemp|acFan|acMode`, `node7/*`).
