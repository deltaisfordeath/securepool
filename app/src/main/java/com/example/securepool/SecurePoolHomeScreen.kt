package com.example.securepool
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.example.securepool.ui.model.HomeUiState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.securepool.GameActivity
import com.example.securepool.LeaderboardActivity
import com.example.securepool.ui.poolgame.PoolSimulatorActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurePoolHomeScreen(
    uiState: HomeUiState,
    onRestoreScore: () -> Unit,
    onFindOpponent: () -> String?,
    onRefresh: () -> Unit,
    onRegisterBiometric: () -> Unit,
    removeBiometricLogin: () -> Unit
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SecurePool", style = MaterialTheme.typography.displayMedium)

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        "Current Score: ${uiState.score}",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    PoolSimulatorActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                    {
                        Text("Play Pool!")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    context,
                                    LeaderboardActivity::class.java
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )
                    {
                        Text("Show Ranking")
                    }

                    if (uiState.isBiometricRegistered) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { removeBiometricLogin() },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                        {
                            Text("Remove Biometric Login")
                        }
                    }

                    if (uiState.score == 0) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onRestoreScore() },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                        {
                            Text("Restore Score")
                        }
                    }

                    if (uiState.isBiometricAvailable && !uiState.isBiometricRegistered) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onRegisterBiometric() },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )
                        {
                            Text("Enable Biometric Login")
                        }
                    }
                }
            }
        }
    }
}