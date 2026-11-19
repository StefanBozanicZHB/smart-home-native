package zhb.company.smart_home.ui.auth

data class AuthState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canUseBiometrics: Boolean = false,
)

sealed interface AuthIntent {
    data class EmailChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data object Submit : AuthIntent
    data object BiometricSucceeded : AuthIntent
}

sealed interface AuthEffect {
    data object NavigateToMqttConnect : AuthEffect
    data object RequestBiometricPrompt : AuthEffect
    data class ShowError(val message: String) : AuthEffect
}
