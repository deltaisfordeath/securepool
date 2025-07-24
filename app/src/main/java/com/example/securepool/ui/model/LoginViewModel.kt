package com.example.securepool.ui.model

import android.app.Application
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securepool.BiometricKeyManager
import com.example.securepool.api.RetrofitClient
import com.example.securepool.model.RegisterRequest
import com.example.securepool.api.TokenManager
import com.example.securepool.model.SignedChallengeRequest
import com.example.securepool.ui.NavigationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isBiometricAvailable: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    // This flow now carries the challenge string to the Activity
    private val _showBiometricPromptRequest = MutableSharedFlow<String>()
    val showBiometricPromptRequest = _showBiometricPromptRequest.asSharedFlow()

    private val apiService = RetrofitClient.create(application)
    private val tokenManager = TokenManager(application)
    private val biometricKeyManager = BiometricKeyManager(application)

    init {
        val storedUsername = tokenManager.getUsername()
        if (!storedUsername.isNullOrBlank()) {
            _uiState.update { it.copy(username = storedUsername) }
        }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun onPasswordVisibilityChange(isVisible: Boolean) {
        _uiState.update { it.copy(isPasswordVisible = isVisible) }
    }

    fun setBiometricAvailable(isAvailable: Boolean) {
        _uiState.update { it.copy(isBiometricAvailable = isAvailable) }
    }

    /**
     * Step 1: User clicks biometric login. ViewModel fetches the challenge.
     */
    fun onBiometricLoginClicked() {
        if (_uiState.value.username.isBlank()) {
            Toast.makeText(getApplication(), "Please enter your username first.", Toast.LENGTH_SHORT).show()
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val challengeResponse = apiService.getChallenge(_uiState.value.username)
                if (challengeResponse.isSuccessful && challengeResponse.body() != null) {
                    val challenge = challengeResponse.body()!!.challenge
                    // Step 2: Emit challenge to Activity to trigger the prompt
                    _showBiometricPromptRequest.emit(challenge)
                } else {
                    Toast.makeText(getApplication(), "Could not get challenge from server.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Step 4: Called from the Activity with the signed challenge to complete login.
     */
    fun loginWithSignedChallenge(signedChallengeBase64: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val request = SignedChallengeRequest(_uiState.value.username, signedChallengeBase64)
                val loginResponse = apiService.postChallenge(request)
                val body = loginResponse.body()
                if (loginResponse.isSuccessful && body != null) {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUsername(body.username)
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                } else {
                    Toast.makeText(getApplication(), "Biometric login failed.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loginUser() {
        // ... (implementation unchanged)
    }

    fun registerUser() {
        val currentState = _uiState.value
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            Toast.makeText(getApplication(), "Username and password cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val publicKey = biometricKeyManager.generateKeyPair()
                if (publicKey == null) {
                    Toast.makeText(getApplication(), "Could not generate security key.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val request = RegisterRequest(currentState.username, currentState.password, publicKey)
                val response = apiService.registerUser(request)

                if (response.isSuccessful) {
                    Toast.makeText(getApplication(), "Registration successful! Please log in.", Toast.LENGTH_SHORT).show()
                    // Optionally, you could log them in directly here by getting tokens
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Registration failed"
                    Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
