/**
 * @file gamePhysics.js
 * A direct JavaScript port of the Kotlin GamePhysics object for handling
 * simplified 2D pool physics calculations on a server.
 */

export const GamePhysics = {
    // --- Constants defining the virtual table (MOVED INSIDE) ---
    VIRTUAL_TABLE_WIDTH: 2000,
    VIRTUAL_TABLE_HEIGHT: 1000,
    CUSHION_THICKNESS: 40,
    BALL_RADIUS: 22.5,

    /**
     * Handles collision between a ball and the table boundaries (walls/cushions).
     * @param {object} ball The ball to check.
     */
    handleWallCollision(ball) {
        const restitution = 0.8; // Bounciness factor

        // Define the inner bounds of the playing surface
        const minX = this.CUSHION_THICKNESS + ball.radius;
        const maxX = this.VIRTUAL_TABLE_WIDTH - this.CUSHION_THICKNESS - ball.radius;
        const minY = this.CUSHION_THICKNESS + ball.radius;
        const maxY = this.VIRTUAL_TABLE_HEIGHT - this.CUSHION_THICKNESS - ball.radius;

        // Check horizontal walls
        if (ball.x < minX) {
            ball.x = minX;
            ball.velocityX *= -restitution;
        } else if (ball.x > maxX) {
            ball.x = maxX;
            ball.velocityX *= -restitution;
        }

        // Check vertical walls
        if (ball.y < minY) {
            ball.y = minY;
            ball.velocityY *= -restitution;
        } else if (ball.y > maxY) {
            ball.y = maxY;
            ball.velocityY *= -restitution;
        }
    },

    /**
     * Handles collision between two balls using elastic collision logic.
     * @param {object} b1 First ball.
     * @param {object} b2 Second ball.
     */
    handleBallCollision(b1, b2) {
        const dx = b2.x - b1.x;
        const dy = b2.y - b1.y;
        const distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < b1.radius + b2.radius) {
            console.log(`Collision detected between ${b1.id} and ${b2.id}`);
            const nx = dx / distance;
            const ny = dy / distance;
            const tx = -ny;
            const ty = nx;

            const v1n = b1.velocityX * nx + b1.velocityY * ny;
            const v1t = b1.velocityX * tx + b1.velocityY * ty;
            const v2n = b2.velocityX * nx + b2.velocityY * ny;
            const v2t = b2.velocityX * tx + b2.velocityY * ty;

            const v1nAfter = v2n;
            const v2nAfter = v1n;

            b1.velocityX = v1nAfter * nx + v1t * tx;
            b1.velocityY = v1nAfter * ny + v1t * ty;
            b2.velocityX = v2nAfter * nx + v2t * tx;
            b2.velocityY = v2nAfter * ny + v2t * ty;

            const overlap = (b1.radius + b2.radius) - distance;
            b1.x -= overlap * nx * 0.5;
            b1.y -= overlap * ny * 0.5;
            b2.x += overlap * nx * 0.5;
            b2.y += overlap * ny * 0.5;
            console.log(`Collision resolved: ${b1.id} and ${b2.id} now at (${b1.x}, ${b1.y}) and (${b2.x}, ${b2.y})`);
        }
    },

    /**
     * Checks if a ball has entered any of the given pockets.
     * @param {object} ball The ball to check.
     * @param {Array<object>} pockets An array of pocket objects.
     * @returns {boolean} True if the ball is inside any pocket.
     */
    isBallInAnyPocket(ball, pockets) {
        for (const pocket of pockets) {
            const dx = ball.x - pocket.x;
            const dy = ball.y - pocket.y;
            const distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < pocket.radius + ball.radius * 0.5) {
                return true;
            }
        }
        return false;
    }
};
