package com.example.securepool.ui.poolgame

import com.example.localpoolsimulator.Ball
import kotlin.math.sqrt

/**
 * Represents a pocket on the pool table, using virtual coordinates.
 * @param x X coordinate of the pocket's center.
 * @param y Y coordinate of the pocket's center.
 * @param radius Radius of the pocket.
 */
data class Pocket(val x: Float, val y: Float, val radius: Float)

/**
 * Utility object for handling simplified 2D pool physics calculations.
 * This version uses the centralized GameConstants for all physics values.
 */
object GamePhysics {

    /**
     * Handles collision between a ball and the table boundaries (walls/cushions).
     * @param ball The ball to check.
     */
    fun handleWallCollision(ball: Ball) {
        val minX = GameConstants.VIRTUAL_CUSHION_THICKNESS + ball.radius
        val maxX = GameConstants.VIRTUAL_WIDTH - GameConstants.VIRTUAL_CUSHION_THICKNESS - ball.radius
        val minY = GameConstants.VIRTUAL_CUSHION_THICKNESS + ball.radius
        val maxY = GameConstants.VIRTUAL_HEIGHT - GameConstants.VIRTUAL_CUSHION_THICKNESS - ball.radius

        if (ball.x <= minX) {
            ball.x = minX
            ball.velocityX *= -GameConstants.WALL_RESTITUTION
        } else if (ball.x >= maxX) {
            ball.x = maxX
            ball.velocityX *= -GameConstants.WALL_RESTITUTION
        }

        if (ball.y <= minY) {
            ball.y = minY
            ball.velocityY *= -GameConstants.WALL_RESTITUTION
        } else if (ball.y >= maxY) {
            ball.y = maxY
            ball.velocityY *= -GameConstants.WALL_RESTITUTION
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
        val distanceSquared = dx * dx + dy * dy
        val combinedRadius = b1.radius + b2.radius

        // Check if balls are overlapping (collision occurred)
        if (distanceSquared < combinedRadius * combinedRadius) {
            val distance = sqrt(distanceSquared)
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

            // In an elastic collision with equal mass, normal velocities are swapped
            val v1nAfter = v2n
            val v2nAfter = v1n

            // Convert scalar normal and tangent velocities back to vectors
            b1.velocityX = v1nAfter * nx + v1t * tx
            b1.velocityY = v1nAfter * ny + v1t * ty
            b2.velocityX = v2nAfter * nx + v2t * tx
            b2.velocityY = v2nAfter * ny + v2t * ty

            // Separate overlapping balls to prevent them from getting stuck
            val overlap = 0.5f * (combinedRadius - distance)
            b1.x -= overlap * nx
            b1.y -= overlap * ny
            b2.x += overlap * nx
            b2.y += overlap * ny
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
            val distanceSquared = dx * dx + dy * dy
            // Ball is pocketed if its center is within the pocket's radius.
            // Using squared distances is faster as it avoids a square root calculation.
            val effectivePocketRadius = pocket.radius + ball.radius * 0.5f
            if (distanceSquared < effectivePocketRadius * effectivePocketRadius) {
                return true
            }
        }
        return false
    }
}