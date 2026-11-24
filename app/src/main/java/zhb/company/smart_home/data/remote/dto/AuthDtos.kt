package zhb.company.smart_home.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
    // No default value on purpose: kotlinx.serialization leaves fields equal to their default out
    // of the JSON, and without returnSecureToken=true the API returns a legacy Identity Toolkit
    // token that Realtime Database rules treat as "not signed in" (401 Permission denied).
    val returnSecureToken: Boolean,
)

@Serializable
data class SignInResponse(
    val idToken: String = "",
    val email: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "3600",
    val localId: String = "",
)

/** Secure Token API answer to a refresh (`grant_type=refresh_token`); field names are snake_case there. */
@Serializable
data class RefreshResponse(
    @SerialName("id_token") val idToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: String = "3600",
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
