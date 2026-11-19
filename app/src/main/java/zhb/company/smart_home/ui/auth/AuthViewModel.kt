package zhb.company.smart_home.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.repository.AuthRepository

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>()
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val saved = authRepository.trySavedCredentials()
            if (saved != null) {
                _state.update { it.copy(email = saved.first, password = saved.second, canUseBiometrics = true) }
                _effects.send(AuthEffect.RequestBiometricPrompt)
            }
        }
    }

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged -> _state.update { it.copy(email = intent.value) }
            is AuthIntent.PasswordChanged -> _state.update { it.copy(password = intent.value) }
            AuthIntent.Submit -> submit()
            AuthIntent.BiometricSucceeded -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.email.contains("@")) {
            _state.update { it.copy(errorMessage = "invalid_email") }
            return
        }
        if (current.password.length < 5) {
            _state.update { it.copy(errorMessage = "invalid_password") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(current.email, current.password)
            _state.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { _effects.send(AuthEffect.NavigateToMqttConnect) },
                onFailure = { _effects.send(AuthEffect.ShowError(it.message ?: "auth_failed")) },
            )
        }
    }
}
