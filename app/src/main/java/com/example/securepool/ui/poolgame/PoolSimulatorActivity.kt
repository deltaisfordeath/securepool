package com.example.securepool.ui.poolgame

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
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolSimulatorScreen() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // State for game view controls
    val poolGameView = remember { PoolGameView(context, null) }
    var aimAngleDegrees by remember { mutableFloatStateOf(0f) }
    var shotPower by remember { mutableFloatStateOf(0.5f) }

    // Message display for pocketed ball
    var gameMessage by remember { mutableStateOf("") }

    // Set up callback for PoolGameView when 8-ball is pocketed
    DisposableEffect(poolGameView) {
        poolGameView.onEightBallPocketed = {
            gameMessage = "Nice! You pocketed the 8-ball!"
        }
        onDispose {
            poolGameView.onEightBallPocketed = null
        }
    }

    // Lifecycle observer to ensure game thread stops/starts correctly
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (poolGameView.gameThread?.running == false) {
                        poolGameView.gameThread = poolGameView.GameThread(poolGameView.holder, poolGameView)
                        poolGameView.gameThread?.running = true
                        poolGameView.gameThread?.start()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    poolGameView.gameThread?.running = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Pass the aimAngleDegrees state to PoolGameView and trigger redraw
    DisposableEffect(poolGameView, aimAngleDegrees) {
        poolGameView.aimAngleDegrees = aimAngleDegrees
        onDispose { /* No specific cleanup needed here */ }
    }

    DisposableEffect(poolGameView) {
        // Set the new callback to update the aim angle state
        poolGameView.onAimAngleUpdated = { newAngle ->
            aimAngleDegrees = newAngle
        }
        // Set the existing callback for the win condition
        poolGameView.onEightBallPocketed = {
            gameMessage = "Nice! You pocketed the 8-ball!"
        }

        onDispose {
            poolGameView.onAimAngleUpdated = null
            poolGameView.onEightBallPocketed = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Pool Simulator") },
                actions = {
                    // Reset Button (moved back to top right, increased spacing and size/font)
                    Button(
                        onClick = {
                            poolGameView.resetBalls()
                            aimAngleDegrees = 0f
                            shotPower = 0.5f
                            gameMessage = ""
                            if (poolGameView.gameThread?.running == false) {
                                poolGameView.gameThread = poolGameView.GameThread(poolGameView.holder, poolGameView)
                                poolGameView.gameThread?.running = true
                                poolGameView.gameThread?.start()
                            }
                        },
                        modifier = Modifier
                            .width(100.dp) // Increased width for "Reset" text
                            .height(40.dp)
                            .padding(end = 16.dp) // Increased spacing from Exit button
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall) // Decreased font size slightly
                    }

                    // Exit Button (remains at top right)
                    Button(
                        onClick = { (context as? ComponentActivity)?.finish() },
                        modifier = Modifier
                            .width(80.dp)
                            .height(40.dp)
                    ) {
                        Text("Exit", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Game Message Display
                Text(
                    text = gameMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Pool Game View - Maximized
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    factory = { poolGameView }
                )

                // Controls Area - Compacted and Side-by-Side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shot Power Bar and "Power" text
                    Column(
                        modifier = Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Slider(
                            value = shotPower,
                            onValueChange = { newValue -> shotPower = newValue },
                            valueRange = 0f..1f,
                            steps = 19,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(20.dp)
                        )
                        Text(
                            "Power: ${(shotPower * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Aiming Controls & Fire Button - Closer to center, on Right
                    Row(
                        modifier = Modifier.weight(0.6f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Rotation Buttons (No text, size-based differentiation)
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees - 2 + 360) % 360 },
                            onLongPress = { aimAngleDegrees = (aimAngleDegrees - 5 + 360) % 360 },
                            modifier = Modifier.size(35.dp) // New size for <<
                        ) { /* No text */ }
                        Spacer(Modifier.width(2.dp))
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees - 1 + 360) % 360 },
                            modifier = Modifier.size(30.dp) // New size for <
                        ) { /* No text */ }
                        Spacer(Modifier.width(2.dp))
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees - 0.1f + 360) % 360 },
                            modifier = Modifier.size(25.dp) // New size for .
                        ) { /* No text */ }
                        Spacer(Modifier.width(4.dp))

                        // Fire Button (Cue) - White circle with red dot, pink border, no text
                        Button(
                            onClick = {
                                if (!poolGameView.isBallsMoving) {
                                    poolGameView.applyShot(aimAngleDegrees, shotPower)
                                    gameMessage = ""
                                    if (poolGameView.gameThread?.running == false) {
                                        poolGameView.gameThread = poolGameView.GameThread(poolGameView.holder, poolGameView)
                                        poolGameView.gameThread?.running = true
                                        poolGameView.gameThread?.start()
                                    }
                                } else {
                                    Toast.makeText(context, "Balls are still moving!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White, CircleShape)
                                .border(2.dp, Color(0xFFF0B2EE), CircleShape), // Pink border
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, CircleShape)
                            )
                        }
                        Spacer(Modifier.width(4.dp))

                        // Right Rotation Buttons (No text, size-based differentiation)
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees + 0.1f) % 360 },
                            modifier = Modifier.size(25.dp) // New size for .
                        ) { /* No text */ }
                        Spacer(Modifier.width(2.dp))
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees + 1) % 360 },
                            modifier = Modifier.size(30.dp) // New size for >
                        ) { /* No text */ }
                        Spacer(Modifier.width(2.dp))
                        RepeatableButton(
                            onClick = { aimAngleDegrees = (aimAngleDegrees + 2 + 360) % 360 },
                            onLongPress = { aimAngleDegrees = (aimAngleDegrees + 5 + 360) % 360 },
                            modifier = Modifier.size(35.dp) // New size for >>
                        ) { /* No text */ }
                    }
                }
            }
        }
    )
}

/**
 * A custom Composable button that supports both a regular click and a continuous action on long press.
 */
@Composable
fun RepeatableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value

    Button(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        content = content
    )

    if (isPressed && onLongPress != null) {
        LaunchedEffect(Unit) {
            delay(300)
            while (isPressed) {
                onLongPress()
                delay(100)
            }
        }
    }
}
