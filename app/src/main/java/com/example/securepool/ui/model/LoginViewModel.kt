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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isBiometricAvailable: Boolean = false,
    val isBiometricRegistered: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _registerRateLimitEvent = MutableSharedFlow<Boolean>()
    val registerRateLimitEvent = _registerRateLimitEvent.asSharedFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _showBiometricPromptRequest = MutableSharedFlow<String>()
    val showBiometricPromptRequest = _showBiometricPromptRequest.asSharedFlow()

    // ✅ Added: Emits when HTTP 429 is hit
    private val _rateLimitEvent = MutableSharedFlow<Boolean>()
    val rateLimitEvent = _rateLimitEvent.asSharedFlow()

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

    fun setBiometricRegistered(isRegistered: Boolean) {
        _uiState.update { it.copy(isBiometricRegistered = isRegistered) }
    }

    fun onBiometricLoginClicked() {
        if (_uiState.value.username.isBlank()) {
            Toast.makeText(
                getApplication(),
                "Please enter your username first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val challengeResponse = apiService.getChallenge(_uiState.value.username)
                if (challengeResponse.isSuccessful && challengeResponse.body() != null) {
                    val challenge = challengeResponse.body()!!.challenge
                    _showBiometricPromptRequest.emit(challenge)
                } else {
                    Toast.makeText(
                        getApplication(),
                        "Could not get challenge from server.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loginWithSignedChallenge(signedChallengeBase64: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val request = SignedChallengeRequest(_uiState.value.username, signedChallengeBase64)
                val loginResponse = apiService.postChallenge(request)
                val body = loginResponse.body()
                if (loginResponse.isSuccessful && body != null && body.accessToken != null && body.refreshToken != null) {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUsername(body.username)
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                } else {
                    Toast.makeText(getApplication(), "Biometric login failed.", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loginUser() {
        val currentState = _uiState.value
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            Toast.makeText(
                getApplication(),
                "Username and password cannot be empty.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val request = RegisterRequest(currentState.username, currentState.password)
                val response = apiService.loginUser(request)
                val body = response.body()

                // ✅ Handle HTTP 429 rate limit
                if (response.code() == 429) {
                    _rateLimitEvent.emit(true)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                if (response.isSuccessful && body != null && body.accessToken != null && body.refreshToken != null) {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUsername(body.username)
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                } else {
                    val errorMessage = when {
                        response.code() == 401 -> "Invalid credentials"
                        else -> "An unknown error occurred."
                    }
                    Toast.makeText(getApplication(), errorMessage, Toast.LENGTH_LONG).show()
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Network error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun registerUser() {
        val currentState = _uiState.value
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            Toast.makeText(
                getApplication(),
                "Username and password cannot be empty.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val publicKey = when {
                    _uiState.value.isBiometricRegistered -> biometricKeyManager.generateKeyPair()
                    else -> null
                }

                val request =
                    RegisterRequest(currentState.username, currentState.password, publicKey)
                val response = apiService.registerUser(request)

                // ✅ Detect HTTP 429 Too Many Requests
                if (response.code() == 429) {
                    _registerRateLimitEvent.emit(true)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val body = response.body()

                if (response.isSuccessful && body != null && body.accessToken != null && body.refreshToken != null) {
                    tokenManager.saveTokens(body.accessToken, body.refreshToken)
                    tokenManager.saveUsername(body.username)
                    _navigationEvent.emit(NavigationEvent.NavigateToHome)
                } else {
                    val errorMsg = body?.message ?: "Registration failed"
                    Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Network error: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
