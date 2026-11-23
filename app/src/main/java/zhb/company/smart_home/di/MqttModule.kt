package zhb.company.smart_home.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.mqtt.ConnectionLifecycle
import zhb.company.smart_home.data.mqtt.HiveMqConnector
import zhb.company.smart_home.data.mqtt.MqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.notifications.NotificationInbox
import zhb.company.smart_home.widget.WidgetLiveSync
import zhb.company.smart_home.widget.WidgetStore
import zhb.company.smart_home.data.notifications.NotificationLog
import zhb.company.smart_home.data.notifications.PushNotifications
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.data.door.Node3Repository
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository
import zhb.company.smart_home.domain.GoodNightUseCase

/** Process-wide scope for background work that outlives screens (reconnects, health checks). */
const val APP_SCOPE = "appScope"

val mqttModule = module {
    single(named(APP_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<MqttConnector> { HiveMqConnector() }
    single {
        SmartHomeRepository(
            configSource = get<FirebaseRtdbApi>(),
            connector = get(),
            scope = get(named(APP_SCOPE)),
        )
    }
    single { ConnectionLifecycle(repository = get(), scope = get(named(APP_SCOPE))) }
    // Created at start so it is listening before the first connect delivers retained messages.
    single(createdAtStart = true) {
        Node5Repository(
            mqtt = get(),
            scope = get(named(APP_SCOPE)),
        )
    }
    // Created at start so it is listening before the first connect delivers retained messages.
    single(createdAtStart = true) { Node1Repository(mqtt = get(), scope = get(named(APP_SCOPE))) }
    single(createdAtStart = true) { Node2Repository(mqtt = get(), scope = get(named(APP_SCOPE))) }
    single(createdAtStart = true) { Node3Repository(mqtt = get(), scope = get(named(APP_SCOPE))) }
    single(createdAtStart = true) { Node6Repository(mqtt = get(), scope = get(named(APP_SCOPE))) }
    single { GoodNightUseCase(get(), get(), get()) }
    single { PushNotifications(context = androidContext(), preferences = get(), scope = get(named(APP_SCOPE)), log = get()) }
    single { NotificationLog(context = androidContext(), httpClient = get(), auth = get()) }
    single { NotificationInbox(context = androidContext(), log = get()) }
    single { WidgetStore(androidContext()) }
    single { WidgetLiveSync(repository = get(), node5 = get(), store = get(), scope = get(named(APP_SCOPE))) }
}
