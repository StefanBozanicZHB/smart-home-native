# Wall display mode (tablet in the upper living room)

Status: request, not implemented. Written 2026-11-26.
Approved look (prototype with sample data): https://claude.ai/artifact/S4vCuGmmecixrcBP3CJrSg

## Goal

An old tablet hangs on the wall in the upper-floor living room, always on its charger, and replaces the
Lidl weather station there. It shows the time first, then the weather forecast and the state of the house.
Node 8 (room sensor) is mounted next to it, beside or below the tablet, never above it (the tablet warms
the air above it).

The wall display is a **mode of this app**, not a new app: same MQTT, Firebase and repositories.

## Target device

- Huawei MediaPad T3 10 (AGS-L09), 9.6", 1280×800, landscape, 2 GB RAM, weak CPU, no GPU worth using.
- **Android version: most likely 7.0 (API 24).** Reports of an Android 8 update are unconfirmed for this
  model. The user must check Settings → System on the tablet before work starts.
- The app has `minSdk = 26`. If the tablet is on API 24, the first task is to find out what blocks
  `minSdk = 24` (HiveMQ client, Keystore code in `SecretCipher`, notifications, widgets, any `java.time`
  use without desugaring) and either lower it for the whole app or report why it cannot be done.
  Do not fork the app for this.

## Turning the mode on

- Settings → "Zidni ekran" switch, stored per device (`AppPreferences`), off by default. Phones never
  see a difference unless it is turned on.
- When on:
  - the app opens straight into the wall screen; normal navigation is one tap on a small settings icon
    (or a long press) away, and returns to the wall screen after 2 minutes without touches;
  - landscape, full screen (system bars hidden, immersive), `FLAG_KEEP_SCREEN_ON`;
  - starts after the tablet reboots (`BOOT_COMPLETED`) and after a crash;
  - no biometric or sign-in prompt on the wall screen: the stored refresh token is used; if it fails,
    show the error on the wall screen instead of the sign-in form;
  - no push notifications on this device (it is not someone's phone).
- Broker: the tablet is always at home, so it should prefer the LAN broker (backup address from RTDB) and
  not depend on Tailscale.

## Layout (landscape 1280×800)

Left column (about 45 %), right column (about 55 %). Dark theme by default; the app's light theme works too.
All texts in Serbian (Latin), as in the rest of the app.

### Left: clock

- `H:MM` very large (about 160 dp), seconds small beside it.
- Date: weekday, day, month, year: "četvrtak, 26. novembar 2026.".
- Sunrise, sunset, day length. Computed on the device with the same NOAA formula and coordinates as the
  firmware (`smart-home-mcu/lib/zhb_schedule`, default 44.8125, 20.4612), no network.

### Left: outside and forecast

- Measured outdoor temperature from `node2/temperature` (retained). If it is missing (deleted after
  5 min without a valid reading), show "–" and the forecast's current temperature in muted text.
- Today's min/max from the history data if available, otherwise from the forecast.
- Hourly forecast for the next 12 hours: temperature line with labels, rain bars (mm), first point
  labelled "sada".
- Five days: weekday, icon, max/min, rain amount if any.
- Source: a free forecast API without a key. Candidate: Open-Meteo (check its terms for this use).
  Refresh every 30 min, keep the last good answer, show its age when it is older than 2 hours.

### Right: rooms

Three rooms side by side, temperature large, humidity below:
- Boravak, sprat: `node8/temperature`, `node8/humidity` (marked "ovde");
- Boravak, prizemlje: `node4/*`;
- Spavaća, prizemlje: `node11/*` (shown when Node 11 reports).

Readings that stopped updating use the app's existing stale style.

### Right: heating (changes with the season)

- **In season:** header with a pill "radi 18 min" or "miruje"; total run time and number of cycles
  today; flow temperature as small secondary text; a **0–24 h strip** with every heat pump run of today
  marked, the part after "now" faded. Reuse the existing heat pump status and pump-run data
  (`Node5Repository`, `HeatPumpStatus`, history pump runs); when Node 10 is installed, `node10/run` and
  `node10/energy` can add kWh today.
- **Out of season:** the tile shrinks to two lines: last run (date and time) and last season's
  dates and total run time. Out of season = the heat pump has not run for 7 days, or the main switch is
  off (`node5` main switch). No empty charts.

### Right: two small tiles

- **Ulazna vrata:** lock state from `node3/state`; the last access events from `node3/access`
  (time, `name` or "dugme iznutra" / "aplikacija" / "nepoznata kartica"). The retained topic holds only
  the last event, so keep the ones received while the screen runs (up to 3); never show `uid_end`.
- **Dvorište i bazen:** next switch-on of neon and lamp from `node2/state` (`*_today`), fountain from
  `node1/state`, pool water temperature from Node 6.

### Bottom strip

Nodes online (x/y, from `NodeStatusTracker`), broker connection, tablet battery and charging state.

### Not now

Solar: no data yet (the inverter's Modbus TCP must first be enabled by the installer). Leave room for a
solar tile in the right column; it will read a future `bozaSub/kuca/solar/state`.

## Night

- 23:00–06:00 (setting): window brightness lowered (`screenBrightness` about 0.05) and colours dimmed;
  the clock stays readable. A touch brightens it for 30 s.
- Optional later: use the tablet's light sensor instead of fixed hours.

## Tablet state over MQTT

The tablet behaves like a node, so the logger records it and Node 8 can later drive its charger.

| Topic | Retain | When | Payload |
|---|---|---|---|
| `bozaSub/kuca/tablet1/status` | yes | Last Will / on connect | `offline` / `online` |
| `bozaSub/kuca/tablet1/state` | yes | every 60 s and when `charging` or `battery` changes | `{"seq":12,"up":720,"battery":62,"charging":false,"plugged":"usb","battery_temp":31.5,"screen":"night","app":"2.3.0"}` |

- `battery` %, `battery_temp` °C (from `BatteryManager` / `ACTION_BATTERY_CHANGED`), `plugged`:
  `ac`, `usb` or `null`, `screen`: `day` or `night`.
- Only published while the wall mode is on. Never retained commands; the tablet takes no commands for now.
- Later (not in this task): Node 8 switches the tablet's 5 V line, charging below 40 % and stopping above
  80 %, and switches it on when `tablet1/state` is older than 10 min.

## Done when

- The mode runs for 48 hours on the tablet without a restart, a frozen clock or a growing memory use.
- After pulling the Wi-Fi for 10 minutes the screen shows stale values clearly and recovers by itself.
- After a reboot of the tablet the wall screen comes back without a touch.
- `tablet1/state` appears in the logger DB every minute.
- Phones with the mode off behave exactly as before.
