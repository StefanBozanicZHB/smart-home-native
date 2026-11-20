# Feature gap: Flutter `smart-home-app` → native `smart-home-native`

Compared on 2026-11-19 against `smart-home-app/lib` (Flutter, reference only, to be retired) and
native `main` at `18ce72e`. Firmware state from `smart-home-mcu/docs` (`nodes.md`, `mqtt_topics.md`).

Method: every MQTT topic literal and `MqttTopics` constant used by the Flutter app was diffed against
`data/mqtt/MqttTopics.kt`, then each Flutter screen, dialog and provider was checked for user-visible
actions and compared with the native screen contracts (`ui/*/*Contract.kt`).

Legend: ✅ same · ⚠️ different / partial · ❌ missing in native · 🗑️ obsolete (firmware no longer supports it)

## 1. Bugs and mismatches found (fix first)

| # | Area | Flutter | Native | Impact | Proposed fix |
|---|---|---|---|---|---|
| B1 | A/C state topics (node8) | `node8/ac/temp`, `node8/ac/fan`, `node8/ac/mode` (retained, written by the app) | `node8/acTemp`, `node8/acFan`, `node8/acMode` | The two apps do not see each other's A/C settings; native ignores the retained values already on the broker. No node reads these topics, so only the apps are affected. | Fixed in native (now `node8/ac/{temp,fan,mode}`). Still to do: clear the retained `node8/acTemp|acFan|acMode` on the broker (empty retained message). |
| B2 | Ground-floor lights (node4) | `indoor_down_screen`: toggles `node4/dnevniBoravak/sijalica1…3`; also in GoodNight | No screen, but `PowerDevices` still lists the three lights, so GoodNight publishes to them and they count as "devices on" from retained `…/stanje` | Since `node4-2.0.0` Node 4 only measures: the light relays were removed and it subscribes to nothing. Stale retained `sijalicaN/stanje` can show lights as "on" forever. | Fixed in native (lights removed). Still to do: clear the retained `node4/dnevniBoravak/sijalicaN/stanje` on the broker. |
| B3 | A/C temperature codes | Gray-code table 17→`0` … 30→`B` | — | Fixed in `e79c36e` (native sent wrong codes for 19–30 °C). | Done. |

## 2. Feature comparison by screen

### Login and connection
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Email/password sign-in (Firebase Identity Toolkit REST) | ✓ | ✓ | ✅ |
| Remember credentials | SharedPreferences (plain text) | DataStore (plain text) | ⚠️ parity, but see §4 S1 |
| Biometric unlock | `local_auth` | `BiometricPrompt` | ✅ |
| Broker config from Firebase RTDB, primary + backup broker | ✓ | ✓ | ✅ |
| Connection error screen with retry | ✓ | ✓ | ✅ |
| Request legacy node state (`node1/stanje` = `s`) after connect | ✓ | ✓ | ✅ (still needed until nodes 0–3, 6, 8 are migrated) |

### Home
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Temperatures (yard, ground floor, upper floor), connected/disconnected status | ✓ | ✓ | ✅ |
| Room cards with online dot | ✓ | ✓ | ✅ |
| Front door unlock / gate (`node3/vrata`) | ✓ | ✓ | ✅ |
| Shortcuts: LED strip, sprinklers, fountain, pool pump, pool heater | ✓ | ✓ | ✅ |
| "Devices on" count + list dialog | ✓ | ✓ | ✅ |
| GoodNight | turns off everything incl. `node3/vrata` and heating | doors/gate excluded on purpose; heating excluded since `18ce72e` | ⚠️ intentional difference |
| Heating card | tube temp, relay, main switch (old API) | flow temp, pump, main switch (Node 5 v2 API) | ✅ (new API) |

