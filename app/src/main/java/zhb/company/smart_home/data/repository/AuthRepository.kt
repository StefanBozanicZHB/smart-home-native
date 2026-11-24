package zhb.company.smart_home.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zhb.company.smart_home.data.local.SecretCipher
import zhb.company.smart_home.data.local.SessionStore
import zhb.company.smart_home.data.remote.AuthApi
import zhb.company.smart_home.data.remote.AuthGrant
import zhb.company.smart_home.data.remote.FirebaseAuthApi

/**
 * The Firebase session. The password is used only to sign in; what is kept is the refresh token,
 * encrypted with a Keystore key ([SecretCipher]), from which a new ID token is made whenever the
 * current one (valid one hour) has expired. The ID token itself lives only in memory.
 */
class AuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val cipher: SecretCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var idToken: String? = null
        private set

    @Volatile
    private var expiryEpochMillis: Long = 0L

    /** One sign-in or refresh at a time; callers that come in meanwhile get its result. */
    private val mutex = Mutex()

    val isAuthenticated: Boolean
        get() = idToken != null && clock() < expiryEpochMillis

    suspend fun login(email: String, password: String): Result<Unit> = mutex.withLock {
        api.signInWithPassword(email, password).map { start(email, it) }
    }

    /** True when a session from an earlier sign-in is saved (possibly still as an old password). */
    suspend fun hasSavedSession(): Boolean = store.sealedRefreshToken() != null || store.legacyPassword() != null

    suspend fun savedEmail(): String? = store.savedEmail()

    /** Signs in again from the saved session (after the app is unlocked). */
    suspend fun resume(): Result<Unit> = mutex.withLock { renew().map { } }

    /**
     * A valid ID token for Firebase REST calls: the current one, or a new one from the saved session.
     * Fails with [NOT_SIGNED_IN] when there is no usable session, or [FirebaseAuthApi.NETWORK_ERROR]
     * (the session is kept and works again once the network is back).
     */
    suspend fun idTokenResult(): Result<String> = mutex.withLock {
        val current = idToken
        if (current != null && clock() < expiryEpochMillis - TOKEN_MARGIN_MS) Result.success(current) else renew()
    }

    suspend fun freshIdToken(): String? = idTokenResult().getOrNull()

    /** Forgets the session on this phone: the next start asks for the password. */
    suspend fun signOut() = mutex.withLock {
        idToken = null
        expiryEpochMillis = 0L
        store.clearSession()
    }

    /** Called with [mutex] held. */
    private suspend fun renew(): Result<String> {
        val email = store.savedEmail().orEmpty()
        val sealed = store.sealedRefreshToken()
        val refreshToken = sealed?.let(cipher::decrypt)
        val result = when {
            refreshToken != null -> api.refresh(refreshToken)
            // Saved by an older version as a plain password: sign in once, then keep a token instead.
            email.isNotBlank() && store.legacyPassword() != null -> api.signInWithPassword(email, store.legacyPassword()!!)
            else -> {
                // Nothing saved, or a token this phone cannot decrypt (e.g. restored from another phone).
                if (sealed != null) store.clearSession()
                return Result.failure(Exception(NOT_SIGNED_IN))
            }
        }
        return result.fold(
            onSuccess = { grant ->
                start(email, grant)
                Result.success(grant.idToken)
            },
            onFailure = { error ->
                if (isRevoked(error)) {
                    idToken = null
                    store.clearSession()
                }
                Result.failure(error)
            },
        )
    }

    private suspend fun start(email: String, grant: AuthGrant) {
        idToken = grant.idToken
        expiryEpochMillis = clock() + grant.expiresInSeconds * 1000
        store.saveSession(email, cipher.encrypt(grant.refreshToken))
    }

    companion object {
        /** Failure code when no session is saved. */
        const val NOT_SIGNED_IN = "NOT_SIGNED_IN"

        private const val TOKEN_MARGIN_MS = 60_000L

        /**
         * Firebase answers that mean the saved session is no longer valid (password changed, user
         * disabled or removed, token revoked); anything else (network, server trouble, too many
         * attempts) keeps it for the next try.
         */
        private val REVOKED = listOf(
            "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "USER_DISABLED", "USER_NOT_FOUND",
            "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "INVALID_GRANT_TYPE", "MISSING_REFRESH_TOKEN",
        )

        fun isRevoked(error: Throwable): Boolean = REVOKED.any { error.message.orEmpty().startsWith(it) }
    }
}
