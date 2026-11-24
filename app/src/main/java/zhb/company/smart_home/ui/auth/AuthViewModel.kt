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
import zhb.company.smart_home.data.remote.FirebaseAuthApi
import zhb.company.smart_home.data.repository.AuthRepository

/**
 * Sign-in. With a saved session the phone's own lock (fingerprint or PIN) opens it; the password is
 * never stored or shown. Cancelling the unlock leaves the form, which needs the real password.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>()
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val email = authRepository.savedEmail().orEmpty()
            val canUnlock = authRepository.hasSavedSession()
            _state.update { it.copy(email = email, canUnlock = canUnlock) }
            if (canUnlock) _effects.send(AuthEffect.RequestUnlock)
        }
    }

    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged -> _state.update { it.copy(email = intent.value) }
            is AuthIntent.PasswordChanged -> _state.update { it.copy(password = intent.value) }
            AuthIntent.Submit -> submit()
            AuthIntent.Unlocked, AuthIntent.NoScreenLock -> resume()
            AuthIntent.UseUnlock -> viewModelScope.launch { _effects.send(AuthEffect.RequestUnlock) }
        }
    }

    private fun resume() {
        if (!_state.value.canUnlock || _state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.resume()
            val stillSaved = authRepository.hasSavedSession()
            _state.update { it.copy(isLoading = false, canUnlock = stillSaved) }
            result.fold(
                onSuccess = { _effects.send(AuthEffect.NavigateToMqttConnect) },
                onFailure = { error ->
                    // A network problem keeps the session; anything else means the password is needed again.
                    val code = if (error.message == FirebaseAuthApi.NETWORK_ERROR) FirebaseAuthApi.NETWORK_ERROR else SESSION_EXPIRED
                    _state.update { it.copy(errorMessage = code) }
                },
            )
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
                onSuccess = {
                    _state.update { it.copy(password = "") } // not kept in memory longer than needed
                    _effects.send(AuthEffect.NavigateToMqttConnect)
                },
                // Firebase error codes (e.g. INVALID_LOGIN_CREDENTIALS) are mapped to text by the screen.
                onFailure = { error -> _state.update { it.copy(errorMessage = error.message ?: "auth_failed") } },
            )
        }
    }

    companion object {
        /** Error code: the saved session no longer works (e.g. password changed elsewhere). */
        const val SESSION_EXPIRED = "session_expired"
    }
}
