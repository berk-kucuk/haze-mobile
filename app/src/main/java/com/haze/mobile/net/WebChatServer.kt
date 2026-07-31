package com.haze.mobile.net

import android.content.Context
import com.haze.mobile.crypto.PasswordHash
import com.haze.mobile.crypto.WebE2E
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optional HTTP + WebSocket bridge that lets Tor Browser users join a
 * mobile-hosted Haze session — the Kotlin twin of `network/web_server.py`.
 *
 * Onion virtual port mapping (see [TorManager.startHiddenService]):
 *   .onion:80   → this server        (web browsers)
 *   .onion:5222 → [ChatServer] TCP   (native Haze clients)
 *
 * SECURITY: browser clients now run their own key exchange — X25519 (or ECDH
 * P-256 on browsers without it), HKDF-SHA256, AES-256-GCM — see [WebE2E]. They
 * cannot join the native ChaCha20-Poly1305 stream because WebCrypto has no such
 * cipher, so this host converts between the two. Only the browser's public key
 * crosses the socket in the clear; the nickname and session password travel
 * inside the encrypted channel.
 *
 * That keeps browser plaintext out of this server's HTTP layer, but it does NOT
 * make a browser guest as safe as the app: the page performing the encryption is
 * served by this host, so a compromised host could serve one that leaks the key.
 * An installed client runs code the host cannot change. Hence the feature stays
 * opt-in per `SettingsStore.allowWebAccess`.
 *
 * @param onRenewCircuit rotate Tor circuits (NEWNYM); null disables /api/renew.
 */
