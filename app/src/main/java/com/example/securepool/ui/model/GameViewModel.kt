package com.example.securepool.ui.model

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.*
import com.example.securepool.api.RetrofitClient
import com.example.securepool.api.SecureWebSocketClient
import com.example.securepool.api.TokenManager
import com.example.securepool.model.MatchResultRequest
import com.example.securepool.ui.NavigationEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val playerUsername: String = "Player",
    val opponentUsername: String = "Opponent",
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false
)

class GameViewModel(
    application: Application,
    val opponentUsername: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState(opponentUsername = opponentUsername))
    val uiState = _uiState.asStateFlow()

    private val apiService = RetrofitClient.create(application)
    private val tokenManager = TokenManager(application)

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val socketClient: SecureWebSocketClient

    init {
        loadInitialScores()
        // Initialize and connect WebSocket
        socketClient = SecureWebSocketClient(getApplication())
        socketClient.connect()

        // Optional: Send hello message to opponent
        socketClient.send("Hello $opponentUsername from ${socketClient.hashCode()}")
    }

    fun sendMessage() {
        Toast.makeText(getApplication(), "Sending message over websocket...", Toast.LENGTH_SHORT).show()

        socketClient.send("This is a message from ${uiState.value.playerUsername}")
    }

    private fun loadInitialScores() {
        viewModelScope.launch {
            val playerUsername = tokenManager.getUsername()

            if (playerUsername.isNullOrEmpty()) {
                _uiState.update { it.copy(isLoading = false) }
                viewModelScope.launch {
                    _navigationEvent.emit(NavigationEvent.NavigateToLogin)
                }
                return@launch
            }

            try {
                val playerScoreRes = apiService.getScore(playerUsername)
                val opponentScoreRes = apiService.getScore(opponentUsername)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playerUsername = playerUsername,
                        playerScore = playerScoreRes.body()?.score ?: 100,
                        opponentScore = opponentScoreRes.body()?.score ?: 100
                    )
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun submitMatchResult(outcome: String, onGameEnd: () -> Unit) {
        _uiState.update { it.copy(isSyncing = true) }
        val currentState = _uiState.value
        val resultRequest = when (outcome) {
            "win" -> MatchResultRequest(currentState.playerUsername, currentState.opponentUsername, "win")
            else -> MatchResultRequest(currentState.opponentUsername, currentState.playerUsername, outcome)
        }

        viewModelScope.launch {
            try {
                apiService.sendMatchResult(resultRequest)
            } catch (e: Exception) {
                // Handle network error
            } finally {
                onGameEnd()
                socketClient.disconnect()
            }
        }
    }
}

class GameViewModelFactory(
    private val application: Application,
    private val opponentUsername: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(application, opponentUsername) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}