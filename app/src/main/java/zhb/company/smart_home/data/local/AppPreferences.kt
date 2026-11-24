package zhb.company.smart_home.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import zhb.company.smart_home.data.notifications.QuietHours

private val Context.dataStore by preferencesDataStore(name = "smart_home_prefs")

/** Where the sign-in session is kept between app starts. */
interface SessionStore {
    suspend fun savedEmail(): String?

    /** The refresh token, encrypted ([SecretCipher]). */
    suspend fun sealedRefreshToken(): String?

    /** The password an older app version kept in plain text; only read once to replace it with a token. */
    suspend fun legacyPassword(): String?

    /** Saves the session and drops any legacy password. */
    suspend fun saveSession(email: String, sealedRefreshToken: String)

    /** Forgets the session (the email stays, to fill in the sign-in form). */
    suspend fun clearSession()
}

/** App settings and the saved sign-in session, in DataStore. */
class AppPreferences(private val context: Context) : SessionStore {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val SESSION = stringPreferencesKey("session")
        val PASSWORD = stringPreferencesKey("password")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START = stringPreferencesKey("quiet_hours_start")
        val QUIET_END = stringPreferencesKey("quiet_hours_end")
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: false }

    /** Push notifications from the house system (FCM topic); on by default. */
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFICATIONS] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    /** Quiet hours for notifications that are not urgent; on by default, 22:00-07:00. */
    val quietHours: Flow<QuietHours> = context.dataStore.data.map {
        QuietHours.of(
            enabled = it[Keys.QUIET_ENABLED] ?: true,
            start = it[Keys.QUIET_START] ?: QuietHours.DEFAULT_START,
            end = it[Keys.QUIET_END] ?: QuietHours.DEFAULT_END,
        )
    }

    suspend fun setQuietHours(enabled: Boolean, start: String, end: String) {
        require(QuietHours.parseTime(start) != null && QuietHours.parseTime(end) != null) { "Time must be HH:MM" }
        context.dataStore.edit {
            it[Keys.QUIET_ENABLED] = enabled
            it[Keys.QUIET_START] = start.trim()
            it[Keys.QUIET_END] = end.trim()
        }
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

    override suspend fun savedEmail(): String? = context.dataStore.data.first()[Keys.USERNAME]

    override suspend fun sealedRefreshToken(): String? = context.dataStore.data.first()[Keys.SESSION]

    override suspend fun legacyPassword(): String? = context.dataStore.data.first()[Keys.PASSWORD]

    override suspend fun saveSession(email: String, sealedRefreshToken: String) {
        context.dataStore.edit {
            it[Keys.USERNAME] = email
            it[Keys.SESSION] = sealedRefreshToken
            it.remove(Keys.PASSWORD)
        }
    }

    override suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.SESSION)
            it.remove(Keys.PASSWORD)
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }
}
