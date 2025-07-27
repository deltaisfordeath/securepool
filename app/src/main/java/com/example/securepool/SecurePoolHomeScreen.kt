import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.example.securepool.ui.model.HomeUiState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.securepool.BiometricKeyManager
import com.example.securepool.GameActivity
import com.example.securepool.LeaderboardActivity
import com.example.securepool.PracticeActivity
import com.example.securepool.ui.model.HomeViewModel
import com.example.securepool.ui.poolgame.PoolSimulatorActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurePoolHomeScreen(
    uiState: HomeUiState,
    onRestoreScore: () -> Unit,
    onFindOpponent: () -> String?,
    onRefresh: () -> Unit,
    onRegisterBiometric: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh data when the app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome ${uiState.username}") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Current Score: ${uiState.score}", style = MaterialTheme.typography.headlineSmall)

                // The "Start Match" button now has two functions based on score
                Button(onClick = {
                    if (uiState.score == 0) {
                        // Logic for cooldown can be added in the ViewModel if needed
                        onRestoreScore()
                    } else {
                        val opponent = onFindOpponent() // Get the opponent's username
                        if (opponent != null) {
                            val intent = Intent(context, GameActivity::class.java).apply {
                                putExtra("OPPONENT_USERNAME", opponent)
                            }
                            context.startActivity(intent)
                        } else {
                            // Handle case where no opponent is found
                        }
                    }
                }) {
                    Text(if (uiState.score == 0) "Restore Score" else "Start Match")
                }

                Button(onClick = { context.startActivity(Intent(context, PoolSimulatorActivity::class.java)) }) {
                    Text("Practice Room")
                }

                Button(onClick = { context.startActivity(Intent(context, LeaderboardActivity::class.java)) }) {
                    Text("Show Ranking")
                }
                if (uiState.isBiometricAvailable && !uiState.isBiometricRegistered) {
                    Button(onClick = {
                        onRegisterBiometric()
                    }) {
                        Text("Enable Biometric Login")
                    }
                }
            }
        }
    }
}