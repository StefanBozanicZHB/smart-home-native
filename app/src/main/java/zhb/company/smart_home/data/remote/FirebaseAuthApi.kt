package zhb.company.smart_home.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import zhb.company.smart_home.BuildConfig
import zhb.company.smart_home.data.remote.dto.FirebaseErrorEnvelope
import zhb.company.smart_home.data.remote.dto.SignInRequest
import zhb.company.smart_home.data.remote.dto.SignInResponse

/** Firebase Identity Toolkit REST sign-in (no Firebase SDK). */
class FirebaseAuthApi(private val httpClient: HttpClient) {

    // Firebase Web API key, injected from local.properties (firebase.apiKey) at build time.
    private val apiKey = BuildConfig.FIREBASE_API_KEY

    /**
     * Never throws for network problems (no internet, DNS, timeout): those come back as a failure
     * with [NETWORK_ERROR], so a sign-in without a connection shows a message instead of crashing.
     */
    suspend fun signInWithPassword(email: String, password: String): Result<SignInResponse> {
        val response: HttpResponse = try {
            httpClient.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(SignInRequest(email = email, password = password, returnSecureToken = true))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.failure(Exception(NETWORK_ERROR))
        }
        return if (response.status.isSuccess()) {
            Result.success(response.body<SignInResponse>())
        } else {
            val error = runCatching { response.body<FirebaseErrorEnvelope>() }.getOrNull()
            Result.failure(Exception(error?.error?.message ?: "AUTH_FAILED"))
        }
    }

    companion object {
        /** Failure code for "the Firebase server could not be reached". */
        const val NETWORK_ERROR = "NETWORK_ERROR"
    }
}
