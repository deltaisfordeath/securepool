package com.example.securepool.api

import com.example.securepool.model.*
import retrofit2.Response
import retrofit2.http.*

interface SecurePoolService {
    @POST("/api/register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<LoginResponse>

    @POST("/api/register-biometric")
    suspend fun registerBiometric(@Body request: BiometricRegisterRequest): Response<RegisterResponse>

    @GET("/api/challenge")
    suspend fun getChallenge(@Query("username") username: String): Response<ChallengeResponse>

    @POST("/api/challenge")
    suspend fun postChallenge(@Body request: SignedChallengeRequest): Response<LoginResponse>

    @POST("/api/login")
    suspend fun loginUser(@Body request: RegisterRequest): Response<LoginResponse>

    @GET("/api/score")
    suspend fun getScore(@Query("username") username: String): Response<ScoreResponse>

    @POST("/api/restore-score")
    suspend fun restoreScore(@Body request: RegisterRequest): Response<Unit>

    @GET("/api/leaderboard")
    suspend fun getLeaderboard(): Response<List<LeaderboardEntry>>

    @POST("/api/matchResult")
    suspend fun sendMatchResult(@Body result: MatchResultRequest): Response<Unit>

    @POST("/api/token/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>
}