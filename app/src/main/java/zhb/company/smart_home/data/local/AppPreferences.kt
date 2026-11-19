package zhb.company.smart_home.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smart_home_prefs")

/** Replaces the Flutter app's SharedPreferences-backed AppLocalStorage. */
class AppPreferences(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
    val password: Flow<String?> = context.dataStore.data.map { it[Keys.PASSWORD] }
    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME] ?: false }

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
}
