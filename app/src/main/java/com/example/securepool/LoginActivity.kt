package com.example.securepool

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Load and log secret key from BuildConfig (for testing only)
        val secretKey = BuildConfig.MY_SECRET_KEY
        Log.d("SECUREPOOL", "Loaded secret: $secretKey")

        setContent {
            SecurePoolTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Listen for one-time navigation events
                LaunchedEffect(Unit) {
                    viewModel.navigationEvent.collect { event ->
                        when (event) {
                            is NavigationEvent.NavigateToHome -> {
                                val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                            }
                            else -> {} // Ignore other events
                        }
                    }
                }

                LoginScreen(
                    uiState = uiState,
                    // Pass all event handlers to the stateless screen
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onPasswordVisibilityChange = viewModel::onPasswordVisibilityChange,
                    onLoginClicked = viewModel::loginUser
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onLoginClicked: () -> Unit
) {
    // This composable no longer holds any state. It's fully controlled by the ViewModel.
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
                value = uiState.username, // Read value from state
                onValueChange = onUsernameChange, // Call lambda on change
                label = { Text("Username") },
                singleLine = true,
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = uiState.password, // Read value from state
                onValueChange = onPasswordChange, // Call lambda on change
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Button(
                onClick = onLoginClicked, // Call lambda on click
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Login")
                }
            }
        }
    }
}
