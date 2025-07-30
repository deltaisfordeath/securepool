/**
 * @file Ball.js
 * Represents a single ball in the pool game for the server-side simulation.
 */

// This threshold determines when a ball is considered "stopped".
const STOP_THRESHOLD = 1.0;
const BALL_RADIUS = 22.5;

export class Ball {
    /**
     * @param {number} x Initial X coordinate.
     * @param {number} y Initial Y coordinate.
     * @param {string} id A unique identifier for the ball (e.g., 'cue', '8-ball').
     */
    constructor(x, y, id) {
        this.x = x;
        this.y = y;
        this.radius = BALL_RADIUS;
        this.id = id; // Useful for identifying balls on the client

        this.velocityX = 0;
        this.velocityY = 0;
    }

    /**
     * Updates the ball's position based on its velocity and applies friction.
     * This logic should mirror the Kotlin implementation.
     * @param {number} deltaTime Time elapsed since the last update (in seconds).
     */
    update(deltaTime) {
        // Apply friction (a simple deceleration)
        const frictionFactor = 0.99;
        this.velocityX *= frictionFactor;
        this.velocityY *= frictionFactor;

        // Stop if velocity is very low to prevent infinite crawling
        if (Math.abs(this.velocityX) < STOP_THRESHOLD) {
            this.velocityX = 0;
        }
        if (Math.abs(this.velocityY) < STOP_THRESHOLD) {
            this.velocityY = 0;
        }

        // Update position
        this.x += this.velocityX * deltaTime;
        this.y += this.velocityY * deltaTime;
    }

    /**
     * Checks if the ball is currently moving.
     * @returns {boolean} True if the ball has any significant velocity.
     */
    isMoving() {
        return this.velocityX !== 0 || this.velocityY !== 0;
    }
}