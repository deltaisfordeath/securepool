package com.example.securepool.ui.poolgame

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.securepool.ui.theme.SecurePoolTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class PoolSimulatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SecurePoolTheme {
                PoolSimulatorScreen()
            }
        }
    }
}

@Composable
fun PoolSimulatorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State for game view and its controls
    val poolGameView = remember { PoolGameView(context, null) }
    var aimAngleDegrees by remember { mutableFloatStateOf(0f) }
    var shotPower by remember { mutableFloatStateOf(0.5f) }
    var gameMessage by remember { mutableStateOf("") }

    // Set up and tear down callbacks and lifecycle observers for the PoolGameView
    DisposableEffect(poolGameView, lifecycleOwner) {
        // Set callbacks to link the View with Compose state
        poolGameView.onAimAngleUpdated = { newAngle -> aimAngleDegrees = newAngle }
        poolGameView.onEightBallPocketed = { gameMessage = "Nice! You pocketed the 8-ball!" }

        // Observe lifecycle events to pause/resume the game thread
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> poolGameView.gameThread?.running = true
                Lifecycle.Event.ON_PAUSE -> poolGameView.gameThread?.running = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            poolGameView.onAimAngleUpdated = null
            poolGameView.onEightBallPocketed = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update the PoolGameView's aim angle whenever the state changes
    LaunchedEffect(aimAngleDegrees) {
        poolGameView.aimAngleDegrees = aimAngleDegrees
    }

    Scaffold(
        topBar = { TopBar(
            onResetClick = {
                poolGameView.resetBalls()
                aimAngleDegrees = 0f
                shotPower = 0.5f
                gameMessage = ""
            },
            onExitClick = { (context as? Activity)?.finish() }
        ) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = gameMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                factory = { poolGameView }
            )

            GameControls(
                shotPower = shotPower,
                onPowerChange = { shotPower = it },
                onAimChange = { aimAngleDegrees = (aimAngleDegrees + it + 360) % 360 },
                onFireClick = {
                    if (!poolGameView.isBallsMoving) {
                        poolGameView.applyShot(aimAngleDegrees, shotPower)
                        gameMessage = ""
                    } else {
                        Toast.makeText(context, "Balls are still moving!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onResetClick: () -> Unit, onExitClick: () -> Unit) {
    TopAppBar(
        title = { Text("Local Pool Simulator") },
        actions = {
            Button(onClick = onResetClick) { Text("Reset") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onExitClick) { Text("Exit") }
            Spacer(Modifier.width(8.dp))
        }
    )
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
        AimControl(-5f, -2f, 35.dp), // Fine & Coarse Left
        AimControl(-1f, -1f, 30.dp), // Normal Left
        AimControl(-0.1f, -0.1f, 25.dp), // Micro Left
        AimControl(0.1f, 0.1f, 25.dp), // Micro Right
        AimControl(1f, 1f, 30.dp), // Normal Right
        AimControl(2f, 5f, 35.dp) // Coarse & Fine Right
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Aiming Buttons
        aimControls.take(3).forEach { control ->
            RepeatableAimButton(control = control, onAim = onAimChange)
            Spacer(Modifier.width(2.dp))
        }

        Spacer(Modifier.width(4.dp))
        FireButton(onClick = onFireClick)
        Spacer(Modifier.width(4.dp))

        // Right Aiming Buttons
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
            .border(2.dp, Color(0xFFF0B2EE), CircleShape), // Pink border
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

/**
 * A custom button that supports a regular click and a continuous action on long press.
 */
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
            delay(300) // Initial delay before repeating
            while (true) { // Will be cancelled when isPressed becomes false
                onLongPress()
                delay(100) // Delay between repeats
            }
        }
    }
}