package zhb.company.smart_home.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import zhb.company.smart_home.ui.ThemeStateHolder
import zhb.company.smart_home.ui.auth.AuthViewModel
import zhb.company.smart_home.ui.garden.GardenViewModel
import zhb.company.smart_home.ui.heating.HeatingViewModel
import zhb.company.smart_home.ui.home.HomeViewModel
import zhb.company.smart_home.ui.house.HouseViewModel
import zhb.company.smart_home.ui.mqttconnect.MqttConnectViewModel
import zhb.company.smart_home.ui.settings.ConnectionsViewModel
import zhb.company.smart_home.ui.settings.SettingsViewModel
import zhb.company.smart_home.ui.smallhome.SmallHomeViewModel

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { MqttConnectViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { GardenViewModel(get()) }
    viewModel { HeatingViewModel(get(), get()) }
    viewModel { HouseViewModel(get()) }
    viewModel { SmallHomeViewModel(get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ConnectionsViewModel(get()) }
    viewModel { ThemeStateHolder(get()) }
}
