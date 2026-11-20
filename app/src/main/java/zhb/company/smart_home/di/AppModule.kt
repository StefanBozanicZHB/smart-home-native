package zhb.company.smart_home.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.ui.navigation.PendingNavigation

val appModule = module {
    single { AppPreferences(androidContext()) }
    single { PendingNavigation() }

    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