### Garden / yard (node0, node1, node2, node6)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Yard temperature and humidity | ✓ | ✓ | ✅ |
| LED strip, lamp (`neonka`, `sijalica`) | ✓ | ✓ | ✅ |
| Fountain pump and light | ✓ | ✓ | ✅ |
| Sprinklers 1–6 manual, all on/off, "currently running" | ✓ | ✓ | ✅ |
| Sprinkler auto timer config (start, on, off, sets, auto) | settings dialog | settings dialog | ✅ |
| Pool pump, pool heating, pool temperature | ✓ | ✓ | ✅ |
| Sprinkler location map ("Pregled prskalica") | ✓ | ✓ (location dialog) | ✅ |

### House (upper floor, node8)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Temperature (calibrated −6 °C), humidity, gas | ✓ | ✓ | ✅ |
| A/C power, mode, fan, temperature (IR via `node8/aq`) | ✓ | ✓ | ✅ (state topics aligned, B1) |
| Play melody (`node8/melody` = `1`) | music-note button | topic constant only, no UI | ❌ |
| Ground-floor lights 1–3 (node4) | `indoor_down_screen` | — | 🗑️ see B2 |

### Heating (node5)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Old API: heating type, pellet level, alarm level, full/empty calibration, `glavniPrekidac`, `planogram`, one-digit hysteresis | ✓ | removed | 🗑️ replaced by `node5-2.0.0` |
| New API: main switch, per-zone workday/weekend schedule (0.1 °C, ≤ 8 segments), hysteresis, safe mode, pump events, command results by `id` | — | ✓ | native only |

Note: the house still runs the **old** Node 5 firmware. Until `node5-2.0.0` is installed, only the
Flutter app can control the real heating.

### Shed (node7)
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Door, lights 1–3, fingerprint add/delete/list | ✓ | ✓ | ✅, but 🗑️ candidate: node7 is **not in use** (`nodes.md`) |

### Settings and connections
| Feature | Flutter | Native | Status |
|---|---|---|---|
| Light/dark theme | ✓ | ✓ | ✅ |
| Microcontroller overview (node0–8 online) | ✓ | ✓ | ✅ |
| Warning instead of a switch when the device's node is offline ("Mikrokontroler … nije povezan") | ✓ (switch replaced by a warning icon, toast) | online dot only; switches stay active and publish into the void | ❌ |

## 3. Topic diff summary

Only in Flutter: all old Node 5 topics (intentionally removed), `node0/sprinkler/auto/enable` (constant
defined but never used in Flutter either), `node8/ac/{temp,fan,mode}` (B1).
Only in native: `node8/acTemp|acFan|acMode` (B1), Node 5 v2 topics under `node5/`.
Everything else (nodes 0–4, 6–8) matches.

## 4. Improvements that are not Flutter parity

| # | Item | Why |
|---|---|---|
| S1 | Store the Firebase password encrypted (Android Keystore) or keep only the refresh token | Plain text in DataStore today, same as Flutter. |
| S2 | ~~Online state from `<node>/status` (retained Last Will) for migrated nodes~~ | Done: `NodeStatusTracker` (status + 3 min heartbeat timeout, unknown while the app is disconnected); legacy nodes keep the old rule until migrated. |
| S3 | Hide or mark the shed screen (node7) and drop node9 references | Nodes are not in use. |
| S4 | Node 0's daily heating report (Telegram) reads `node5/grejanjeTemp/stanje`, which `node5-2.0.0` no longer publishes | Firmware side (`smart-home-mcu`), not the app; listed so it is not forgotten when Node 5 v2 is installed. |
| S5 | Replace deprecated Compose APIs (`Slider` in settings, `PasswordVisualTransformation` in auth) | Compiler warnings after the Compose 1.13 upgrade. |

## 5. Before the Flutter app can be retired

1. ~~Fix B1 and B2~~ (done in native; broker cleanup in step 6).
2. Add the melody button (node8).
3. Disable or warn on switches whose node is offline.
4. Install `node5-2.0.0` on the real Node 5 (only then can native control the heating).
5. Decide on the shed screen (S3).
6. Clear the obsolete retained topics on the broker (old Node 5 topics, `node4/…/sijalicaN/stanje`, `node8/acTemp|acFan|acMode`).
