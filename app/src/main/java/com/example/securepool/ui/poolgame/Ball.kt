package com.example.localpoolsimulator

import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

/**
 * Represents a single ball in the pool game.
 * Now uses a string 'id' to match the server-side model.
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
    // Time step matching the server's simulation (1/60th of a second)
    private val timeStepSec = 1.0f / 60.0f

    /**
     * Updates the ball's position based on its velocity using a fixed time step.
     */
    fun update() {
        // Apply friction (a simple deceleration)
        val frictionFactor = 0.99f // Adjust for desired friction
        velocityX *= frictionFactor
        velocityY *= frictionFactor

        // Stop if velocity is very low to prevent infinite crawling
        val stopThreshold = 1.5f // You can tune this value
        if (abs(velocityX) < stopThreshold) velocityX = 0f
        if (abs(velocityY) < stopThreshold) velocityY = 0f

        // Update position using the fixed time step
        x += velocityX * timeStepSec
        y += velocityY * timeStepSec
    }

    /**
     * Checks if the ball is currently moving.
     * @return True if the ball has significant velocity, false otherwise.
     */
    fun isMoving(): Boolean {
        return velocityX != 0f || velocityY != 0f
    }
}
