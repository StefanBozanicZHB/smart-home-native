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

Messages carry a `notification` part (title, body, `android.notification.channel_id`, `tag`) and a
`data` part (`type` = `node_status`, `heating` or `system`, `event`, `node`, `state`, `since_ms`, `at_ms`). The tag is
`node-<id>`, so "back online" replaces the "offline" notification of the same node.

Channel ids and the topic name must match the notifier config (`[fcm] topic`) and
`CHANNEL_NODES` / `CHANNEL_HEATING` / `CHANNEL_SYSTEM` in `das_notifier.py`.
