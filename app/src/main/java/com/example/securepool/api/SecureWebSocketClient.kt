package com.example.securepool.api

import android.content.Context
import android.util.Log
import com.example.securepool.security.AuditLogger
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.net.URISyntaxException

class SecureWebSocketClient(
    private val context: Context,
    private val serverUrl: String = "https://10.0.2.2:443"
) {
    private lateinit var mSocket: Socket

    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onMessageReceived: ((message: String) -> Unit)? = null
    var onError: ((exception: Exception) -> Unit)? = null

    init {
        try {
            val options = IO.Options()

            val tokenManager = TokenManager(context)
            val token = tokenManager.getAccessToken()
            val authMap = mutableMapOf<String, String>()
            authMap["token"] = "Bearer $token"
            options.auth = authMap

            options.forceNew = true
            options.reconnection = true
            options.reconnectionAttempts = 5
            options.reconnectionDelay = 1000

            mSocket = IO.socket(serverUrl, options)
            setupListeners()
        } catch (e: URISyntaxException) {
            Log.e("SocketIO", "URI Syntax Error: ${e.message}", e)
            onError?.invoke(e)
        }
    }

    private fun setupListeners() {
        mSocket.on(Socket.EVENT_CONNECT, Emitter.Listener {
            Log.d("SocketIO", "Connection opened")
            AuditLogger.logEvent("WebSocket Connected", mapOf("url" to serverUrl))
            onConnected?.invoke()
        })

        mSocket.on(Socket.EVENT_DISCONNECT, Emitter.Listener { args ->
            val reason = args[0] as String
            Log.d("SocketIO", "Closed: $reason")
            AuditLogger.logEvent("WebSocket Disconnected", mapOf("reason" to reason))
            onDisconnected?.invoke(reason)
        })

        mSocket.on(Socket.EVENT_CONNECT_ERROR, Emitter.Listener { args ->
            val error = args[0] as Exception
            Log.e("SocketIO", "Error: ${error.message}", error)
            AuditLogger.logEvent("WebSocket Error", mapOf("message" to (error.message ?: "unknown")))
            onError?.invoke(error)
        })

        mSocket.on("serverMessage", Emitter.Listener { args ->
            val message = args[0] as String
            AuditLogger.logEvent("WebSocket Message Received", mapOf("payload" to message.take(100)))
            onMessageReceived?.invoke(message)
        })
    }

    fun connect() {
        mSocket.connect()
    }

    fun send(message: String) {
        if (mSocket.connected()) {
            mSocket.emit("message", message)
            Log.d("SocketIO", "Sent: $message")
            AuditLogger.logEvent("WebSocket Message Sent", mapOf("payload" to message.take(100)))
        } else {
            Log.w("SocketIO", "Socket not connected, message not sent: $message")
        }
    }

    fun disconnect() {
        mSocket.disconnect()
        Log.d("SocketIO", "Client disconnected")
    }

    fun isConnected(): Boolean {
        return mSocket.connected()
    }
}
