package zhb.company.smart_home.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true,
)

@Serializable
data class SignInResponse(
    val idToken: String = "",
    val email: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "3600",
    val localId: String = "",
)

@Serializable
data class FirebaseErrorEnvelope(
    val error: FirebaseError = FirebaseError(),
)

@Serializable
data class FirebaseError(
    val code: Int = 0,
    val message: String = "",
)
