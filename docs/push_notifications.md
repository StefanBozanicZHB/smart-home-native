# Push notifications (FCM)

The house system sends push notifications through Firebase Cloud Messaging. The sender is the
`das-notifier` service on the Raspberry Pi (DAS repository, `05_eksperimenti/notifier/`, with
setup steps in its README). It sends to the FCM **topic** `home-alerts`; the app only subscribes,
so there is no device token to register anywhere.

## Firebase configuration

The app does not use the `google-services` Gradle plugin and does not commit
`google-services.json`. The four values the plugin would generate are read from
`local.properties` and added as string resources by `app/build.gradle.kts`, so
`FirebaseInitProvider` initializes Firebase at startup as usual:

```
firebase.android.apiKey=...    # client[].api_key[0].current_key
firebase.android.appId=...     # client[].client_info.mobilesdk_app_id
firebase.senderId=...          # project_info.project_number
firebase.projectId=...         # project_info.project_id
```

Take them from `google-services.json` for package `zhb.company.smart_home` (Firebase console →
Project settings → Your apps → Android). The Android API key is different from
`firebase.apiKey` (the Web API key used for REST sign-in).

## In the app

| Piece | Role |
|---|---|
| `data/notifications/PushNotifications` | creates channels `node_status` (high), `heating_alerts` (high) and `system_status` (default); subscribes to / unsubscribes from the topic following the setting |
| `data/notifications/HomeAlertMessagingService` | shows messages that arrive while the app is in the foreground, with the same channel and tag as the system would |
| Settings dialog → "Obaveštenja iz kućnog sistema" | on/off (default on); asks for `POST_NOTIFICATIONS` on Android 13+, links to system settings if denied |
| `MainActivity` | asks for the permission once, on the first start |

Since notifier 1.4.0 messages are **data-only**: `title`, `body`, `channel_id`, `tag`, `id`
(stable event id), `feedback` (`1` = show the buttons), `sent_ms`, plus `type` (`node_status`,
`heating`, `system`, `report`), `event`, `node`/`room`, `state`, `since_ms`, `at_ms`. They reach
`HomeAlertMessagingService` in the foreground and the background, and the app builds every
notification (`AlertPayload`). The same tag replaces the previous notification, so "back online"
replaces "offline" for that node. Older messages with a `notification` part still work.

## Feedback buttons and receipt log

Problem notifications get "U redu je" / "Pravi problem". `FeedbackReceiver` replaces the
notification with a short silent confirmation and writes `feedback` (`ok`/`problem`) and
`feedback_ms`; the service writes `received_ms` and the event fields on every arrival. Both go to
`notification_log/<id>` with PATCH through `NotificationLog`, a persistent queue (SharedPreferences)
flushed after each write and on app start, so labels survive missing network. The database rules
must allow authenticated writes to `notification_log/$id`. The RPi exports them to CSV
(`notification_log_export.py`) for the thesis (implicit labels, RQ4; RPi → phone latency).

Channel ids and the topic name must match the notifier config (`[fcm] topic`) and
`CHANNEL_NODES` / `CHANNEL_HEATING` / `CHANNEL_SYSTEM` in `das_notifier.py`.

## Daily heating reports

The RPi (`das_report.py`) writes a report per heated day to Realtime Database
(`reports/heating/<yyyy-MM-dd>`, list in `reports/heating_index`) and sends a silent notification on
the `reports` channel with data `type=report`, `kind=heating`, `date`. A tap opens that day:
`PendingNavigation` reads the extras (FCM puts them on the launch intent; the foreground path
copies them in `HomeAlertMessagingService`) and the nav graph opens `heating_report/{date}` once
sign-in is done. The list is behind the report icon in the heating header.

Reading uses the signed-in user's ID token (`AuthRepository.freshIdToken`, re-signs in after the
one-hour expiry), so the database rules must allow `reports` reads for authenticated users.
`HeatingReportJsonTest` decodes a report produced by the Python generator.

