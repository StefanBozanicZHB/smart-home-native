package zhb.company.smart_home.di

import org.koin.dsl.module
import zhb.company.smart_home.data.mqtt.HiveMqConnector
import zhb.company.smart_home.data.mqtt.MqttConnector
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.domain.GoodNightUseCase

val mqttModule = module {
    single<MqttConnector> { HiveMqConnector() }
    single { SmartHomeRepository(configSource = get<FirebaseRtdbApi>(), connector = get()) }
    single { GoodNightUseCase(get()) }
}
