package com.example.securepool

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.securepool.ui.model.LoginUiState
import com.example.securepool.ui.model.LoginViewModel
import com.example.securepool.ui.theme.SecurePoolTheme
import com.example.securepool.ui.NavigationEvent
import com.example.securepool.security.AuditLogger // <-- added

class LoginActivity : FragmentActivity() {
    private val viewModel: LoginViewModel by viewModels()
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var biometricKeyManager: BiometricKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        biometricKeyManager = BiometricKeyManager(applicationContext)

        setupBiometricPrompt()
        checkBiometricSupport()

        setContent {
            SecurePoolTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { event ->
                        if (event is NavigationEvent.NavigateToHome) {
                            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.showBiometricPromptRequest.collect { challenge ->
                        try {
                            val signature = biometricKeyManager.signChallenge(challenge)
                            if (signature != null) {
                                val cryptoObject = BiometricPrompt.CryptoObject(signature)
                                biometricPrompt.authenticate(promptInfo, cryptoObject)
                            }

                        } catch (e: Exception) {
                            Log.e("LoginActivity", "Error preparing for biometric auth", e)
                            Toast.makeText(this@LoginActivity, "Could not start biometric login.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                // ✅ Add this block
                LaunchedEffect(Unit) {
                    viewModel.rateLimitEvent.collect {
                        Toast.makeText(this@LoginActivity, "Too many login attempts. Please try again later.", Toast.LENGTH_LONG).show()
                    }
                }
                LaunchedEffect(Unit) {
                    viewModel.registerRateLimitEvent.collect {
                        Toast.makeText(
                            this@LoginActivity,
                            "Too many registration attempts. Please try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onPasswordVisibilityChange = viewModel::onPasswordVisibilityChange,
                    onLoginClicked = {
                        AuditLogger.logEvent("Login Attempt", mapOf("method" to "password", "username" to uiState.username))
                        viewModel.loginUser()
                    },
                    onBiometricLoginClicked = {
                        AuditLogger.logEvent("Biometric Login Attempt", mapOf("username" to uiState.username))
                        viewModel.onBiometricLoginClicked()
                    },
                    onRegisterClicked = viewModel::registerUser
                )
            }
        }
    }

    private fun checkBiometricSupport() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
        viewModel.setBiometricAvailable(canAuthenticate)
        viewModel.setBiometricRegistered(biometricKeyManager.keyPairExists())
    }

    private fun setupBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    AuditLogger.logEvent("Biometric Auth Error", mapOf("code" to errorCode.toString(), "message" to errString.toString()))
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val signature = result.cryptoObject?.signature
                    if (signature != null) {
                        try {
                            val signedChallengeBytes = signature.sign()
                            val signedChallengeBase64 = Base64.encodeToString(signedChallengeBytes, Base64.NO_WRAP)
                            AuditLogger.logEvent("Biometric Authentication Succeeded", mapOf("username" to viewModel.uiState.value.username))
                            viewModel.loginWithSignedChallenge(signedChallengeBase64)
                        } catch (e: Exception) {
                            Log.e("LoginActivity", "Error signing challenge", e)
                            Toast.makeText(applicationContext, "Failed to sign challenge.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("LoginActivity", "CryptoObject was null in onAuthenticationSucceeded.")
                        Toast.makeText(applicationContext, "An unexpected error occurred.", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    AuditLogger.logEvent("Biometric Auth Failed", mapOf("username" to viewModel.uiState.value.username))
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric login for my app")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onLoginClicked: () -> Unit,
    onBiometricLoginClicked: () -> Unit,
    onRegisterClicked: () -> Unit
) {
    val isUsernamePopulated = uiState.username.isNotBlank()
    val isPasswordPopulated = uiState.password.isNotBlank()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SecurePool Login") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                enabled = !uiState.isLoading,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val icon = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    IconButton(onClick = { onPasswordVisibilityChange(!uiState.isPasswordVisible) }) {
                        Icon(icon, "Toggle password visibility")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onLoginClicked,
                    enabled = !uiState.isLoading && isUsernamePopulated && isPasswordPopulated,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Login")
                    }
                }
            }

            if (uiState.isBiometricAvailable && uiState.isBiometricRegistered) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Button(
                            onClick = onBiometricLoginClicked,
                            enabled = !uiState.isLoading && isUsernamePopulated,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Text("Use Fingerprint")
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Login with fingerprint"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onRegisterClicked,
                    enabled = !uiState.isLoading && isUsernamePopulated && isPasswordPopulated,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text("Register")
                    }
                }
            }
        }
    }
}
