package com.example.securepool.api

import android.util.Log
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class SecureWebSocketClient(
    private val token: String // Auth token, if needed
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private lateinit var webSocket: WebSocket

    fun connect() {
        val request = Request.Builder()
            .url("wss://yourserver.com/socket?token=$token") // replace with real URL
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connection opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Received binary: ${bytes.hex()}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed: $code / $reason")
            }
        })
    }

    fun send(message: String) {
        webSocket.send(message)
    }

    fun disconnect() {
        webSocket.close(1000, "Client closed")
    }
}
