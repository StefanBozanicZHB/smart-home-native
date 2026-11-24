package zhb.company.smart_home.ui.auth

data class AuthState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** A session from an earlier sign-in is saved: it opens after unlocking (fingerprint or phone PIN). */
    val canUnlock: Boolean = false,
)

sealed interface AuthIntent {
    data class EmailChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data object Submit : AuthIntent

    /** The phone confirmed the user (fingerprint, face or PIN). */
    data object Unlocked : AuthIntent

    /** The phone has no screen lock at all, so there is nothing to confirm with. */
    data object NoScreenLock : AuthIntent
    data object UseUnlock : AuthIntent
}

sealed interface AuthEffect {
    data object NavigateToMqttConnect : AuthEffect
    data object RequestUnlock : AuthEffect
}
