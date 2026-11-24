package zhb.company.smart_home.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zhb.company.smart_home.data.local.SecretCipher
import zhb.company.smart_home.data.local.SessionStore
import zhb.company.smart_home.data.remote.AuthApi
import zhb.company.smart_home.data.remote.AuthGrant
import zhb.company.smart_home.data.remote.FirebaseAuthApi

class AuthRepositoryTest {

    private class FakeStore : SessionStore {
        var email: String? = null
        var sealed: String? = null
        var password: String? = null
        override suspend fun savedEmail() = email
        override suspend fun sealedRefreshToken() = sealed
        override suspend fun legacyPassword() = password
        override suspend fun saveSession(email: String, sealedRefreshToken: String) {
            this.email = email
            sealed = sealedRefreshToken
            password = null
        }
        override suspend fun clearSession() {
            sealed = null
            password = null
        }
    }

    /** Reversible stand-in for the Keystore; "other-key:" values cannot be decrypted. */
    private class FakeCipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(sealed: String) = sealed.removePrefix("enc:").takeIf { sealed.startsWith("enc:") }
    }

    private class FakeApi : AuthApi {
        var signIns = 0
        var refreshes = 0
        var refreshFailure: String? = null
        var counter = 0
        override suspend fun signInWithPassword(email: String, password: String): Result<AuthGrant> {
            signIns++
            return if (password == "correct") Result.success(grant()) else Result.failure(Exception("INVALID_LOGIN_CREDENTIALS"))
        }
        override suspend fun refresh(refreshToken: String): Result<AuthGrant> {
            refreshes++
            delay(10)
            refreshFailure?.let { return Result.failure(Exception(it)) }
            return Result.success(grant())
        }
        private fun grant(): AuthGrant {
            counter++
            return AuthGrant("id-$counter", "refresh-$counter", 3600)
        }
    }

    private var now = 1_000_000L
    private val store = FakeStore()
    private val api = FakeApi()
    private val repo = AuthRepository(api, store, FakeCipher(), clock = { now })

    @Test
    fun `sign-in keeps an encrypted refresh token, never the password`() = runTest {
        store.password = "old plain password"
        assertTrue(repo.login("a@b.rs", "correct").isSuccess)

        assertEquals("enc:refresh-1", store.sealed)
        assertNull("legacy password removed", store.password)
        assertEquals("id-1", repo.idToken)
        assertTrue(repo.hasSavedSession())
    }

    @Test
    fun `a valid token is reused, an expired one is renewed from the saved session`() = runTest {
        repo.login("a@b.rs", "correct")
        assertEquals("id-1", repo.freshIdToken())
        assertEquals(0, api.refreshes)

        now += 3600_000
        assertEquals("id-2", repo.freshIdToken())
        assertEquals(1, api.refreshes)
        assertEquals("the rotated refresh token is stored", "enc:refresh-2", store.sealed)
    }

    @Test
    fun `parallel callers share one refresh`() = runTest {
        repo.login("a@b.rs", "correct")
        now += 3600_000
        val tokens = List(5) { async { repo.freshIdToken() } }.awaitAll()
        assertEquals(1, api.refreshes)
        assertTrue(tokens.all { it == "id-2" })
    }

    @Test
    fun `no network keeps the session, a revoked token forgets it`() = runTest {
        repo.login("a@b.rs", "correct")
        now += 3600_000

        api.refreshFailure = FirebaseAuthApi.NETWORK_ERROR
        assertEquals(FirebaseAuthApi.NETWORK_ERROR, repo.idTokenResult().exceptionOrNull()?.message)
        assertTrue(repo.hasSavedSession())

        api.refreshFailure = "TOKEN_EXPIRED"
        assertTrue(repo.resume().isFailure)
        assertFalse(repo.hasSavedSession())
        assertEquals("the email stays for the sign-in form", "a@b.rs", repo.savedEmail())
    }

    @Test
    fun `a password saved by an older version is exchanged for a token once`() = runTest {
        store.email = "a@b.rs"
        store.password = "correct"
        assertTrue(repo.hasSavedSession())

        assertTrue(repo.resume().isSuccess)

        assertEquals(1, api.signIns)
        assertNull(store.password)
        assertEquals("enc:refresh-1", store.sealed)
    }

    @Test
    fun `a session from another phone cannot be decrypted and is dropped`() = runTest {
        store.email = "a@b.rs"
        store.sealed = "other-key:xyz"

        assertEquals(AuthRepository.NOT_SIGNED_IN, repo.resume().exceptionOrNull()?.message)
        assertFalse(repo.hasSavedSession())
        assertEquals(0, api.refreshes)
    }

    @Test
    fun `sign-out forgets the session`() = runTest {
        repo.login("a@b.rs", "correct")
        repo.signOut()

        assertFalse(repo.hasSavedSession())
        assertNull(repo.idToken)
        assertEquals(AuthRepository.NOT_SIGNED_IN, repo.idTokenResult().exceptionOrNull()?.message)
    }
}
