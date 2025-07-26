package com.example.securepool.model

data class LoginResponse (
    val success: Boolean,
    val message: String?,
    val username: String,
    val score: Int,
    val lastZeroTimestamp: String?,
    val accessToken: String?,
    val refreshToken: String?
)