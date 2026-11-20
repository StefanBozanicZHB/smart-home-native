package zhb.company.smart_home.di

import org.koin.core.module.dsl.viewModel
import zhb.company.smart_home.ui.garden.IrrigationPlanViewModel
import zhb.company.smart_home.ui.history.HistoryViewModel
import zhb.company.smart_home.ui.notifications.NotificationsViewModel
import org.koin.android.ext.koin.androidContext
import zhb.company.smart_home.ui.system.NodeDetailViewModel
import org.koin.dsl.module
import zhb.company.smart_home.ui.ThemeStateHolder
import zhb.company.smart_home.ui.auth.AuthViewModel
import zhb.company.smart_home.ui.garden.GardenViewModel
import zhb.company.smart_home.ui.heating.HeatingViewModel
import zhb.company.smart_home.ui.heating.PipeCalibrationViewModel
import zhb.company.smart_home.ui.heating.PipeSensorsViewModel
import zhb.company.smart_home.ui.home.HomeViewModel
import zhb.company.smart_home.ui.house.HouseViewModel
import zhb.company.smart_home.ui.mqttconnect.MqttConnectViewModel
import zhb.company.smart_home.ui.reports.ReportDetailViewModel
import zhb.company.smart_home.ui.reports.ReportsViewModel
import zhb.company.smart_home.ui.settings.ConnectionsViewModel
import zhb.company.smart_home.ui.settings.SettingsViewModel
import zhb.company.smart_home.ui.smallhome.SmallHomeViewModel

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { MqttConnectViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GardenViewModel(get(), get(), get(), get()) }
    viewModel { IrrigationPlanViewModel(get(), get()) }
    viewModel { HeatingViewModel(get(), get()) }
    viewModel { PipeSensorsViewModel(get(), get()) }
    viewModel { PipeCalibrationViewModel(get()) }
    viewModel { HouseViewModel(get()) }
    viewModel { SmallHomeViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { ConnectionsViewModel(get()) }
    viewModel { params -> NodeDetailViewModel(params.get(), get()) }
    viewModel { ThemeStateHolder(get()) }
    viewModel { ReportsViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { NotificationsViewModel(get(), androidContext()) }
    viewModel { params -> ReportDetailViewModel(params.get(), get()) }
}
