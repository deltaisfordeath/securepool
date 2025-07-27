package com.example.securepool.api

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class SecureWebSocketClient(
    private val context: Context,
    private val serverUrl: String = "https://10.0.2.2:443"
) {

    private lateinit var mSocket: Socket
    private var currentGameId: String? = null

    // Public property to safely access the socket ID
    val socketId: String?
        get() = if (::mSocket.isInitialized && mSocket.connected()) mSocket.id() else null

    // Callbacks for matchmaking and game events
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onError: ((exception: Exception) -> Unit)? = null
    var onGameStateUpdate: ((state: JSONObject) -> Unit)? = null
    var onWaitingForOpponent: (() -> Unit)? = null
    var onMatchFound: ((gameId: String, initialState: JSONObject) -> Unit)? = null
    var onOpponentDisconnected: (() -> Unit)? = null

    init {
        try {
            val options = IO.Options().apply {
                val token = TokenManager(context).getAccessToken()
                auth = mapOf("token" to "Bearer $token")
                forceNew = true
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
            }
            mSocket = IO.socket(serverUrl, options)
            setupListeners()
        } catch (e: URISyntaxException) {
            Log.e("SocketIO", "URI Syntax Error: ${e.message}", e)
            onError?.invoke(e)
        }
    }

    private fun setupListeners() {
        mSocket.on(Socket.EVENT_CONNECT) {
            Log.d("SocketIO", "✅ Connection Established: ${mSocket.id()}")
            onConnected?.invoke()
        }
        mSocket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("SocketIO", "❌ Connection Error: ${args.joinToString(", ")}")
        }
        mSocket.on(Socket.EVENT_DISCONNECT) { args ->
            Log.d("SocketIO", "🔌 Disconnected: ${args.joinToString(", ")}")
            onDisconnected?.invoke(args.getOrNull(0)?.toString() ?: "Unknown reason")
        }
        mSocket.on("gameStateUpdate") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { onGameStateUpdate?.invoke(it) }
        }

        mSocket.on("waitingForOpponent") {
            Log.d("SocketIO", "⏳ Waiting for opponent...")
            onWaitingForOpponent?.invoke()
        }
        mSocket.on("matchFound") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { data ->
                val gameId = data.getString("gameId")
                val gameState = data.getJSONObject("gameState")
                this.currentGameId = gameId
                Log.d("SocketIO", "🎉 Match found! Game ID: $gameId")
                onMatchFound?.invoke(gameId, gameState)
            }
        }
        mSocket.on("opponentDisconnected") {
            Log.w("SocketIO", "Opponent has disconnected from the match.")
            onOpponentDisconnected?.invoke()
            currentGameId = null // Clear the current game
        }
    }

    fun connect() {
        if (!mSocket.connected()) {
            mSocket.connect()
        }
    }

    fun joinPracticeGame() {
        if (mSocket.connected()) {
            val data = JSONObject().put("mode", "practice")
            mSocket.emit("joinGame", data)
        }
    }

    fun findMatch() {
        if (mSocket.connected()) {
            val data = JSONObject().put("mode", "match")
            mSocket.emit("joinGame", data)
        }
    }

    fun takeShot(angle: Float, power: Float) {
        if (mSocket.connected() && currentGameId != null) {
            val shotData = JSONObject().apply {
                put("gameId", currentGameId)
                put("angle", angle)
                put("power", power)
            }
            mSocket.emit("takeShot", shotData)
        } else {
            Log.w("SocketIO", "Cannot take shot: Not in a game or socket disconnected.")
        }
    }

    /**
     * Sends a message to the Socket.IO server by emitting a custom event.
     * The event name 'clientMessage' must match what your Node.js server expects to receive.
     *
     * @param message The string message to send.
     */
    fun sendMessage(message: String) {
        if (mSocket.connected()) {
            mSocket.emit("message", message)
            Log.d("SocketIO", "Sent: $message")
        } else {
            Log.w("SocketIO", "Socket not connected, message not sent: $message")
        }
    }

    fun disconnect() {
        mSocket.disconnect()
        currentGameId = null
        Log.d("SocketIO", "Client disconnected")
    }

    fun isConnected(): Boolean = mSocket.connected()
}