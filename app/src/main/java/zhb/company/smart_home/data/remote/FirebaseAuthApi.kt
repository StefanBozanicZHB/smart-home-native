package zhb.company.smart_home.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import zhb.company.smart_home.BuildConfig
import zhb.company.smart_home.data.remote.dto.FirebaseErrorEnvelope
import zhb.company.smart_home.data.remote.dto.RefreshResponse
import zhb.company.smart_home.data.remote.dto.SignInRequest
import zhb.company.smart_home.data.remote.dto.SignInResponse

/** A signed-in session: the short-lived ID token and the refresh token that renews it. */
data class AuthGrant(val idToken: String, val refreshToken: String, val expiresInSeconds: Long)

/**
 * Firebase sign-in. Failures carry the Firebase error code as their message (e.g.
 * `INVALID_LOGIN_CREDENTIALS`, `TOKEN_EXPIRED`), or [FirebaseAuthApi.NETWORK_ERROR].
 */
interface AuthApi {
    suspend fun signInWithPassword(email: String, password: String): Result<AuthGrant>

    /** A new ID token (and possibly a new refresh token) for [refreshToken]. */
    suspend fun refresh(refreshToken: String): Result<AuthGrant>
}

/** Firebase Identity Toolkit and Secure Token REST APIs (no Firebase SDK). */
class FirebaseAuthApi(private val httpClient: HttpClient) : AuthApi {

    // Firebase Web API key, injected from local.properties (firebase.apiKey) at build time.
    private val apiKey = BuildConfig.FIREBASE_API_KEY

    /**
     * Never throws for network problems (no internet, DNS, timeout): those come back as a failure
     * with [NETWORK_ERROR], so a sign-in without a connection shows a message instead of crashing.
     */
    override suspend fun signInWithPassword(email: String, password: String): Result<AuthGrant> = call(
        request = {
            httpClient.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(email = email, password = password, returnSecureToken = true))
            }
        },
        parse = { it.body<SignInResponse>().let { r -> AuthGrant(r.idToken, r.refreshToken, r.expiresIn.toLongOrNull() ?: 3600L) } },
    )

    override suspend fun refresh(refreshToken: String): Result<AuthGrant> = call(
        request = {
            httpClient.post("https://securetoken.googleapis.com/v1/token?key=$apiKey") {
                setBody(
                    FormDataContent(
                        parameters {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refreshToken)
                        },
                    ),
                )
            }
        },
        parse = { it.body<RefreshResponse>().let { r -> AuthGrant(r.idToken, r.refreshToken, r.expiresIn.toLongOrNull() ?: 3600L) } },
    )

    private suspend fun call(request: suspend () -> HttpResponse, parse: suspend (HttpResponse) -> AuthGrant): Result<AuthGrant> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.failure(Exception(NETWORK_ERROR))
        }
        return if (response.status.isSuccess()) {
            runCatching { parse(response) }.mapCatching { grant ->
                check(grant.idToken.isNotBlank() && grant.refreshToken.isNotBlank()) { "AUTH_FAILED" }
                grant
            }
        } else {
            val error = runCatching { response.body<FirebaseErrorEnvelope>() }.getOrNull()
            Result.failure(Exception(error?.error?.message?.takeIf { it.isNotBlank() } ?: "AUTH_FAILED"))
        }
    }

    companion object {
        /** Failure code for "the Firebase server could not be reached". */
        const val NETWORK_ERROR = "NETWORK_ERROR"
    }
}
