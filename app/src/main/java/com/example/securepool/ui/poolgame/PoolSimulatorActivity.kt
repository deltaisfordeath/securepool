package com.example.securepool.ui.poolgame

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.securepool.api.SecureWebSocketClient
import com.example.securepool.ui.theme.SecurePoolTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Represents the different screens in the app
private sealed class Screen {
    object MainMenu : Screen()
    object InGame : Screen()
}

class PoolSimulatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecurePoolTheme {
                PoolApp()
            }
        }
    }
}

@Composable
fun PoolApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- State Management ---
    // Single instances of the client and view to persist across the app
    val webSocketClient = remember { SecureWebSocketClient(context) }
    val poolGameView = remember { PoolGameView(context, null) }

    // Navigation state
    var currentScreen by remember { mutableStateOf<Screen>(Screen.MainMenu) }

    // UI and Game state
    var statusMessage by remember { mutableStateOf("Connecting to server...") }
    var gameId by remember { mutableStateOf<String?>(null) }
    var currentPlayerId by remember { mutableStateOf<String?>(null) }
    var myPlayerId by remember { mutableStateOf<String?>(null) }

    // State for game controls
    var aimAngleDegrees by remember { mutableFloatStateOf(0f) }
    var shotPower by remember { mutableFloatStateOf(0.5f) }

    DisposableEffect(webSocketClient) {
        webSocketClient.onConnected = {
            myPlayerId = webSocketClient.socketId
            statusMessage = "Connected. Choose a mode."
        }
        webSocketClient.onDisconnected = { statusMessage = "Disconnected. Please restart." }
        webSocketClient.onWaitingForOpponent = { statusMessage = "Waiting for an opponent..." }
        webSocketClient.onOpponentDisconnected = {
            scope.launch { snackbarHostState.showSnackbar("Opponent disconnected. You win!") }
            currentScreen = Screen.MainMenu
            statusMessage = "Ready to play again."
        }
        webSocketClient.onMatchFound = { receivedGameId, initialState ->
            gameId = receivedGameId
            currentPlayerId = initialState.optString("currentPlayer", null)
            poolGameView.applyStateFromServer(initialState)
            statusMessage = ""
            currentScreen = Screen.InGame
        }
        webSocketClient.onOpponentShot = { angle, power ->
            // When the opponent takes a shot, start the local animation on this client.
            poolGameView.applyShot(angle, power)
        }
        webSocketClient.onGameStateUpdate = { newState ->
            currentPlayerId = newState.optString("currentPlayer", currentPlayerId)
            poolGameView.setPendingServerState(newState, myPlayerId)
        }

        webSocketClient.connect()

        onDispose {
            webSocketClient.disconnect()
        }
    }

    // Effect for handling PoolGameView-specific events
    DisposableEffect(poolGameView) {
        poolGameView.onAimAngleUpdated = { newAngle ->
            aimAngleDegrees = newAngle
        }
        onDispose {
            poolGameView.onAimAngleUpdated = null
        }
    }

    LaunchedEffect(aimAngleDegrees) {
        poolGameView.aimAngleDegrees = aimAngleDegrees
    }

    // --- UI Navigation ---
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                is Screen.MainMenu -> MainMenuScreen(
                    statusMessage = statusMessage,
                    onPracticeClick = { webSocketClient.joinPracticeGame() },
                    onMatchClick = { webSocketClient.findMatch() }
                )
                is Screen.InGame -> GameScreen(
                    poolGameView = poolGameView,
                    shotPower = shotPower,
                    onPowerChange = { shotPower = it },
                    aimAngleDegrees = aimAngleDegrees,
                    onAimChange = { aimAngleDegrees = (aimAngleDegrees + it + 360) % 360 },
                    onFireClick = {
                        val isMyTurn = (currentPlayerId == myPlayerId) || gameId?.startsWith("practice_") == true
                        if (poolGameView.isBallsMoving) {
                            Toast.makeText(context, "Balls are still moving!", Toast.LENGTH_SHORT).show()
                        } else if (!isMyTurn) {
                            Toast.makeText(context, "It's not your turn!", Toast.LENGTH_SHORT).show()
                        } else {
                            // 1. Start the local animation for immediate feedback
                            poolGameView.applyShot(aimAngleDegrees, shotPower)
                            // 2. Send the authoritative shot to the server
                            webSocketClient.takeShot(aimAngleDegrees, shotPower)
                        }
                    },
                    onExitClick = {
                        webSocketClient.disconnect()
                        (context as? Activity)?.finish()
                    },
                    turnStatus = when {
                        currentPlayerId == null -> "Loading..."
                        gameId?.startsWith("practice_") == true -> "Practice Mode"
                        currentPlayerId == myPlayerId -> "Your Turn"
                        else -> "Opponent's Turn"
                    }
                )
            }
        }
    }
}

