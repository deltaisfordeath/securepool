package com.example.securepool.ui.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.securepool.BiometricKeyManager
import com.example.securepool.api.RetrofitClient
import com.example.securepool.model.LeaderboardEntry
import com.example.securepool.api.TokenManager
import com.example.securepool.model.BiometricRegisterRequest
import com.example.securepool.model.RegisterRequest
import com.example.securepool.ui.NavigationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String = "",
    val score: Int = 0,
    val isLoading: Boolean = true,
    val opponent: String? = null,
    val isBiometricAvailable: Boolean = false,
    val isBiometricRegistered: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val apiService = RetrofitClient.create(application)
    private val tokenManager = TokenManager(application)

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage = _userMessage.asSharedFlow()

    init {
        loadData()
    }

    fun setBiometricAvailable(isAvailable: Boolean) {
        _uiState.update { it.copy(isBiometricAvailable = isAvailable) }
    }

    fun setBiometricRegistered(isRegistered: Boolean) {
        _uiState.update { it.copy(isBiometricRegistered = isRegistered) }
    }

    fun loadData() {
        _uiState.update { it.copy(isLoading = true) }

        val username = tokenManager.getUsername()

        // If there's no username, redirect to login screen
        if (username.isNullOrEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            viewModelScope.launch {
                _navigationEvent.emit(NavigationEvent.NavigateToLogin)
            }
            return
        }

        viewModelScope.launch {
            try {
                // Use the retrieved username for the API calls
                val scoreResponse = apiService.getScore(username)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        username = username,
                        score = scoreResponse.body()?.score ?: 0,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _userMessage.emit("Failed to load data: ${e.message}")
            }
        }
    }

    fun restoreScore() {
        viewModelScope.launch {
            try {
                val username = _uiState.value.username
                if (username.isEmpty()) return@launch // Don't run if username is not loaded

                val request = RegisterRequest(username, "placeholder")

                val response = apiService.restoreScore(request)

                if (response.isSuccessful) {
                    _userMessage.emit("Points restored!")
                    loadData() // Reload data to get the new score
                } else {
                    _userMessage.emit("Restore request failed")
                }
            } catch (e: Exception) {
                _userMessage.emit("Network error: ${e.message}")
            }
        }
    }

    fun registerBiometricKey() {
        viewModelScope.launch {
            val biometricKeyManager = BiometricKeyManager(getApplication())
            val publicKey = biometricKeyManager.generateKeyPair()
            if (publicKey == null) {
                _userMessage.emit("An error occurred generating biometric authentication key pair.")
                return@launch
            }
            try {
                val request = BiometricRegisterRequest(publicKey)
                val response = apiService.registerBiometric(request)
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isBiometricRegistered = true) }
                }
            } catch (e: Exception) {
                _userMessage.emit("An error occurred registering biometric key to api.")
                biometricKeyManager.deleteKeyPair()
            }
        }
    }

    // TODO: handle opponent matching server side via websocket
    fun findOpponent(): String? {
        val currentUser = _uiState.value.username
        return "gamerd"
    }
}