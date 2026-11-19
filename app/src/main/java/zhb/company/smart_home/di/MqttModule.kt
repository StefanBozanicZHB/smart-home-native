package zhb.company.smart_home.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.mqtt.HiveMqConnector
import zhb.company.smart_home.data.mqtt.MqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.domain.GoodNightUseCase

val mqttModule = module {
    single<MqttConnector> { HiveMqConnector() }
    single { SmartHomeRepository(configSource = get<FirebaseRtdbApi>(), connector = get()) }
    // Created at start so it is listening before the first connect delivers retained messages.
    single(createdAtStart = true) {
        Node5Repository(
            mqtt = get(),
            nodeIds = get<AppPreferences>().node5Id,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
    single { GoodNightUseCase(get()) }
}
