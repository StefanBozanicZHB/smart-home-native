package zhb.company.smart_home.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import zhb.company.smart_home.data.heating.Node5Topics

private val Context.dataStore by preferencesDataStore(name = "smart_home_prefs")

/** Replaces the Flutter app's SharedPreferences-backed AppLocalStorage. */
class AppPreferences(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NODE5_ID = stringPreferencesKey("node5_id")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
    }

    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
    val password: Flow<String?> = context.dataStore.data.map { it[Keys.PASSWORD] }
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: false }

    /** Node 5 MQTT node id: "node5" (real heat pump) or "testnode5" (bench-test board). */
    val node5Id: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.NODE5_ID]?.takeIf { it in Node5Topics.ALLOWED } ?: Node5Topics.REAL
    }

    /** Push notifications from the house system (FCM topic); on by default. */
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    /** True once the app has asked for the notification permission on its own (first start). */
    suspend fun markNotificationPermissionAsked(): Boolean {
        var wasAsked = false
        context.dataStore.edit {
            wasAsked = it[Keys.NOTIFICATION_PERMISSION_ASKED] ?: false
            it[Keys.NOTIFICATION_PERMISSION_ASKED] = true
        }
        return wasAsked
    }

    suspend fun savedUsername(): String? = username.first()
    suspend fun savedPassword(): String? = password.first()

    suspend fun saveCredentials(username: String, password: String) {
        context.dataStore.edit {
            it[Keys.USERNAME] = username
            it[Keys.PASSWORD] = password
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setNode5Id(nodeId: String) {
        require(nodeId in Node5Topics.ALLOWED) { "Unknown Node 5 id: $nodeId" }
        context.dataStore.edit { it[Keys.NODE5_ID] = nodeId }
    }
}
