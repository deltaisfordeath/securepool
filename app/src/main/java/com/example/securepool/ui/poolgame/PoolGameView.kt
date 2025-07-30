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
import org.json.JSONObject
import kotlin.math.*


private sealed class GameEndState {
    object None : GameEndState()
    object Won : GameEndState()
    object Lost : GameEndState()
}
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
    private var gameEndState: GameEndState = GameEndState.None
    var isBallsMoving: Boolean = false
    private var awaitingServerResponse = false
    var onEightBallPocketed: (() -> Unit)? = null
    var onAimAngleUpdated: ((Float) -> Unit)? = null
    private var myPlayerId: String? = null

    // --- Aiming ---
    var aimAngleDegrees: Float = 0f
        set(value) {
            field = value
            if (!isBallsMoving) postInvalidate()
        }
    var shotPower: Float = 0.5f

    // --- Networking ---
    @Volatile private var pendingServerState: JSONObject? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        setupPockets()

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
    }

    fun applyStateFromServer(data: JSONObject) {
        val ballsData = data.optJSONArray("balls") ?: return

        // Use a temporary list to avoid concurrent modification issues during redraw
        val newBalls = mutableListOf<Ball>()
        for (i in 0 until ballsData.length()) {
            val ballJson = ballsData.getJSONObject(i)
            val id = ballJson.getString("id")
            val x = ballJson.getDouble("x").toFloat()
            val y = ballJson.getDouble("y").toFloat()
            val isPocketed = ballJson.getBoolean("isPocketed")

            val color = when (id) {
                "cue" -> GameConstants.CUE_BALL_COLOR
                "8-ball" -> GameConstants.EIGHT_BALL_COLOR
                else -> Color.RED // Default for other balls if added later
            }

            val ball = Ball(id, x, y, GameConstants.VIRTUAL_BALL_RADIUS, color)
            if (isPocketed) ball.x = -1000f // Move pocketed balls off-screen
            newBalls.add(ball)
        }

        synchronized(balls) {
            balls.clear()
            balls.addAll(newBalls)
        }

        updateAimAngleFromBallPositions()

        if (data.optString("gameState") == "GameOver") {
            val winnerId = data.optString("winnerId", null)
            gameEndState = if (winnerId != null && winnerId == myPlayerId) {
                GameEndState.Won
            } else {
                GameEndState.Lost
            }
        } else {
            gameEndState = GameEndState.None
        }

        // After applying state, redraw the view
        postInvalidate()
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

    fun setPendingServerState(state: JSONObject, socketId: String?) {
        pendingServerState = state
        myPlayerId = socketId
    }

    fun update() {
        // --- NEW: Check for an early game-over condition ---
        // Peek at the pending state from the server without consuming it yet.
        pendingServerState?.let { state ->
            val eightBall = balls.find { it.id == "8-ball" }
            // If the client predicts the 8-ball is pocketed AND the server's pending state confirms it's game over...
            if (eightBall != null && GamePhysics.isBallInAnyPocket(eightBall, pockets) && state.optString("gameState") == "GameOver") {

                // ...then stop the local animation immediately.
                isBallsMoving = false

                // Apply the final, authoritative state from the server.
                applyStateFromServer(state)

                // Clear the pending state and exit the update cycle.
                pendingServerState = null
                return
            }
        }

        // --- If no early exit, continue with the normal animation loop ---
        if (isBallsMoving) {
            var stillMovingCount = 0
            synchronized(balls) {
                balls.forEach { ball ->
                    ball.update()
                    if (ball.isMoving()) stillMovingCount++
                }

                for (i in balls.indices) {
                    GamePhysics.handleWallCollision(balls[i])
                    for (j in i + 1 until balls.size) {
                        GamePhysics.handleBallCollision(balls[i], balls[j])
                    }
                }

                // This prediction still makes the 8-ball disappear visually if the server state hasn't arrived yet.
                val eightBall = balls.find { it.id == "8-ball" }
                if (eightBall != null && eightBall.x > 0) {
                    if (GamePhysics.isBallInAnyPocket(eightBall, pockets)) {
                        eightBall.x = -1000f
                        eightBall.velocityX = 0f
                        eightBall.velocityY = 0f
                    }
                }
            }

            if (stillMovingCount == 0) {
                isBallsMoving = false
            }
        }

        // --- Regular Reconciliation: Apply server state after normal animation finishes ---
        if (!isBallsMoving && pendingServerState != null) {
            val stateToApply = pendingServerState!!
            pendingServerState = null
            applyStateFromServer(stateToApply)
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
        when (gameEndState) {
            is GameEndState.Won -> drawEndGameText(canvas, "You Win!")
            is GameEndState.Lost -> drawEndGameText(canvas, "You Lose!")
            is GameEndState.None -> { /* Do nothing */ }
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

    private fun drawEndGameText(canvas: Canvas, text: String) {
        winTextPaint.textSize = 150f * scaleFactor
        val x = width / 2f
        val y = height / 2f
        canvas.drawText(text, x, y, winTextPaint)
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
        if (isBallsMoving) return

        val cueBall = balls.find { it.id == "cue" } ?: return

        val maxSpeed = 3000f
        val speed = maxSpeed * power
        val angleRad = Math.toRadians(angleDegrees.toDouble()).toFloat()

        cueBall.velocityX = speed * cos(angleRad)
        cueBall.velocityY = speed * sin(angleRad)
        isBallsMoving = true
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