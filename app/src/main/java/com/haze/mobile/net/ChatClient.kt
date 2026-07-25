package com.haze.mobile.net

import com.haze.mobile.crypto.PasswordHash
import com.haze.mobile.crypto.SessionCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.Socket
import java.util.UUID

/**
 * Join-mode client — the Kotlin twin of `network/client.py`.
 *
 * Connects to the host's onion service through Tor's SOCKS proxy, performs the
 * X25519 handshake, then relays encrypted chat frames. All decrypted inner
 * payloads (plus a few synthetic lifecycle events) are delivered via [onEvent].
 *
 * Synthetic event types emitted by this class (not on the wire):
 *   connected · auth_failed · disconnected
 */
class ChatClient(
    onionHost: String,
    private val nick: String,
    private val sessionPassword: String,
    private val socksHost: String,
    private val socksPort: Int,
    private val onEvent: (JsonObject) -> Unit,
) {
    private val onionHost = onionHost.trim().removeSuffix("/")
    private val crypto = SessionCrypto()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()

    @Volatile private var socket: Socket? = null
    @Volatile private var connected = false

    fun start() {
        scope.launch {
            try {
                runSession()
            } catch (_: Exception) {
                connected = false
                emit("disconnected")
            }
        }
    }

    private suspend fun runSession() {
        val s = Socks5.connect(socksHost, socksPort, onionHost, Protocol.CHAT_PORT, CONNECT_TIMEOUT_MS)
        socket = s
        val out = s.getOutputStream()
        val inp = s.getInputStream()

        // Handshake: send hello, receive welcome (or auth_failed).
        val hello = Protocol.hello(crypto.publicKeyB64, nick, PasswordHash.hash(sessionPassword))
        Framing.writeMessage(out, encode(hello))

        val response = decode(Framing.readMessage(inp))
        when (response["type"]?.jsonPrimitive?.content) {
            "auth_failed" -> {
                emit("auth_failed")
                runCatching { s.close() }
                return
            }
            "welcome" -> Unit
            else -> throw IOException("Unexpected handshake response")
        }

        crypto.unwrapSessionKey(
            response.str("public_key"),
            response.str("nonce"),
            response.str("ciphertext"),
        )
        connected = true
        emit("connected")
        startHeartbeat()

        // Read loop — decrypt every encrypted frame and surface the inner payload.
        while (connected) {
            val frame = decode(Framing.readMessage(inp))
            if (frame["type"]?.jsonPrimitive?.content != "encrypted") continue
            val plain = crypto.decrypt(frame.str("nonce"), frame.str("ciphertext"))
            onEvent(decode(plain))
        }
    }

    /**
     * Periodic keepalive. Idle clients send no traffic, so the host's read
     * timeout would eventually drop the connection and broadcast our `leave`
     * (the user appears to "leave" after sitting quietly). A steady `ping`
     * keeps the socket alive; the host answers with `pong` (ignored here).
     */
    private fun startHeartbeat() {
        scope.launch {
            while (connected) {
                delay(PING_INTERVAL_MS)
                if (!connected) break
                runCatching { sendEncrypted(Protocol.ping(System.currentTimeMillis() / 1000.0)) }
            }
        }
    }

    // ── Sending ────────────────────────────────────────────────────────────

    fun sendChat(content: String): String {
        val msgId = UUID.randomUUID().toString()
        enqueue(Protocol.chat(nick, content, msgId))
        return msgId
    }

    fun sendTyping(isTyping: Boolean) = enqueue(Protocol.typing(nick, isTyping))

    fun sendPanic() = enqueue(Protocol.panic(nick))

    /** Split [data] into 256 KB chunks and stream it as file_start/chunk/end. */
    fun sendFile(fileId: String, filename: String, mime: String, data: ByteArray) {
        scope.launch {
            runCatching {
                val totalChunks = (data.size + Protocol.CHUNK_SIZE - 1) / Protocol.CHUNK_SIZE
                sendEncrypted(Protocol.fileStart(nick, fileId, filename, mime, data.size, totalChunks))
                var i = 0
                var offset = 0
                while (offset < data.size) {
                    val end = minOf(offset + Protocol.CHUNK_SIZE, data.size)
                    val b64 = android.util.Base64.encodeToString(
                        data.copyOfRange(offset, end), android.util.Base64.NO_WRAP
                    )
                    sendEncrypted(Protocol.fileChunk(nick, fileId, i, b64))
                    offset = end; i++
                }
                sendEncrypted(Protocol.fileEnd(nick, fileId))
            }
        }
    }

    fun disconnect() {
        if (!connected && socket == null) {
            scope.launch { closeQuietly() }
            return
        }
        connected = false
        scope.launch {
            runCatching { sendEncrypted(Protocol.leave(nick)) }
            closeQuietly()
        }
    }

    private fun enqueue(payload: JsonObject) {
        scope.launch { runCatching { sendEncrypted(payload) } }
    }

    private suspend fun sendEncrypted(payload: JsonObject) {
        val s = socket ?: return
        val (nonce, ct) = crypto.encrypt(encode(payload))
        val envelope = buildJsonObject {
            put("type", "encrypted")
            put("nonce", nonce)
            put("ciphertext", ct)
        }
        sendMutex.withLock {
            Framing.writeMessage(s.getOutputStream(), encode(envelope))
        }
    }

    private fun closeQuietly() {
        runCatching { socket?.close() }
        socket = null
        crypto.wipe()
    }

    /** Fully tear down: stop the session and cancel all coroutines. */
    fun shutdown() {
        connected = false
        closeQuietly()
        scope.cancel()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun emit(type: String) = onEvent(buildJsonObject { put("type", type) })
    private fun encode(obj: JsonObject): ByteArray = json.encodeToString(JsonObject.serializer(), obj).toByteArray(Charsets.UTF_8)
    private fun decode(bytes: ByteArray): JsonObject = json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw IOException("missing field: $key")

    companion object {
        private const val CONNECT_TIMEOUT_MS = 120_000
        /** Keepalive cadence — comfortably below the host's idle read timeout. */
        private const val PING_INTERVAL_MS = 30_000L
    }
}
