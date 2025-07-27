package com.example.securepool.ui.poolgame

import com.example.localpoolsimulator.Ball
import kotlin.math.*

/**
 * Represents a pocket on the pool table, using virtual coordinates.
 * @param x X coordinate of the pocket's center.
 * @param y Y coordinate of the pocket's center.
 * @param radius Radius of the pocket.
 */
data class Pocket(val x: Float, val y: Float, val radius: Float)

/**
 * Utility object for handling simplified 2D pool physics calculations.
 * This version uses a fixed, internal virtual coordinate system to ensure
 * consistency between the client and server.
 */
object GamePhysics {

    // --- Virtual World Constants (Must match server and PoolGameView) ---
    private const val VIRTUAL_WIDTH = 2000f
    private const val VIRTUAL_HEIGHT = 1000f
    private const val VIRTUAL_CUSHION_THICKNESS = 40f

    /**
     * Handles collision between a ball and the table boundaries (walls/cushions).
     * This function now uses the internal virtual constants.
     * @param ball The ball to check.
     */
    fun handleWallCollision(ball: Ball) {
        val restitution = 0.8f // Bounciness factor

        // Define the inner bounds of the playing surface using the virtual constants
        val minX = VIRTUAL_CUSHION_THICKNESS + ball.radius
        val maxX = VIRTUAL_WIDTH - VIRTUAL_CUSHION_THICKNESS - ball.radius
        val minY = VIRTUAL_CUSHION_THICKNESS + ball.radius
        val maxY = VIRTUAL_HEIGHT - VIRTUAL_CUSHION_THICKNESS - ball.radius

        // Check horizontal walls
        if (ball.x < minX) { // Left wall
            ball.x = minX
            ball.velocityX *= -restitution
        } else if (ball.x > maxX) { // Right wall
            ball.x = maxX
            ball.velocityX *= -restitution
        }

        // Check vertical walls
        if (ball.y < minY) { // Top wall
            ball.y = minY
            ball.velocityY *= -restitution
        } else if (ball.y > maxY) { // Bottom wall
            ball.y = maxY
            ball.velocityY *= -restitution
        }
    }

    /**
     * Handles collision between two balls.
     * Modifies velocities of both balls if a collision occurs.
     * @param b1 First ball.
     * @param b2 Second ball.
     */
    fun handleBallCollision(b1: Ball, b2: Ball) {
        val dx = b2.x - b1.x
        val dy = b2.y - b1.y
        val distance = sqrt(dx * dx + dy * dy)

        // Check if balls are overlapping (collision occurred)
        if (distance < b1.radius + b2.radius) {
            // Calculate unit normal and tangent vectors
            val nx = dx / distance
            val ny = dy / distance
            val tx = -ny
            val ty = nx

            // Project velocities onto the normal and tangent axes
            val v1n = b1.velocityX * nx + b1.velocityY * ny
            val v1t = b1.velocityX * tx + b1.velocityY * ty
            val v2n = b2.velocityX * nx + b2.velocityY * ny
            val v2t = b2.velocityX * tx + b2.velocityY * ty

            // For elastic collision (assuming equal mass), swap normal velocities
            val v1nAfter = v2n
            val v2nAfter = v1n

            // Convert scalar normal and tangent velocities back to vectors
            b1.velocityX = v1nAfter * nx + v1t * tx
            b1.velocityY = v1nAfter * ny + v1t * ty
            b2.velocityX = v2nAfter * nx + v2t * tx
            b2.velocityY = v2nAfter * ny + v2t * ty

            // Separate overlapping balls to prevent multiple collisions in one frame
            val overlap = (b1.radius + b2.radius) - distance
            b1.x -= overlap * nx * 0.5f
            b1.y -= overlap * ny * 0.5f
            b2.x += overlap * nx * 0.5f
            b2.y += overlap * ny * 0.5f
        }
    }

    /**
     * Checks if a ball has entered any of the given pockets.
     * @param ball The ball to check.
     * @param pockets A list of pockets on the table.
     * @return True if the ball is inside any pocket, false otherwise.
     */
    fun isBallInAnyPocket(ball: Ball, pockets: List<Pocket>): Boolean {
        for (pocket in pockets) {
            val dx = ball.x - pocket.x
            val dy = ball.y - pocket.y
            val distance = sqrt(dx * dx + dy * dy)
            // Ball is considered pocketed if its center is within the pocket's radius.
            if (distance < pocket.radius + ball.radius * 0.5f) {
                return true
            }
        }
        return false
    }
}
