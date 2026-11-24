package zhb.company.smart_home.di

import org.koin.dsl.module
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.local.KeystoreCipher
import zhb.company.smart_home.data.local.SecretCipher
import zhb.company.smart_home.data.local.SessionStore
import zhb.company.smart_home.data.remote.AuthApi
import zhb.company.smart_home.data.remote.FirebaseAuthApi
import zhb.company.smart_home.data.remote.FirebaseRtdbApi
import zhb.company.smart_home.data.remote.RtdbReader
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.reports.HeatingReportsApi
import zhb.company.smart_home.data.repository.AuthRepository

val networkModule = module {
    single<AuthApi> { FirebaseAuthApi(get()) }
    single { FirebaseRtdbApi(get()) }
    single<SessionStore> { get<AppPreferences>() }
    single<SecretCipher> { KeystoreCipher() }
    single { AuthRepository(get(), get(), get()) }
    single { RtdbReader(get(), get()) }
    single { HeatingReportsApi(get()) }
    single { HistoryRepository(get()) }
}
