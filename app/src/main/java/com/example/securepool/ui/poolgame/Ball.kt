package com.example.localpoolsimulator

import android.graphics.Paint
import com.example.securepool.ui.poolgame.GameConstants
import kotlin.math.abs

/**
 * Represents a single ball in the pool game.
 * Now uses constants from the GameConstants object for physics properties.
 *
 * @param id A unique identifier for the ball (e.g., "cue", "8-ball").
 * @param x Current X coordinate of the ball's center.
 * @param y Current Y coordinate of the ball's center.
 * @param radius Radius of the ball.
 * @param color Color of the ball.
 */
data class Ball(
    val id: String,
    var x: Float,
    var y: Float,
    val radius: Float,
    val color: Int,
) {
    var velocityX: Float = 0f
    var velocityY: Float = 0f

    val paint: Paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }

    /**
     * Updates the ball's position based on its velocity and applies friction.
     */
    fun update() {
        // Apply friction (a simple deceleration)
        velocityX *= GameConstants.FRICTION_FACTOR
        velocityY *= GameConstants.FRICTION_FACTOR

        // Stop if velocity is very low to prevent infinite crawling
        if (abs(velocityX) < GameConstants.STOP_VELOCITY_THRESHOLD) velocityX = 0f
        if (abs(velocityY) < GameConstants.STOP_VELOCITY_THRESHOLD) velocityY = 0f

        // Update position using the fixed time step
        x += velocityX * GameConstants.PHYSICS_TIME_STEP_SEC
        y += velocityY * GameConstants.PHYSICS_TIME_STEP_SEC
    }

    /**
     * Checks if the ball is currently moving.
     * @return True if the ball has significant velocity, false otherwise.
     */
    fun isMoving(): Boolean {
        return velocityX != 0f || velocityY != 0f
    }
}