package com.example.securepool.model

data class SignedChallengeRequest(
    val userId: String,
    val signedChallenge: String
)