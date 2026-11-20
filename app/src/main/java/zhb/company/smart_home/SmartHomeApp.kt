package zhb.company.smart_home

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import zhb.company.smart_home.data.mqtt.ConnectionLifecycle
import zhb.company.smart_home.data.notifications.PushNotifications
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
        watchVisibilityAndNetwork(get())
        get<PushNotifications>().start()
    }

    /** Feeds app foreground/background and "network is back" events to the MQTT connection. */
    private fun watchVisibilityAndNetwork(lifecycle: ConnectionLifecycle) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = lifecycle.onForeground()
                override fun onStop(owner: LifecycleOwner) = lifecycle.onBackground()
            },
        )
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) lifecycle.onNetworkAvailable()
                }
            },
        )
    }
}
