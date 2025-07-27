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
import org.json.JSONObject
import kotlin.math.*

/**
 * Custom SurfaceView for rendering the pool game.
 * This version uses a centralized GameConstants object for all dimensions and physics.
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
        setupSocket()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setupPockets()
        resetBalls()

        gameThread = GameThread(holder, this).apply {
            running = true
            start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()

        val scaleX = screenWidth / GameConstants.VIRTUAL_WIDTH
        val scaleY = screenHeight / GameConstants.VIRTUAL_HEIGHT
        scaleFactor = min(scaleX, scaleY)

        val renderedWidth = GameConstants.VIRTUAL_WIDTH * scaleFactor
        val renderedHeight = GameConstants.VIRTUAL_HEIGHT * scaleFactor

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
                Log.e("PoolGameView", "Failed to stop game thread.", e)
            }
        }
        webSocketClient?.disconnect()
    }

    private fun setupSocket() {
        webSocketClient = SecureWebSocketClient(context).apply {
            onGameStateUpdate = { serverState ->
                pendingServerState = serverState
                awaitingServerResponse = false
            }
            onConnected = { Log.d("PoolGameView", "Socket connected successfully!") }
            onError = { e -> Log.e("PoolGameView", "Socket Error", e) }
            connect()
        }
    }

    private fun applyStateFromServer(data: JSONObject) {
        val ballsData = data.optJSONArray("balls") ?: return

        for (i in 0 until ballsData.length()) {
            val ballJson = ballsData.getJSONObject(i)
            val id = ballJson.getString("id")
            val x = ballJson.getDouble("x").toFloat()
            val y = ballJson.getDouble("y").toFloat()
            val isPocketed = ballJson.getBoolean("isPocketed")

            balls.find { it.id == id }?.let { ball ->
                ball.x = x
                ball.y = y
                if (isPocketed) ball.x = -1000f // Move pocketed balls off-screen
            }
        }

        updateAimAngleFromBallPositions()

        if (data.optString("gameState") == "GameOver" && data.optString("reason") == "8BallPocketed") {
            gameWon = true
            onEightBallPocketed?.invoke()
        }
    }

    private fun updateAimAngleFromBallPositions() {
        val cueBall = balls.find { it.id == "cue" }
        val eightBall = balls.find { it.id == "8-ball" }

        if (cueBall != null && eightBall != null && cueBall.x > 0 && eightBall.x > 0) {
            val dx = eightBall.x - cueBall.x
            val dy = eightBall.y - cueBall.y
            val newAngle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            onAimAngleUpdated?.invoke(newAngle)
        }
    }

    private fun setupPockets() {
        pockets.clear()
        val vw = GameConstants.VIRTUAL_WIDTH
        val vh = GameConstants.VIRTUAL_HEIGHT
        val ct = GameConstants.VIRTUAL_CUSHION_THICKNESS
        val pr = GameConstants.VIRTUAL_POCKET_RADIUS
        val cornerPocketRadius = pr * 1.1f // Slightly larger corner pockets for easier shots

        pockets.addAll(listOf(
            // Top Row
            Pocket(ct, ct, cornerPocketRadius),
            Pocket(vw / 2, ct / 2, pr),
            Pocket(vw - ct, ct, cornerPocketRadius),
            // Bottom Row
            Pocket(ct, vh - ct, cornerPocketRadius),
            Pocket(vw / 2, vh - ct / 2, pr),
            Pocket(vw - ct, vh - ct, cornerPocketRadius)
        ))
    }

    fun resetBalls() {
        balls.clear()
        val startX = GameConstants.VIRTUAL_WIDTH / 4f
        val midY = GameConstants.VIRTUAL_HEIGHT / 2f

        balls.add(Ball("cue", startX, midY, GameConstants.VIRTUAL_BALL_RADIUS, GameConstants.CUE_BALL_COLOR))
        balls.add(Ball("8-ball", GameConstants.VIRTUAL_WIDTH * 0.75f, midY, GameConstants.VIRTUAL_BALL_RADIUS, GameConstants.EIGHT_BALL_COLOR))

        isBallsMoving = false
        gameWon = false
        awaitingServerResponse = false

        balls.forEach {
            it.velocityX = 0f
            it.velocityY = 0f
        }
    }

    fun update() {
        if (isBallsMoving) {
            var stillMovingCount = 0
            balls.forEach { ball ->
                ball.update()
                if (ball.isMoving()) stillMovingCount++
            }

            for (i in balls.indices) {
                GamePhysics.handleWallCollision(balls[i])
                for (j in i + 1 until balls.size) {
                    GamePhysics.handleBallCollision(balls[i], balls[j])
                }
                // Check for non-cue ball pocketing to stop it locally
                if (balls[i].id != "cue" && GamePhysics.isBallInAnyPocket(balls[i], pockets)) {
                    balls[i].velocityX = 0f
                    balls[i].velocityY = 0f
                }
            }

            if (stillMovingCount == 0) {
                isBallsMoving = false
            }
        }

        // Apply authoritative server state only when client simulation is idle
        pendingServerState?.let { state ->
            if (!isBallsMoving) {
                pendingServerState = null
                (context as? Activity)?.runOnUiThread {
                    applyStateFromServer(state)
                    invalidate()
                }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawColor(Color.DKGRAY)

        drawTable(canvas)
        pockets.forEach { drawPocket(canvas, it) }
        balls.forEach { drawBall(canvas, it) }

        if (!isBallsMoving && !awaitingServerResponse) {
            drawAimingLine(canvas)
        }
        if (gameWon) {
            drawWinText(canvas)
        }
    }

    private fun drawTable(canvas: Canvas) {
        val tableRect = RectF(offsetX, offsetY, offsetX + GameConstants.VIRTUAL_WIDTH * scaleFactor, offsetY + GameConstants.VIRTUAL_HEIGHT * scaleFactor)
        canvas.drawRect(tableRect, cushionPaint)

        val playingSurfaceRect = RectF(
            offsetX + GameConstants.VIRTUAL_CUSHION_THICKNESS * scaleFactor,
            offsetY + GameConstants.VIRTUAL_CUSHION_THICKNESS * scaleFactor,
            offsetX + (GameConstants.VIRTUAL_WIDTH - GameConstants.VIRTUAL_CUSHION_THICKNESS) * scaleFactor,
            offsetY + (GameConstants.VIRTUAL_HEIGHT - GameConstants.VIRTUAL_CUSHION_THICKNESS) * scaleFactor
        )
        canvas.drawRect(playingSurfaceRect, tablePaint)
    }

    private fun drawPocket(canvas: Canvas, pocket: Pocket) {
        val screenX = offsetX + pocket.x * scaleFactor
        val screenY = offsetY + pocket.y * scaleFactor
        val screenRadius = pocket.radius * scaleFactor
        canvas.drawCircle(screenX, screenY, screenRadius, pocketPaint)
    }

    private fun drawBall(canvas: Canvas, ball: Ball) {
        // Don't draw balls that are effectively off-screen
        if (ball.x < 0) return

        ball.paint.color = ball.color

        val screenX = offsetX + ball.x * scaleFactor
        val screenY = offsetY + ball.y * scaleFactor
        val screenRadius = ball.radius * scaleFactor
        canvas.drawCircle(screenX, screenY, screenRadius, ball.paint)
    }

    private fun drawWinText(canvas: Canvas) {
        winTextPaint.textSize = 150f * scaleFactor
        val x = width / 2f
        val y = height / 2f
        canvas.drawText("You Win!", x, y, winTextPaint)
    }

    private fun drawAimingLine(canvas: Canvas) {
        val cueBall = balls.find { it.id == "cue" } ?: return

        val linePaint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f * scaleFactor
            isAntiAlias = true
        }

        val angleRad = Math.toRadians(aimAngleDegrees.toDouble()).toFloat()
        val lineLength = GameConstants.AIM_LINE_LENGTH

        val startX = offsetX + cueBall.x * scaleFactor
        val startY = offsetY + cueBall.y * scaleFactor
        val endX = startX + lineLength * cos(angleRad) * scaleFactor
        val endY = startY + lineLength * sin(angleRad) * scaleFactor
        canvas.drawLine(startX, startY, endX, endY, linePaint)

        // Draw arrow head
        val arrowLength = GameConstants.AIM_ARROW_LENGTH * scaleFactor
        val arrowAngleRad = Math.toRadians(GameConstants.AIM_ARROW_ANGLE_DEG.toDouble())
        val angle1 = angleRad - PI + arrowAngleRad
        val arrowX1 = endX + arrowLength * cos(angle1).toFloat()
        val arrowY1 = endY + arrowLength * sin(angle1).toFloat()
        canvas.drawLine(endX, endY, arrowX1, arrowY1, linePaint)

        val angle2 = angleRad + PI - arrowAngleRad
        val arrowX2 = endX + arrowLength * cos(angle2).toFloat()
        val arrowY2 = endY + arrowLength * sin(angle2).toFloat()
        canvas.drawLine(endX, endY, arrowX2, arrowY2, linePaint)
    }

    fun applyShot(angleDegrees: Float, power: Float) {
        if (isBallsMoving || awaitingServerResponse) return

        val cueBall = balls.find { it.id == "cue" } ?: return

        val maxSpeed = 3000f // This can be tuned
        val speed = maxSpeed * power
        val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()

        // Start local prediction
        cueBall.velocityX = speed * cos(angleRad)
        cueBall.velocityY = speed * sin(angleRad)
        isBallsMoving = true

        // Send authoritative shot to server
        awaitingServerResponse = true
        webSocketClient?.takeShot(gameId, angleDegrees, power)
    }

    inner class GameThread(private val surfaceHolder: SurfaceHolder, private val gameView: PoolGameView) : Thread() {
        @Volatile var running: Boolean = false
        private val timeStepMs = 1000.0 / 60.0 // 60 physics updates per second
        private var lastTime: Long = 0
        private var accumulator = 0.0

        override fun run() {
            lastTime = System.currentTimeMillis()
            while (running) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastTime).toDouble()
                lastTime = currentTime
                accumulator += deltaTime

                while (accumulator >= timeStepMs) {
                    gameView.update()
                    accumulator -= timeStepMs
                }

                val canvas: Canvas? = try {
                    surfaceHolder.lockCanvas()
                } catch (e: Exception) {
                    Log.e("GameThread", "Error locking canvas", e)
                    null
                }

                canvas?.let {
                    try {
                        synchronized(surfaceHolder) {
                            gameView.draw(it)
                        }
                    } finally {
                        surfaceHolder.unlockCanvasAndPost(it)
                    }
                }
            }
        }
    }
}