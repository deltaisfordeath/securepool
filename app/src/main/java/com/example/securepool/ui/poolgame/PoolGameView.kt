package com.example.securepool.ui.poolgame

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.localpoolsimulator.Ball
import com.example.securepool.api.SecureWebSocketClient
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.*

/**
 * Custom SurfaceView for rendering the pool game.
 * This version is refactored to use a virtual coordinate system, allowing it
 * to scale correctly on any device size while communicating with a server.
 */
class PoolGameView(context: Context, attrs: AttributeSet?) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    // --- Core Game and Rendering ---
    var gameThread: GameThread? = null
    private val tablePaint = Paint().apply { color = Color.parseColor("#228B22") } // Forest Green
    private val cushionPaint = Paint().apply { color = Color.parseColor("#8B4513") } // Saddle Brown
    private val pocketPaint = Paint().apply { color = Color.BLACK }
    private val winTextPaint = Paint().apply {
        color = Color.YELLOW
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(10f, 5f, 5f, Color.BLACK)
    }

    // --- Virtual World Constants (Must match server) ---
    private val VIRTUAL_WIDTH = 2000f
    private val VIRTUAL_HEIGHT = 1000f
    private val VIRTUAL_CUSHION_THICKNESS = 40f
    private val VIRTUAL_BALL_RADIUS = 22.5f
    private val VIRTUAL_POCKET_RADIUS = 45f

    // --- Screen Scaling Properties ---
    private var scaleFactor = 1.0f
    private var offsetX = 0f
    private var offsetY = 0f

    // --- Game Elements ---
    private val balls = mutableListOf<Ball>()
    private val pockets = mutableListOf<Pocket>()

    // --- Game State ---
    private var gameWon = false
    var isBallsMoving: Boolean = false
    private var awaitingServerResponse = false
    var onEightBallPocketed: (() -> Unit)? = null
    var onAimAngleUpdated: ((Float) -> Unit)? = null

    // --- Aiming ---
    var aimAngleDegrees: Float = 0f
        set(value) {
            field = value
            if (!isBallsMoving) postInvalidate()
        }
    var shotPower: Float = 0.5f

    // --- Networking ---
    private var webSocketClient: SecureWebSocketClient? = null
    private val gameId = "placeholder-game-id" // Example game ID
    @Volatile private var pendingServerState: JSONObject? = null

    init {
        holder.addCallback(this)
        setupSocket() // Connect to the server when the view is initialized
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setupPockets()
        resetBalls()

        gameThread = GameThread(holder, this)
        gameThread?.running = true
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        val scaleX = screenWidth / VIRTUAL_WIDTH
        val scaleY = screenHeight / VIRTUAL_HEIGHT
        scaleFactor = min(scaleX, scaleY)

        val renderedWidth = VIRTUAL_WIDTH * scaleFactor
        val renderedHeight = VIRTUAL_HEIGHT * scaleFactor

        offsetX = (screenWidth - renderedWidth) / 2f
        offsetY = (screenHeight - renderedHeight) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread?.running = false
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                e.printStackTrace()
            }
        }
        webSocketClient?.disconnect() // Disconnect socket when view is destroyed
    }

    /**
     * Sets up socket connection and listeners.
     */
    private fun setupSocket() {
        webSocketClient = SecureWebSocketClient(context)

        webSocketClient?.onGameStateUpdate = { serverState ->
            pendingServerState = serverState
            awaitingServerResponse = false
        }

        webSocketClient?.onConnected = {
            Log.d("PoolGameView", "Socket connected successfully!")
        }

        webSocketClient?.onError = { e ->
            Log.e("PoolGameView", "Socket Error", e)
        }

        webSocketClient?.connect()
    }

    /**
     * Applies the authoritative state received from the server.
     */
    private fun applyStateFromServer(data: JSONObject) {
        if (!data.has("balls")) return

        val ballsData = data.getJSONArray("balls")
        ballsData.let {
            for (i in 0 until it.length()) {
                val ballJson = it.getJSONObject(i)
                val id = ballJson.getString("id")
                val x = ballJson.getDouble("x").toFloat()
                val y = ballJson.getDouble("y").toFloat()
                val isPocketed = ballJson.getBoolean("isPocketed")

                val ball = balls.find { b -> b.id == id }
                ball?.let {
                    it.x = x
                    it.y = y
                    if (isPocketed) {
                        it.x = -1000f
                    }
                }
            }
        }

        // After positions are updated, calculate the angle to the 8-ball
        val cueBall = balls.find { it.id == "cue" }
        val eightBall = balls.find { it.id == "8-ball" }

        // Only update angle if both balls are on the table
        if (cueBall != null && eightBall != null && cueBall.x > 0 && eightBall.x > 0) {
            val dx = eightBall.x - cueBall.x
            val dy = eightBall.y - cueBall.y
            val newAngleRad = atan2(dy, dx)
            val newAngle = Math.toDegrees(newAngleRad.toDouble()).toFloat()
            // Invoke the callback to update the composable's state
            onAimAngleUpdated?.invoke(newAngle)
        }

        if (data.has("gameState") && data.getString("gameState") == "GameOver") {
            if (data.has("reason") && data.getString("reason") == "8BallPocketed") {
                gameWon = true
                onEightBallPocketed?.invoke()
            }
        }
    }

    /**
     * Sets up pocket positions in the VIRTUAL coordinate system.
     */
    private fun setupPockets() {
        pockets.clear()
        val cornerPocketRadius = VIRTUAL_POCKET_RADIUS * 1.1f
        // Top Row
        pockets.add(Pocket(VIRTUAL_CUSHION_THICKNESS, VIRTUAL_CUSHION_THICKNESS, cornerPocketRadius))
        pockets.add(Pocket(VIRTUAL_WIDTH / 2, VIRTUAL_CUSHION_THICKNESS / 2, VIRTUAL_POCKET_RADIUS))
        pockets.add(Pocket(VIRTUAL_WIDTH - VIRTUAL_CUSHION_THICKNESS, VIRTUAL_CUSHION_THICKNESS, cornerPocketRadius))
        // Bottom Row
        pockets.add(Pocket(VIRTUAL_CUSHION_THICKNESS, VIRTUAL_HEIGHT - VIRTUAL_CUSHION_THICKNESS, cornerPocketRadius))
        pockets.add(Pocket(VIRTUAL_WIDTH / 2, VIRTUAL_HEIGHT - VIRTUAL_CUSHION_THICKNESS / 2, VIRTUAL_POCKET_RADIUS))
        pockets.add(Pocket(VIRTUAL_WIDTH - VIRTUAL_CUSHION_THICKNESS, VIRTUAL_HEIGHT - VIRTUAL_CUSHION_THICKNESS, cornerPocketRadius))
    }

    /**
     * Resets ball positions to their starting state.
     */
    fun resetBalls() {
        balls.clear()
        val startX = VIRTUAL_WIDTH / 4f
        val midY = VIRTUAL_HEIGHT / 2f

        balls.add(Ball("cue", startX, midY, VIRTUAL_BALL_RADIUS, Color.WHITE))
        balls.add(Ball("8-ball", VIRTUAL_WIDTH * 0.75f, midY, VIRTUAL_BALL_RADIUS, Color.BLACK))

        isBallsMoving = false
        gameWon = false
        awaitingServerResponse = false

        balls.forEach {
            it.velocityX = 0f
            it.velocityY = 0f
        }
    }

    /**
     * Runs the game logic with a fixed time step to match the server.
     */
    fun update() {
        if (isBallsMoving) {
            var stillMovingCount = 0
            for (ball in balls) {
                ball.update() // Call update without deltaTime
                if (ball.isMoving()) {
                    stillMovingCount++
                }
            }

            for (i in balls.indices) {
                GamePhysics.handleWallCollision(balls[i])
                for (j in i + 1 until balls.size) {
                    GamePhysics.handleBallCollision(balls[i], balls[j])
                }
                if (balls[i].id != "cue") {
                    val isGameWon = GamePhysics.isBallInAnyPocket(balls[i], pockets)
                    if (isGameWon) {
                        balls[i].velocityX = 0f
                        balls[i].velocityY = 0f
                    }
                }
            }

            if (stillMovingCount == 0) {
                isBallsMoving = false
            }
        }

        if (!isBallsMoving && pendingServerState != null) {
            val stateToApply = pendingServerState!!
            pendingServerState = null

            (context as? Activity)?.runOnUiThread {
                applyStateFromServer(stateToApply)
                invalidate()
            }
        }
    }

    /**
     * Draws all game elements.
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.DKGRAY)

        val tableRect = RectF(offsetX, offsetY, offsetX + VIRTUAL_WIDTH * scaleFactor, offsetY + VIRTUAL_HEIGHT * scaleFactor)
        canvas.drawRect(tableRect, cushionPaint)

        val playingSurfaceRect = RectF(
            offsetX + VIRTUAL_CUSHION_THICKNESS * scaleFactor,
            offsetY + VIRTUAL_CUSHION_THICKNESS * scaleFactor,
            offsetX + (VIRTUAL_WIDTH - VIRTUAL_CUSHION_THICKNESS) * scaleFactor,
            offsetY + (VIRTUAL_HEIGHT - VIRTUAL_CUSHION_THICKNESS) * scaleFactor
        )
        canvas.drawRect(playingSurfaceRect, tablePaint)

        pockets.forEach { pocket ->
            val screenX = offsetX + pocket.x * scaleFactor
            val screenY = offsetY + pocket.y * scaleFactor
            val screenRadius = pocket.radius * scaleFactor
            canvas.drawCircle(screenX, screenY, screenRadius, pocketPaint)
        }

        balls.forEach { drawBall(canvas, it) }

        if (!isBallsMoving && !awaitingServerResponse) {
            drawAimingLine(canvas)
        }

        if (gameWon) {
            winTextPaint.textSize = 150f * scaleFactor
            val x = width / 2f
            val y = height / 2f
            canvas.drawText("You Win!", x, y, winTextPaint)
        }
    }

    private fun drawBall(canvas: Canvas, ball: Ball) {
        val screenX = offsetX + ball.x * scaleFactor
        val screenY = offsetY + ball.y * scaleFactor
        val screenRadius = ball.radius * scaleFactor
        ball.paint.color = ball.color
        canvas.drawCircle(screenX, screenY, screenRadius, ball.paint)
    }

    private fun drawAimingLine(canvas: Canvas) {
        val cueBall = balls.find { it.id == "cue" } ?: return

        val linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f * scaleFactor
        }

        val angleRad = Math.toRadians(aimAngleDegrees.toDouble()).toFloat()
        val lineLength = 700f

        val startX = offsetX + cueBall.x * scaleFactor
        val startY = offsetY + cueBall.y * scaleFactor
        val endX = startX + lineLength * cos(angleRad) * scaleFactor
        val endY = startY + lineLength * sin(angleRad) * scaleFactor
        canvas.drawLine(startX, startY, endX, endY, linePaint)

        val arrowLength = 40f * scaleFactor
        val arrowAngle = 25f
        val angle1 = angleRad - PI + Math.toRadians(arrowAngle.toDouble())
        val arrowX1 = endX + arrowLength * cos(angle1).toFloat()
        val arrowY1 = endY + arrowLength * sin(angle1).toFloat()
        canvas.drawLine(endX, endY, arrowX1, arrowY1, linePaint)
        val angle2 = angleRad + PI - Math.toRadians(arrowAngle.toDouble())
        val arrowX2 = endX + arrowLength * cos(angle2).toFloat()
        val arrowY2 = endY + arrowLength * sin(angle2).toFloat()
        canvas.drawLine(endX, endY, arrowX2, arrowY2, linePaint)
    }

    /**
     * Starts local simulation and sends shot data to the server.
     */
    fun applyShot(angleDegrees: Float, power: Float) {
        if (isBallsMoving || awaitingServerResponse) return

        val cueBall = balls.find { it.id == "cue" } ?: return

        val maxSpeed = 3000f
        val speed = maxSpeed * power
        val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
        cueBall.velocityX = speed * cos(angleRad)
        cueBall.velocityY = speed * sin(angleRad)
        isBallsMoving = true

        awaitingServerResponse = true
        webSocketClient?.takeShot(gameId, angleDegrees, power)
    }

    /**
     * A fixed-step game loop to ensure client prediction matches server simulation.
     */
    inner class GameThread(private val surfaceHolder: SurfaceHolder, private val gameView: PoolGameView) : Thread() {
        var running: Boolean = false
        private val timeStep = 1000.0 / 60.0 // Time per frame in ms for 60Hz physics
        private var lastTime: Long = 0
        private var accumulator = 0.0

        override fun run() {
            lastTime = System.currentTimeMillis()

            while (running) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastTime).toDouble()
                lastTime = currentTime
                accumulator += deltaTime

                // Perform fixed updates for physics
                while (accumulator >= timeStep) {
                    gameView.update() // Update physics with a fixed step
                    accumulator -= timeStep
                }

                // Render the result
                var canvas: Canvas? = null
                try {
                    canvas = surfaceHolder.lockCanvas()
                    synchronized(surfaceHolder) {
                        canvas?.let { gameView.draw(it) }
                    }
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                }
            }
        }
    }
}