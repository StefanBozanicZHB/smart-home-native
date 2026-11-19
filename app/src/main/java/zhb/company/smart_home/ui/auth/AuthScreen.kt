package zhb.company.smart_home.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

/**
 * The auth card is always rendered on a literal white background regardless of the app's
 * light/dark theme, so its text/icon colors are fixed here rather than pulled from
 * MaterialTheme.colorScheme - the dark scheme's onSurface/onSurfaceVariant tokens are calibrated
 * for a dark card and read as washed-out light gray on white.
 */
private val CardTextPrimary = Color(0xFF16211E)
private val CardTextSecondary = Color(0xFF63736F)
private val CardFieldBorder = Color(0xFFB7C6C1)

@Composable
fun AuthScreen(viewModel: AuthViewModel = koinViewModel(), onAuthenticated: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var passwordVisible by remember { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        val fragmentActivity = activity ?: return
        val biometricManager = BiometricManager.from(fragmentActivity)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) return

        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onIntent(AuthIntent.BiometricSucceeded)
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragmentActivity.getString(R.string.auth_biometric_title))
            .setSubtitle(fragmentActivity.getString(R.string.auth_biometric_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(fragmentActivity.getString(android.R.string.cancel))
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AuthEffect.NavigateToMqttConnect -> onAuthenticated()
                AuthEffect.RequestBiometricPrompt -> launchBiometricPrompt()
                is AuthEffect.ShowError -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.img_primary_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, shadowElevation = 10.dp, color = White) {
                Image(
                    painter = painterResource(R.drawable.img_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = White,
            )
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = White.copy(alpha = 0.96f),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.auth_welcome_back),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryColor,
                    )
                    Text(
                        text = stringResource(R.string.auth_welcome_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CardTextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    )

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { viewModel.onIntent(AuthIntent.EmailChanged(it)) },
                        label = { Text(stringResource(R.string.auth_email_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onIntent(AuthIntent.PasswordChanged(it)) },
                        label = { Text(stringResource(R.string.auth_password_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = CardTextSecondary,
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.errorMessage?.let { code ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DisableRed.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = DisableRed, modifier = Modifier.size(18.dp))
                                Text(
                                    text = errorMessageFor(code),
                                    color = DisableRed,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.onIntent(AuthIntent.Submit) },
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .heightIn(min = 52.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Text(text = stringResource(R.string.auth_submit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (state.canUseBiometrics) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = CardTextSecondary, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.auth_biometric_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = CardTextSecondary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun errorMessageFor(code: String): String = when (code) {
    "invalid_email" -> stringResource(R.string.auth_error_invalid_email)
    "invalid_password" -> stringResource(R.string.auth_error_invalid_password)
    else -> stringResource(R.string.auth_error_generic)
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CardTextPrimary,
    unfocusedTextColor = CardTextPrimary,
    focusedBorderColor = PrimaryColor,
    unfocusedBorderColor = CardFieldBorder,
    focusedLabelColor = PrimaryColor,
    unfocusedLabelColor = CardTextSecondary,
    focusedLeadingIconColor = PrimaryColor,
    unfocusedLeadingIconColor = CardTextSecondary,
    cursorColor = PrimaryColor,
)
