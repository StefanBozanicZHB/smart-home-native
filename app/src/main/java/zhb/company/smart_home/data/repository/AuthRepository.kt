package zhb.company.smart_home.data.repository

import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.remote.FirebaseAuthApi

/** In-memory session + credential persistence, mirroring the Flutter `Auth` provider. */
class AuthRepository(
    private val authApi: FirebaseAuthApi,
    private val preferences: AppPreferences,
) {
    var idToken: String? = null
        private set

    private var expiryEpochMillis: Long = 0L

    val isAuthenticated: Boolean
        get() = idToken != null && System.currentTimeMillis() < expiryEpochMillis

    suspend fun login(email: String, password: String): Result<Unit> {
        val result = authApi.signInWithPassword(email, password)
        return result.fold(
            onSuccess = { response ->
                idToken = response.idToken
                val expiresInSeconds = response.expiresIn.toLongOrNull() ?: 3600L
                expiryEpochMillis = System.currentTimeMillis() + expiresInSeconds * 1000
                preferences.saveCredentials(email, password)
                Result.success(Unit)
            },
            onFailure = { Result.failure(it) },
        )
    }

    /**
     * A valid ID token for Firebase REST calls: the current one, or a new sign-in with the saved
     * credentials once it has expired (tokens last one hour). Null if that is not possible.
     */
    suspend fun freshIdToken(): String? {
        if (idToken != null && System.currentTimeMillis() < expiryEpochMillis - TOKEN_MARGIN_MS) return idToken
        val (user, pass) = trySavedCredentials() ?: return null
        return login(user, pass).fold(onSuccess = { idToken }, onFailure = { null })
    }

    suspend fun trySavedCredentials(): Pair<String, String>? {
        val user = preferences.savedUsername()
        val pass = preferences.savedPassword()
        return if (!user.isNullOrBlank() && !pass.isNullOrBlank()) user to pass else null
    }

    private companion object {
        const val TOKEN_MARGIN_MS = 60_000L
    }
}
