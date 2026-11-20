package zhb.company.smart_home.di

import org.koin.dsl.module
import zhb.company.smart_home.data.remote.FirebaseAuthApi
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.data.remote.RtdbReader
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.reports.HeatingReportsApi
import zhb.company.smart_home.data.repository.AuthRepository

val networkModule = module {
    single { FirebaseAuthApi(get()) }
    single { FirebaseRtdbApi(get()) }
    single { AuthRepository(get(), get()) }
    single { RtdbReader(get(), get()) }
    single { HeatingReportsApi(get()) }
    single { HistoryRepository(get()) }
}