@Composable
fun MainMenuScreen(
    statusMessage: String,
    onPracticeClick: () -> Unit,
    onMatchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SecurePool", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))
        AnimatedVisibility(visible = statusMessage.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusMessage.contains("Waiting") || statusMessage.contains("Connecting")) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPracticeClick,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) {
            Text("Practice Mode")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onMatchClick,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp)
        ) {
            Text("Find a Match")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    poolGameView: PoolGameView,
    shotPower: Float,
    onPowerChange: (Float) -> Unit,
    aimAngleDegrees: Float,
    onAimChange: (Float) -> Unit,
    onFireClick: () -> Unit,
    onExitClick: () -> Unit,
    turnStatus: String
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text(
                        text = turnStatus,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    Button(onClick = onExitClick) {
                        Text("Exit Game")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { innerPadding ->

        // This Column holds the main content of your screen.
        Column(
            // This modifier applies the padding from the Scaffold.
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(4.dp),
                factory = { poolGameView }
            )

            GameControls(
                shotPower = shotPower,
                onPowerChange = onPowerChange,
                onAimChange = onAimChange,
                onFireClick = onFireClick
            )
        }
    }
}


@Composable
private fun GameControls(
    shotPower: Float,
    onPowerChange: (Float) -> Unit,
    onAimChange: (Float) -> Unit,
    onFireClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.4f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = shotPower,
                onValueChange = onPowerChange,
                valueRange = 0.0f..1.0f
            )
            Text(
                "Power: ${(shotPower * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }

        Spacer(Modifier.width(8.dp))

        AimAndFireControls(
            modifier = Modifier.weight(0.6f),
            onAimChange = onAimChange,
            onFireClick = onFireClick
        )
    }
}

@Composable
private fun AimAndFireControls(
    modifier: Modifier = Modifier,
    onAimChange: (Float) -> Unit,
    onFireClick: () -> Unit
) {
    val aimControls = listOf(
        AimControl(-5f, -2f, 35.dp),
        AimControl(-1f, -1f, 30.dp),
        AimControl(-0.1f, -0.1f, 25.dp),
        AimControl(0.1f, 0.1f, 25.dp),
        AimControl(1f, 1f, 30.dp),
        AimControl(2f, 5f, 35.dp)
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        aimControls.take(3).forEach { control ->
            RepeatableAimButton(control = control, onAim = onAimChange)
            Spacer(Modifier.width(2.dp))
        }

        Spacer(Modifier.width(4.dp))
        FireButton(onClick = onFireClick)
        Spacer(Modifier.width(4.dp))

        aimControls.drop(3).forEach { control ->
            RepeatableAimButton(control = control, onAim = onAimChange)
            Spacer(Modifier.width(2.dp))
        }
    }
}

private data class AimControl(val increment: Float, val longIncrement: Float, val size: Dp)

@Composable
private fun RepeatableAimButton(control: AimControl, onAim: (Float) -> Unit) {
    RepeatableButton(
        onClick = { onAim(control.increment) },
        onLongPress = { onAim(control.longIncrement) },
        modifier = Modifier.size(control.size)
    ) {}
}

@Composable
private fun FireButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .border(2.dp, Color(0xFFF0B2EE), CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color.Red, CircleShape)
        )
    }
}

@Composable
private fun RepeatableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Button(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        content = content
    )

    if (isPressed && onLongPress != null) {
        LaunchedEffect(isPressed) {
            delay(300)
            while (true) {
                onLongPress()
                delay(100)
            }
        }
    }
}