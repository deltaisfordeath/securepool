
package com.example.securepool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.securepool.ui.model.GameUiState
import com.example.securepool.ui.model.GameViewModel
import com.example.securepool.ui.model.GameViewModelFactory
import com.example.securepool.ui.theme.SecurePoolTheme
import com.example.securepool.api.SecureWebSocketClient


class GameActivity : ComponentActivity() {

    private lateinit var socketClient: SecureWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val opponentUsername = intent.getStringExtra("OPPONENT_USERNAME") ?: run {
            finish()
            return
        }


        // Initialize and connect WebSocket
        socketClient = SecureWebSocketClient("your_token_here")
        socketClient.connect()

        // Optional: Send hello message to opponent
        socketClient.send("Hello ${opponentUsername} from ${socketClient.hashCode()}")



        val viewModel: GameViewModel by viewModels {
            GameViewModelFactory(application, opponentUsername)
        }

        setContent {
            SecurePoolTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                GameScreen(
                    uiState = uiState,
                    onMatchResult = { outcome ->
                        viewModel.submitMatchResult(outcome) { finish() }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketClient.disconnect() // ✅ Gracefully close WebSocket
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(uiState: GameUiState, onMatchResult: (String) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Match: ${uiState.playerUsername} vs ${uiState.opponentUsername}") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading || uiState.isSyncing) {
                CircularProgressIndicator()
                if (uiState.isSyncing) Text("Saving result...")
            } else {
                Text("${uiState.playerUsername} Score: ${uiState.playerScore}")
                Text("${uiState.opponentUsername} Score: ${uiState.opponentScore}")
                Button(onClick = { onMatchResult("win") }) { Text("I Won") }
                Button(onClick = { onMatchResult("lose") }) { Text("I Lost") }
                Button(onClick = { onMatchResult("exit") }) { Text("Exit Game") }
            }
        }
    }
}