package zhb.company.smart_home

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import zhb.company.smart_home.di.appModule
import zhb.company.smart_home.di.mqttModule
import zhb.company.smart_home.di.networkModule
import zhb.company.smart_home.di.viewModelModule

class SmartHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SmartHomeApp)
            modules(appModule, networkModule, mqttModule, viewModelModule)
        }
    }
}
