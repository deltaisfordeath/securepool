package com.example.securepool.api

import android.content.Context
import android.util.Log
import android.widget.Toast
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * A client for connecting to a Socket.IO server from an Android application.
 * This class handles the connection, sending, and receiving of messages using
 * the socket.io-client-java library.
 *
 * @param context The application context.
 * @param serverUrl The URL of the Socket.IO server (e.g., "http://10.0.2.2:3000").
 */
class SecureWebSocketClient(
    private val context: Context,
    private val serverUrl: String = "https://10.0.2.2:443" // Default to Android emulator's localhost
) {

    // The Socket.IO client instance
    private lateinit var mSocket: Socket

    // Callbacks to notify the calling component (e.g., Activity/Fragment) about events
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null
    var onMessageReceived: ((message: String) -> Unit)? = null
    var onError: ((exception: Exception) -> Unit)? = null

    init {
        try {
            // Configure Socket.IO connection options
            val options = IO.Options()

            val tokenManager = TokenManager(context)
            val token = tokenManager.getAccessToken()
            val authMap = mutableMapOf<String, String>()
            authMap["token"] = "Bearer $token"
            options.auth = authMap

            // Ensure a new connection is established, useful if reusing the client instance
            options.forceNew = true
            // Enable automatic re-connection attempts
            options.reconnection = true
            // Set the number of re-connection attempts
            options.reconnectionAttempts = 5
            // Set the delay between re-connection attempts in milliseconds
            options.reconnectionDelay = 1000

            // Initialize the Socket.IO client with the server URL and options
            mSocket = IO.socket(serverUrl, options)

            // Set up event listeners for the Socket.IO connection
            setupListeners()

        } catch (e: URISyntaxException) {
            // Log and report any URI syntax errors during initialization
            Log.e("SocketIO", "URI Syntax Error: ${e.message}", e)
            onError?.invoke(e)
        }
    }

    /**
     * Sets up the various event listeners for the Socket.IO client.
     * These listeners handle connection status, errors, and incoming messages.
     */
    private fun setupListeners() {
        // Listener for the 'connect' event (when the socket successfully connects)
        mSocket.on(Socket.EVENT_CONNECT, Emitter.Listener {
            Log.d("SocketIO", "Connection opened")
            onConnected?.invoke() // Trigger the external connected callback
        })

        // Listener for the 'disconnect' event (when the socket disconnects)
        mSocket.on(Socket.EVENT_DISCONNECT, Emitter.Listener { args ->
            val reason = args[0] as String // The reason for disconnection
            Log.d("SocketIO", "Closed: $reason")
            onDisconnected?.invoke(reason) // Trigger the external disconnected callback
        })

        // Listener for connection errors
        mSocket.on(Socket.EVENT_CONNECT_ERROR, Emitter.Listener { args ->
            val error = args[0] as Exception // The exception object
            Log.e("SocketIO", "Error: ${error.message}", error)
            onError?.invoke(error) // Trigger the external error callback
        })

        // Listener for a custom event named 'serverMessage' from the server.
        // This event name must match what your Node.js server emits.
        mSocket.on("serverMessage", Emitter.Listener { args ->
            // Extract the message from the event arguments.
            // Socket.IO events can send multiple arguments; here we expect the first to be a String.
            val message = args[0] as String
            onMessageReceived?.invoke(message) // Trigger the external message received callback
        })

        // You can add more listeners for other custom events specific to your application
        // Example: mSocket.on("gameUpdate", Emitter.Listener { args -> ... })
    }

    /**
     * Initiates the connection to the Socket.IO server.
     */
    fun connect() {
        mSocket.connect()
    }

    /**
     * Sends a message to the Socket.IO server by emitting a custom event.
     * The event name 'clientMessage' must match what your Node.js server expects to receive.
     *
     * @param message The string message to send.
     */
    fun send(message: String) {
        if (mSocket.connected()) {
            mSocket.emit("message", message)
            Log.d("SocketIO", "Sent: $message")
        } else {
            Log.w("SocketIO", "Socket not connected, message not sent: $message")
        }
    }

    /**
     * Disconnects from the Socket.IO server.
     */
    fun disconnect() {
        mSocket.disconnect()
        Log.d("SocketIO", "Client disconnected")
    }

    /**
     * Checks if the Socket.IO client is currently connected.
     * @return True if connected, false otherwise.
     */
    fun isConnected(): Boolean {
        return mSocket.connected()
    }
}
