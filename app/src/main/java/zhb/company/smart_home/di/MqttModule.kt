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
import zhb.company.smart_home.data.notifications.PushNotifications
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
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
            nodeIds = get<AppPreferences>().node5Id,
            scope = get(named(APP_SCOPE)),
        )
    }
    single { GoodNightUseCase(get()) }
    single { PushNotifications(context = androidContext(), preferences = get(), scope = get(named(APP_SCOPE))) }
}