class WebChatServer(
    private val context: Context,
    private val hostNick: String,
    private val httpPort: Int,
    private val chatServer: ChatServer,
    private val onEvent: (JsonObject) -> Unit,
    sessionPassword: String = "",
    private val onRenewCircuit: (suspend () -> Unit)? = null,
) {
    private val passwordHash = PasswordHash.hash(sessionPassword)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Connected browser clients, keyed by their assigned "[web]…" nick. */
    private val wsClients = ConcurrentHashMap<String, io.ktor.websocket.WebSocketSession>()
    private val nickMutex = Mutex()

    @Volatile private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    /**
     * The chat UI page, read once from assets/web/index.html.
     *
     * Byte-identical to the desktop client's src/haze/assets/web/index.html —
     * one canonical page, so a guest gets the same product whichever platform
     * happens to be hosting.
     */
    private val indexHtml: String by lazy {
        context.assets.open("web/index.html").use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Wordmark the page shows on the join card and in the top bar. */
    private val logoBytes: ByteArray by lazy {
        context.assets.open("web/logo.png").use { it.readBytes() }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    fun start() {
        // Bind to loopback only — Tor is the sole path in, so the server must
        // never be reachable over the phone's Wi-Fi/mobile interfaces.
        val server = embeddedServer(CIO, port = httpPort, host = "127.0.0.1") {
            install(WebSockets) {
                // Keeps idle browser sockets (and their Tor circuit) alive.
                pingPeriodMillis = 30_000
                timeoutMillis = 60_000
            }
            routing {
                get("/") { call.respondText(indexHtml, ContentType.Text.Html) }
                get("/logo.png") {
                    // Its own route rather than a data: URI, so it is fetched
                    // once and cached instead of inflating every page load over
                    // a Tor circuit.
                    call.response.headers.append("Cache-Control", "public, max-age=86400")
                    call.respondBytes(logoBytes, ContentType.Image.PNG)
                }
                post("/api/renew") {
                    val renew = onRenewCircuit
                    if (renew == null) {
                        call.respondText("Circuit renewal not available", status = HttpStatusCode.ServiceUnavailable)
                    } else {
                        val res = runCatching { renew() }
                        if (res.isSuccess) call.respondText("ok")
                        else call.respondText(
                            res.exceptionOrNull()?.message ?: "error",
                            status = HttpStatusCode.InternalServerError,
                        )
                    }
                }
                webSocket("/ws") { handleWs() }
            }
        }
        engine = server
        server.start(wait = false)
    }

    fun stop() {
        wsClients.values.forEach { runCatching { it.cancel() } }
        wsClients.clear()
        runCatching { engine?.stop(0, 0) }
        engine = null
    }

    // ── WebSocket session ────────────────────────────────────────────────

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.handleWs() {
        var nick: String? = null
        // Set once the browser has completed the key exchange. Until then the
        // only frame accepted is "hello"; afterwards every frame in both
        // directions is an "encrypted" envelope.
        var trafficKey: ByteArray? = null
        // file_id → running totals for this connection's in-flight uploads.
        val uploads = HashMap<String, Upload>()

        suspend fun sendSecure(payload: JsonObject) {
            val key = trafficKey ?: return
            sendJson(WebE2E.encrypt(key, payload))
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                var data = runCatching { json.parseToJsonElement(frame.readText()) as JsonObject }
                    .getOrNull() ?: continue

                if (data["type"]?.jsonPrimitive?.content == "hello" && trafficKey == null) {
                    // Key exchange first, so the nickname and session password
                    // below never cross the socket in the clear.
                    val welcome = try {
                        val key = chatServer.webTrafficKey()
                        val w = WebE2E.wrapTrafficKey(
                            data["pubkey"]?.jsonPrimitive?.content ?: "",
                            data["alg"]?.jsonPrimitive?.content ?: "",
                            key,
                        )
                        trafficKey = key
                        w
                    } catch (e: Exception) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "handshake_failed"))
                        return
                    }
                    sendJson(buildJsonObject {
                        put("type", "welcome")
                        welcome.forEach { (k, v) -> put(k, v) }
                    })
                    continue
                }

                val key = trafficKey ?: run {
                    // Anything before the handshake is not part of the protocol.
                    close(CloseReason(CloseReason.Codes.NORMAL, "handshake_required"))
                    return
                }
                if (data["type"]?.jsonPrimitive?.content != "encrypted") continue
                data = try {
                    json.parseToJsonElement(WebE2E.decrypt(key, data)) as JsonObject
                } catch (e: Exception) {
                    close(CloseReason(CloseReason.Codes.NORMAL, "bad_frame"))
                    return
                }

                when (data["type"]?.jsonPrimitive?.content) {
                    "join" -> {
                        if (nick != null) continue
                        // Session-password gate before admitting the client.
                        if (passwordHash.isNotEmpty() &&
                            !constantTimeEquals(
                                data["password_hash"]?.jsonPrimitive?.content ?: "",
                                passwordHash,
                            )
                        ) {
                            sendSecure(Protocol.authFailed())
                            close(CloseReason(CloseReason.Codes.NORMAL, "auth_failed"))
                            return
                        }
                        val assigned = makeNick(data["nick"]?.jsonPrimitive?.content ?: "")
                        nick = assigned
                        wsClients[assigned] = this

                        // Current participants, for the newcomer's user list.
                        val users = listOf(hostNick) + chatServer.clientNicks + wsClients.keys
                        sendSecure(Protocol.userlist(users.distinct()))

                        // Announce to the host UI and native clients. Browser
                        // clients are reached through onEvent -> broadcastEvent
                        // (the host wires that up), so deliberately no direct
                        // broadcastWeb here — doing both would deliver join
                        // twice to every other browser.
                        val joinEv = Protocol.join(assigned)
                        onEvent(joinEv)
                        chatServer.relayToClients(joinEv)
                    }
                    "chat" -> {
                        val who = nick ?: continue
                        val content = (data["content"]?.jsonPrimitive?.content ?: "").take(4000)
                        val msgId = data["msg_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                            ?: UUID.randomUUID().toString()
                        // Reply metadata is quoted back to the whole room, so it
                        // is length-capped like the body. Every client renders
                        // both as text only.
                        val replyNick = data["reply_to_nick"]?.jsonPrimitive?.content
                            ?.takeIf { it.isNotBlank() }?.take(20)
                        val replyContent = data["reply_to_content"]?.jsonPrimitive?.content
                            ?.takeIf { it.isNotBlank() }?.take(200)
                        chatServer.receiveWebChat(who, content, msgId, replyNick, replyContent)
                    }
                    "file_start", "file_chunk", "file_end" -> {
                        val who = nick ?: continue
                        handleWebFile(
                            who, data["type"]!!.jsonPrimitive.content, data, uploads,
                        )
                    }
                    "edit" -> {
                        // Offered by the browser UI only on the guest's own
                        // messages; the nick is stamped here either way, exactly
                        // as it is for a native client's edit.
                        val who = nick ?: continue
                        val msgId = data["msg_id"]?.jsonPrimitive?.content
                            ?.takeIf { it.isNotBlank() }?.take(64) ?: continue
                        val content = (data["content"]?.jsonPrimitive?.content ?: "").take(4000)
                        if (content.isNotEmpty()) chatServer.receiveWebEdit(who, msgId, content)
                    }
                    "delete" -> {
                        // The browser UI only offers this on the guest's own
                        // messages. The event is stamped with the authenticated
                        // nick here, exactly as a native client's delete is, so
                        // a web guest gains nothing a native peer lacks.
                        val who = nick ?: continue
                        val msgId = data["msg_id"]?.jsonPrimitive?.content
                            ?.takeIf { it.isNotBlank() }?.take(64) ?: continue
                        chatServer.receiveWebDelete(who, msgId)
                    }
                    "typing" -> {
                        val who = nick ?: continue
                        val state = runCatching { data["state"]!!.jsonPrimitive.boolean }.getOrDefault(false)
                        chatServer.receiveWebTyping(who, state)
                    }
                }
            }
        } catch (_: Exception) {
            // browser closed / transport error
        } finally {
            val who = nick
            if (who != null && wsClients.remove(who) != null) {
                // Removed from wsClients first, so the onEvent -> broadcastEvent
                // fan-out below no longer targets this (now closed) session.
                val leaveEv = Protocol.leave(who)
                onEvent(leaveEv)
                runCatching { chatServer.relayToClients(leaveEv) }
            }
        }
    }

    // ── Uploads from browser clients ─────────────────────────────────────

    companion object {
        /** Concurrent uploads one browser may have open. */
        private const val MAX_UPLOADS_IN_FLIGHT = 4
        /** base64 of one CHUNK_SIZE block, plus slack for padding. */
        private const val MAX_CHUNK_B64 = Protocol.CHUNK_SIZE * 4 / 3 + 1024
    }

    /** Running totals for one in-flight upload. */
    private class Upload(val chunks: Int) {
        var bytes: Long = 0
        val seen = HashSet<Int>()
    }

    /**
     * Validate and relay one file frame from a browser.
     *
     * Every limit is counted from what actually arrives rather than trusted
     * from the sender's header — a declared one-chunk file must not be able to
     * be followed by unlimited chunks.
     */
    private suspend fun handleWebFile(
        nick: String,
        type: String,
        data: JsonObject,
        uploads: HashMap<String, Upload>,
    ) {
        val fileId = data["file_id"]?.jsonPrimitive?.content?.take(64)?.takeIf { it.isNotBlank() }
            ?: return

        if (type == "file_start") {
            if (uploads.size >= MAX_UPLOADS_IN_FLIGHT) return
            val totalSize = data["total_size"]?.jsonPrimitive?.intOrNull ?: return
            val totalChunks = data["total_chunks"]?.jsonPrimitive?.intOrNull ?: return
            if (totalChunks !in 1..Protocol.MAX_FILE_CHUNKS) return
            if (totalSize !in 1..Protocol.MAX_FILE_BYTES) return
            // The filename is displayed and offered as a download by every
            // client; strip anything that could escape a directory when saved.
            val filename = (data["filename"]?.jsonPrimitive?.content ?: "file")
                .take(120).replace('\\', '/').substringAfterLast('/').trim().ifEmpty { "file" }
            val mime = (data["mime"]?.jsonPrimitive?.content ?: "application/octet-stream").take(100)
            uploads[fileId] = Upload(totalChunks)
            chatServer.receiveWebFile(
                Protocol.fileStart(nick, fileId, filename, mime, totalSize, totalChunks)
            )
            return
        }

        val upload = uploads[fileId] ?: return

        if (type == "file_chunk") {
            val index = data["chunk_index"]?.jsonPrimitive?.intOrNull ?: return
            if (index < 0 || index >= upload.chunks || !upload.seen.add(index)) return
            val chunk = data["data"]?.jsonPrimitive?.content ?: return
            if (chunk.length > MAX_CHUNK_B64) { uploads.remove(fileId); return }
            upload.bytes += chunk.length * 3L / 4L
            if (upload.bytes > Protocol.MAX_FILE_BYTES) { uploads.remove(fileId); return }
            chatServer.receiveWebFile(Protocol.fileChunk(nick, fileId, index, chunk))
            return
        }

        uploads.remove(fileId)
        chatServer.receiveWebFile(Protocol.fileEnd(nick, fileId))
    }

    // ── Broadcasting ─────────────────────────────────────────────────────

    /**
     * Forward an event that originated on the host/TCP side to every browser
     * client. Called for each event the host UI sees, mirroring desktop's
     * _NetBridge, so browsers observe the same stream native clients do.
     */
    suspend fun broadcastEvent(payload: JsonObject) = broadcastWeb(payload)

    private suspend fun broadcastWeb(payload: JsonObject, exclude: String? = null) {
        if (wsClients.isEmpty()) return
        // Browser clients share one traffic key, so the envelope is built once
        // and the same ciphertext goes to every one of them.
        val envelope = try {
            WebE2E.encrypt(chatServer.webTrafficKey(), payload)
        } catch (e: Exception) {
            return
        }
        val dead = mutableListOf<String>()
        for ((n, session) in wsClients) {
            if (n == exclude) continue
            try {
                session.sendJson(envelope)
            } catch (_: ClosedSendChannelException) {
                dead += n
            } catch (_: Exception) {
                dead += n
            }
        }
        dead.forEach { wsClients.remove(it) }
    }

    private suspend fun io.ktor.websocket.WebSocketSession.sendJson(payload: JsonObject) {
        send(Frame.Text(json.encodeToString(JsonObject.serializer(), payload)))
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Sanitize a requested nick and make it unique, tagging it "[web]" so a
     * browser participant is always distinguishable from a native one (the
     * bracket characters are outside the native clients' allowed nick set, so
     * a TCP peer cannot impersonate a web user or vice versa).
     */
    private suspend fun makeNick(raw: String): String = nickMutex.withLock {
        val sanitized = raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(20)
        val base = "[web]" + sanitized.ifEmpty { "user" }
        val taken = wsClients.keys + chatServer.clientNicks + hostNick
        var candidate = base
        var i = 2
        while (candidate in taken) {
            candidate = "${base}_$i"
            i++
        }
        candidate
    }

    /** Length-independent comparison so the password check can't be timed. */
    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
