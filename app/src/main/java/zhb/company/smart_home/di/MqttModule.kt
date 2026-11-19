package zhb.company.smart_home.di

import org.koin.dsl.module
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.GoodNightUseCase

val mqttModule = module {
    single { SmartHomeRepository(get()) }
    single { GoodNightUseCase(get()) }
}
