package com.example.securepool.api

import android.content.Context
import com.example.securepool.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator constructor(
    private val tokenManager: TokenManager,
    private val context: Context // We need context to re-create the service
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // We need to synchronize to avoid multiple threads trying to refresh at the same time
        synchronized(this) {
            val accessToken = tokenManager.getAccessToken()
            val refreshToken = tokenManager.getRefreshToken()

            // If the request already has the latest token, or we have no refresh token, we can't proceed.
            if (accessToken == null || refreshToken == null || response.request.header("Authorization") == "Bearer $accessToken") {
                return null // Give up, authentication is not possible.
            }

            // Use runBlocking to call the suspend function from a synchronous context
            val newTokens = runBlocking {
                val apiService = RetrofitClient.create(context) // Re-create service
                apiService.refreshToken(RefreshTokenRequest(refreshToken)).body()
            }

            return if (newTokens != null) {
                // Save the new tokens
                tokenManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)

                // Retry the original request with the new access token
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } else {
                // The refresh token was invalid. Log out the user.
                tokenManager.clearTokens()
                null // Give up.
            }
        }
    }
}