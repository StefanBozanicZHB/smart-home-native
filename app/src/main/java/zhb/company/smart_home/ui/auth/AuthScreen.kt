package zhb.company.smart_home.ui.auth

import android.app.KeyguardManager
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.Tone

@Composable
fun AuthScreen(viewModel: AuthViewModel = koinViewModel(), onAuthenticated: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity

    /**
     * Asks the phone to confirm the user: fingerprint or face, or the screen-lock PIN/pattern. A phone
     * without any screen lock has nothing to ask, so the saved session opens directly; if the lock
     * exists but cannot be used right now, the form stays (sign in with the password).
     */
    fun requestUnlock() {
        val fragmentActivity = activity ?: return
        val keyguard = fragmentActivity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure == false) {
            viewModel.onIntent(AuthIntent.NoScreenLock)
            return
        }
        if (BiometricManager.from(fragmentActivity).canAuthenticate(UNLOCK_AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) return
        val prompt = BiometricPrompt(
            fragmentActivity,
            ContextCompat.getMainExecutor(fragmentActivity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onIntent(AuthIntent.Unlocked)
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragmentActivity.getString(R.string.auth_biometric_title))
            .setSubtitle(fragmentActivity.getString(R.string.auth_biometric_subtitle))
            // With the device credential allowed the prompt brings its own way out (no negative button).
            .setAllowedAuthenticators(UNLOCK_AUTHENTICATORS)
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AuthEffect.NavigateToMqttConnect -> onAuthenticated()
                AuthEffect.RequestUnlock -> requestUnlock()
            }
        }
    }

    AuthContent(state, viewModel::onIntent)
}

@Composable
private fun AuthContent(state: AuthState, onIntent: (AuthIntent) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    // The field keeps its own text state; the view model gets every change (and is the one that submits).
    val passwordState = rememberTextFieldState(state.password)
    LaunchedEffect(passwordState) {
        snapshotFlow { passwordState.text.toString() }.collect { onIntent(AuthIntent.PasswordChanged(it)) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.HomeWork, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
                }
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.auth_welcome_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
                label = { Text(stringResource(R.string.auth_email_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedSecureTextField(
                state = passwordState,
                label = { Text(stringResource(R.string.auth_password_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                    }
                },
                textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                onKeyboardAction = { onIntent(AuthIntent.Submit) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let { code -> AlertBanner(Icons.Outlined.ErrorOutline, errorMessageFor(code), Tone.DANGER) }

            Button(
                onClick = { onIntent(AuthIntent.Submit) },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.auth_submit), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (state.canUnlock) {
                OutlinedButton(
                    onClick = { onIntent(AuthIntent.UseUnlock) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.auth_use_biometrics), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Validation codes from the view model, or Firebase Identity Toolkit error codes. */
@Composable
private fun errorMessageFor(code: String): String = when {
    code == "invalid_email" -> stringResource(R.string.auth_error_invalid_email)
    code == "invalid_password" -> stringResource(R.string.auth_error_invalid_password)
    code == AuthViewModel.SESSION_EXPIRED -> stringResource(R.string.auth_error_session_expired)
    code.startsWith("INVALID_LOGIN_CREDENTIALS") || code.startsWith("INVALID_PASSWORD") || code.startsWith("EMAIL_NOT_FOUND") ->
        stringResource(R.string.auth_error_wrong_credentials)
    code.startsWith("TOO_MANY_ATTEMPTS") -> stringResource(R.string.auth_error_too_many)
    code == zhb.company.smart_home.data.remote.FirebaseAuthApi.NETWORK_ERROR -> stringResource(R.string.auth_error_network)
    else -> stringResource(R.string.auth_error_generic)
}

@androidx.compose.ui.tooling.preview.Preview(name = "Sign in - wrong password", heightDp = 800)
@androidx.compose.ui.tooling.preview.Preview(name = "Sign in - font 200%", heightDp = 1500, fontScale = 2f)
@Composable
private fun AuthPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            AuthContent(
                AuthState(email = "ime@primer.rs", password = "secret12", errorMessage = "INVALID_LOGIN_CREDENTIALS", canUnlock = true),
                onIntent = {},
            )
        }
    }
}

/** Fingerprint or face, or the phone's screen-lock PIN, pattern or password. */
private const val UNLOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
