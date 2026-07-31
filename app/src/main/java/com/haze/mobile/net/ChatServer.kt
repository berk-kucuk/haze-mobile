package com.haze.mobile.net

import com.haze.mobile.crypto.PasswordHash
import com.haze.mobile.crypto.SessionCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Host-mode TCP server — the Kotlin twin of `network/server.py`.
 *
 * Listens on a local port that the embedded Tor daemon publishes as a v3 onion
 * hidden service. Performs the X25519 handshake with each connecting client,
 * shares one group session key, and relays/broadcasts encrypted frames.
 *
 * Synthetic events emitted via [onEvent] mirror the client's:
 *   chat · join · leave · typing (plus the host's own echoed chat).
 */
class ChatServer(
    private val hostNick: String,
    private val localPort: Int,
    sessionPassword: String,
    private val onEvent: (JsonObject) -> Unit,
) {
    // One shared session key for the whole group (mirrors server.py).
    private val crypto = SessionCrypto().apply { generateSessionKey() }
    private val passwordHash = PasswordHash.hash(sessionPassword)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clients = ConcurrentHashMap<String, ClientConn>()
    @Volatile private var serverSocket: ServerSocket? = null

    private class ClientConn(val socket: Socket, val out: OutputStream) {
        val sendMutex = Mutex()
    }

    fun start() {
        scope.launch {
            try {
                val ss = ServerSocket()
                ss.bind(InetSocketAddress("127.0.0.1", localPort))
                serverSocket = ss
                while (true) {
                    val socket = ss.accept()
                    scope.launch { handleClient(socket) }
                }
            } catch (_: Exception) {
                // server closed
            }
        }
    }

    // ── Host-originated sends ────────────────────────────────────────────

    fun sendChat(content: String, replyToNick: String? = null, replyToContent: String? = null,
                 disappearSecs: Int = 0): String {
        val msgId = UUID.randomUUID().toString()
        val payload = Protocol.chat(hostNick, content, msgId, replyToNick, replyToContent, disappearSecs)
        onEvent(payload)                 // local echo for the host UI
        scope.launch { broadcast(payload) }
        return msgId
    }

    fun sendTyping(isTyping: Boolean) {
        scope.launch { broadcast(Protocol.typing(hostNick, isTyping)) }
    }

    fun sendDelete(msgId: String) {
        val payload = Protocol.delete(hostNick, msgId)
        onEvent(payload)
        scope.launch { broadcast(payload) }
    }

    fun sendEdit(msgId: String, content: String) {
        val payload = Protocol.edit(hostNick, msgId, content)
        onEvent(payload)
        scope.launch { broadcast(payload) }
    }

    fun sendPanic() {
        scope.launch { broadcast(Protocol.panic(hostNick)) }
    }

    /** Host kicks a participant: tell them they're removed, then drop the connection. */
    fun kickClient(nick: String) {
        scope.launch {
            val conn = clients[nick] ?: return@launch
            runCatching { sendEncrypted(conn, Protocol.kicked()) }
            disconnect(nick)   // closes socket + broadcasts leave to everyone
        }
    }

    /** Stream a file to every client (and echo to the host UI via onEvent). */
    fun sendFile(fileId: String, filename: String, mime: String, data: ByteArray) {
        scope.launch {
            val totalChunks = (data.size + Protocol.CHUNK_SIZE - 1) / Protocol.CHUNK_SIZE
            val start = Protocol.fileStart(hostNick, fileId, filename, mime, data.size, totalChunks)
            onEvent(start); broadcast(start)
            var i = 0
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + Protocol.CHUNK_SIZE, data.size)
                val b64 = android.util.Base64.encodeToString(
                    data.copyOfRange(offset, end), android.util.Base64.NO_WRAP
                )
                val chunk = Protocol.fileChunk(hostNick, fileId, i, b64)
                onEvent(chunk); broadcast(chunk)
                offset = end; i++
            }
            val fin = Protocol.fileEnd(hostNick, fileId)
            onEvent(fin); broadcast(fin)
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        clients.values.forEach { runCatching { it.socket.close() } }
        clients.clear()
        crypto.wipe()
        scope.cancel()
    }

    // ── Web (Tor Browser) bridge ─────────────────────────────────────────
    //
    // Mirrors server.py's receive_web_chat / receive_web_typing. Browser
    // clients don't run the X25519 handshake — WebChatServer terminates their
    // plaintext-JSON WebSocket locally and feeds messages in through here, so
    // to native TCP clients and the host UI they look like any other peer.

    /** Nicks of the currently connected native TCP clients (host nick excluded). */
    val clientNicks: Set<String> get() = clients.keys.toSet()

    /** AES-GCM key browser clients share — see [SessionCrypto.webTrafficKey]. */
    fun webTrafficKey(): ByteArray = crypto.webTrafficKey()

    /** Accept a chat from a browser client: echo to the host UI, relay to TCP clients. */
    suspend fun receiveWebChat(
        nick: String,
        content: String,
        msgId: String,
        replyToNick: String? = null,
        replyToContent: String? = null,
    ) {
        val payload = Protocol.chat(nick, content, msgId, replyToNick, replyToContent)
        onEvent(payload)
        broadcast(payload)
    }

    /**
     * Relay one file frame (start/chunk/end) from a browser client.
     *
     * The nick is stamped by the caller from the authenticated session, so a
     * browser cannot attribute a transfer to someone else; size and chunk
     * limits are applied there too.
     */
    suspend fun receiveWebFile(payload: JsonObject) {
        onEvent(payload)
        broadcast(payload)
    }

    /** Edit requested by a browser client, attributed to that client's nick. */
    suspend fun receiveWebEdit(nick: String, msgId: String, content: String) {
        val payload = Protocol.edit(nick, msgId, content)
        onEvent(payload)
        broadcast(payload)
    }

    /**
     * Delete requested by a browser client, attributed to that client's nick.
     *
     * Separate from the host's own delete: a web guest removing its own message
     * must not look to the room like the host did it.
     */
    suspend fun receiveWebDelete(nick: String, msgId: String) {
        val payload = Protocol.delete(nick, msgId)
        onEvent(payload)
        broadcast(payload)
    }

    /** Accept a typing notice from a browser client. */
    suspend fun receiveWebTyping(nick: String, isTyping: Boolean) {
        val payload = Protocol.typing(nick, isTyping)
        onEvent(payload)
        broadcast(payload)
    }

    /** Relay a web-originated join/leave to native TCP clients. */
    suspend fun relayToClients(payload: JsonObject) = broadcast(payload)

    // ── Client handling ──────────────────────────────────────────────────

    private suspend fun handleClient(socket: Socket) {
        var nick: String? = null
        try {
            val inp = socket.getInputStream()
            val out = socket.getOutputStream()

            // Step 1: hello — bounded the same way desktop's server.py bounds
            // its initial recv_msg (asyncio.wait_for(..., timeout=30)): a
            // connecting peer that never sends hello would otherwise tie up
            // this accept loop's thread indefinitely. Only the handshake read
            // gets this timeout; the main read loop below reverts to blocking
            // (a live session has no such deadline).
            socket.soTimeout = HELLO_TIMEOUT_MS
            val hello = decode(Framing.readMessage(inp))
            socket.soTimeout = 0
            if (hello["type"]?.jsonPrimitive?.content != "hello") {
                runCatching { socket.close() }; return
            }
            val clientPub = hello.str("public_key")
            var n = sanitizeNick(hello["nick"]?.jsonPrimitive?.content ?: "anon")

            // Step 1b: password check
            if (passwordHash.isNotEmpty() &&
                hello["password_hash"]?.jsonPrimitive?.content != passwordHash
            ) {
                Framing.writeMessage(out, encode(Protocol.authFailed()))
                runCatching { socket.close() }; return
            }

            // Deduplicate nick
            if (clients.containsKey(n) || n == hostNick) n = "${n}_${clients.size + 1}"
            nick = n

            // Step 2: wrap + send welcome
            val (nonce, ct) = crypto.wrapSessionKey(clientPub)
            Framing.writeMessage(out, encode(Protocol.welcome(crypto.publicKeyB64, nonce, ct)))

            // Step 3: current userlist (encrypted)
            val conn = ClientConn(socket, out)
            val users = listOf(hostNick) + clients.keys.toList()
            sendEncrypted(conn, Protocol.userlist(users))

            // Register
            clients[n] = conn

            // Step 4: broadcast join
            val joinP = Protocol.join(n)
            onEvent(joinP)
            broadcast(joinP)

            // Step 5: read loop
            while (true) {
                val frame = decode(Framing.readMessage(inp))
                if (frame["type"]?.jsonPrimitive?.content != "encrypted") continue
                val inner = decode(crypto.decrypt(frame.str("nonce"), frame.str("ciphertext")))
                when (inner["type"]?.jsonPrimitive?.content) {
                    "chat", "typing", "delete", "edit", "file_start", "file_chunk", "file_end" -> {
                        val m = withNick(inner, n)
                        onEvent(m)
                        broadcast(m, exclude = n)
                    }
                    "ping" -> {
                        val ts = inner["ts"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                        sendEncrypted(conn, Protocol.pong(ts))
                    }
                    "leave" -> { disconnect(n); return }
                    "panic" -> {
                        val m = withNick(inner, n)
                        onEvent(m)
                        broadcast(m)
                        return
                    }
                }
            }
        } catch (_: Exception) {
            // client dropped
        } finally {
            nick?.let { disconnect(it) }
        }
    }

    private suspend fun broadcast(payload: JsonObject, exclude: String? = null) {
        val dead = mutableListOf<String>()
        for ((nick, conn) in clients) {
            if (nick == exclude) continue
            try {
                sendEncrypted(conn, payload)
            } catch (_: Exception) {
                dead += nick
            }
        }
        dead.forEach { disconnect(it) }
    }

    private suspend fun sendEncrypted(conn: ClientConn, payload: JsonObject) {
        val (nonce, ct) = crypto.encrypt(encode(payload))
        val envelope = Protocol.encrypted(nonce, ct)
        conn.sendMutex.withLock {
            Framing.writeMessage(conn.out, encode(envelope))
        }
    }

    private suspend fun disconnect(nick: String) {
        val conn = clients.remove(nick) ?: return
        runCatching { conn.socket.close() }
        val leaveP = Protocol.leave(nick)
        onEvent(leaveP)
        broadcast(leaveP)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun withNick(inner: JsonObject, nick: String): JsonObject =
        JsonObject(inner.toMutableMap().apply { put("nick", JsonPrimitive(nick)) })

    private fun encode(obj: JsonObject): ByteArray =
        json.encodeToString(JsonObject.serializer(), obj).toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray): JsonObject =
        json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as JsonObject

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw IOException("missing field: $key")

    private fun sanitizeNick(nick: String): String {
        val cleaned = nick.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(20)
        return cleaned.ifEmpty { "anon" }
    }

    companion object {
        /** Matches desktop server.py's asyncio.wait_for(recv_msg(reader), timeout=30) on the initial hello. */
        private const val HELLO_TIMEOUT_MS = 30_000
    }
}
