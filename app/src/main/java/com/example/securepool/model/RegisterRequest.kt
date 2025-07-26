package com.example.securepool.model

data class RegisterRequest(
    val username: String,
    val password: String,
    val publicKey: String? = null
)

