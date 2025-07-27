package com.example.securepool.ui.poolgame

import android.graphics.Color

/**
 * Singleton object to hold all shared constants for the pool game simulation.
 * This ensures consistency between the physics engine and the rendering view.
 */
object GameConstants {
    // --- Virtual World Dimensions ---
    const val VIRTUAL_WIDTH = 2000f
    const val VIRTUAL_HEIGHT = 1000f
    const val VIRTUAL_CUSHION_THICKNESS = 40f

    // --- Ball Properties ---
    const val VIRTUAL_BALL_RADIUS = 22.5f

    // --- Pocket Properties ---
    const val VIRTUAL_POCKET_RADIUS = 45f

    // --- Physics Parameters ---
    const val PHYSICS_TIME_STEP_SEC = 1.0f / 60.0f
    const val FRICTION_FACTOR = 0.99f
    const val STOP_VELOCITY_THRESHOLD = 1.5f
    const val WALL_RESTITUTION = 0.8f // Bounciness

    // --- Aiming Line ---
    const val AIM_LINE_LENGTH = 700f
    const val AIM_ARROW_LENGTH = 40f
    const val AIM_ARROW_ANGLE_DEG = 25f

    // --- Ball Colors ---
    val CUE_BALL_COLOR: Int = Color.WHITE
    val EIGHT_BALL_COLOR: Int = Color.BLACK
}