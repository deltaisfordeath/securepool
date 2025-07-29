package com.example.securepool.api

import android.content.Context
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URISyntaxException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SecureWebSocketClient(
    private val context: Context,
    private val serverUrl: String = "https://10.0.2.2" // Port 443 is implied by https
) {

    private lateinit var mSocket: Socket
    private var currentGameId: String? = null

    val socketId: String?
        get() = if (::mSocket.isInitialized && mSocket.connected()) mSocket.id() else null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onError: ((exception: Exception) -> Unit)? = null
    var onGameStateUpdate: ((state: JSONObject) -> Unit)? = null
    var onWaitingForOpponent: (() -> Unit)? = null
    var onMatchFound: ((gameId: String, initialState: JSONObject) -> Unit)? = null
    var onOpponentShot: ((angle: Float, power: Float) -> Unit)? = null
    var onOpponentDisconnected: (() -> Unit)? = null

    init {
        try {
            // NEW: Create the custom OkHttpClient
            val unsafeOkHttpClient = createUnsafeOkHttpClient()

            val options = IO.Options().apply {
                // NEW: Assign the custom client to the options
                callFactory = unsafeOkHttpClient
                webSocketFactory = unsafeOkHttpClient

                // Your existing options
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

    /**
     * 🔒 WARNING: DEVELOPMENT ONLY
     * Creates an OkHttpClient that trusts all SSL certificates, including self-signed ones.
     * Do NOT use this in a production environment.
     */
    private fun createUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true } // Disable hostname verification for dev
            .build()
    }


    // --- All your other functions (setupListeners, connect, etc.) remain unchanged ---

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
        mSocket.on("opponentTookShot") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { data ->
                val angle = data.getDouble("angle").toFloat()
                val power = data.getDouble("power").toFloat()
                Log.d("SocketIO", "Opponent took shot: angle=$angle, power=$power")
                onOpponentShot?.invoke(angle, power)
            }
        }
        mSocket.on("opponentDisconnected") {
            Log.w("SocketIO", "Opponent has disconnected from the match.")
            onOpponentDisconnected?.invoke()
            currentGameId = null
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

    fun disconnect() {
        mSocket.disconnect()
        currentGameId = null
        Log.d("SocketIO", "Client disconnected")
    }

    fun isConnected(): Boolean = mSocket.connected()
}