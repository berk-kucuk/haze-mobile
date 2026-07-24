package com.haze.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haze.mobile.net.ChatClient
import com.haze.mobile.net.ChatServer
import com.haze.mobile.net.TorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class Screen { Connect, Chat, Vault }

data class ChatMessage(
    val nick: String,
    val content: String,
    val isMe: Boolean = false,
    val isSystem: Boolean = false,
    val msgId: String? = null,
    // File transfer
    val isFile: Boolean = false,
    val fileId: String? = null,
    val filename: String? = null,
    val mime: String? = null,
    val totalSize: Int = 0,
    val received: Int = 0,
    val fileData: ByteArray? = null,
    val timestamp: String = java.util.Calendar.getInstance().let {
        String.format("%02d:%02d", it.get(java.util.Calendar.HOUR_OF_DAY), it.get(java.util.Calendar.MINUTE))
    },
) {
    val fileReady: Boolean get() = fileData != null
}

/** Reassembly buffer for an incoming file transfer. */
private class FileBuffer(
    val filename: String,
    val mime: String,
    val totalSize: Int,
    val totalChunks: Int,
) {
    val chunks = arrayOfNulls<ByteArray>(totalChunks.coerceAtLeast(1))
    var receivedBytes = 0
}

/** One-line summary of a session for the switcher UI. */
data class SessionSummary(
    val id: String,
    val label: String,
    val isHost: Boolean,
    val connected: Boolean,
    val active: Boolean,
)

data class ChatUiState(
    val screen: Screen = Screen.Connect,
    // ── Active session mirror ──
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val myNick: String = "",
    val hostOnion: String = "",
    val isHost: Boolean = false,
    val participants: List<String> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val typingUsers: List<String> = emptyList(),
    val blockedUsers: Set<String> = emptySet(),
    // ── Sessions ──
    val sessions: List<SessionSummary> = emptyList(),
    val addingSession: Boolean = false,   // Connect screen shown on top of existing sessions
    // ── Vault ──
    val vaultSessions: List<com.haze.mobile.storage.VaultStore.Entry> = emptyList(),
    val vaultOpenMessages: List<ChatMessage>? = null,
    val vaultOpenName: String = "",
    val vaultError: String? = null,
    val vaultReturnTo: Screen = Screen.Connect,
)

/** All network + UI state for one active chat connection (host or join). */
private class SessionState(
    val id: String,
    val isHost: Boolean,
    val nick: String,
) {
    var hostOnion: String = ""
    var connecting: Boolean = true
    var connected: Boolean = false
    var status: String = ""
    var error: String? = null
    var messages: List<ChatMessage> = emptyList()
    var participants: List<String> = emptyList()
    var typingUsers: List<String> = emptyList()
    var client: ChatClient? = null
    var server: ChatServer? = null
    val fileBuffers = ConcurrentHashMap<String, FileBuffer>()
    val blocked = mutableSetOf<String>()   // nicks muted client-side

    val label: String get() = "${if (isHost) "HOST" else "JOIN"} · $nick"

    fun teardown() {
        runCatching { client?.shutdown() }
        runCatching { server?.stop() }
        client = null
        server = null
        fileBuffers.clear()
    }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private val sessions = LinkedHashMap<String, SessionState>()
    private var activeId: String? = null

    private fun active(): SessionState? = activeId?.let { sessions[it] }

    // ── UI projection ────────────────────────────────────────────────────

    private fun summaries(): List<SessionSummary> = sessions.values.map {
        SessionSummary(it.id, it.label, it.isHost, it.connected, it.id == activeId)
    }

    /** Rebuild the visible UI from the active session (+ session list). */
    private fun rebuildUi(screen: Screen? = null) {
        val a = active()
        _ui.update { cur ->
            if (a == null) {
                cur.copy(
                    screen = screen ?: cur.screen,
                    connecting = false, connected = false, status = "", error = null,
                    myNick = "", hostOnion = "", isHost = false,
                    participants = emptyList(), messages = emptyList(), typingUsers = emptyList(),
                    sessions = summaries(),
                )
            } else {
                cur.copy(
                    screen = screen ?: cur.screen,
                    connecting = a.connecting, connected = a.connected, status = a.status, error = a.error,
                    myNick = a.nick, hostOnion = a.hostOnion, isHost = a.isHost,
                    participants = a.participants, messages = a.messages, typingUsers = a.typingUsers,
                    blockedUsers = a.blocked.toSet(),
                    sessions = summaries(),
                )
            }
        }
    }

    private fun touch(s: SessionState) {
        if (s.id == activeId) rebuildUi()
    }

    // ── Session lifecycle ────────────────────────────────────────────────

    /** Show the Connect screen to add another session (keeps existing ones running). */
    fun newSession() {
        _ui.update { it.copy(screen = Screen.Connect, addingSession = sessions.isNotEmpty(), error = null) }
    }

    /** Cancel adding a session and return to the active chat (if any). */
    fun cancelAdd() {
        if (sessions.isEmpty()) return
        _ui.update { it.copy(addingSession = false) }
        rebuildUi(Screen.Chat)
    }

    fun switchSession(id: String) {
        if (!sessions.containsKey(id)) return
        activeId = id
        _ui.update { it.copy(addingSession = false) }
        rebuildUi(Screen.Chat)
    }

    /** Leave (close) the active session. */
    fun leaveActive() {
        val a = active() ?: return
        a.teardown()
        sessions.remove(a.id)
        activeId = sessions.keys.lastOrNull()
        if (activeId == null) {
            _ui.value = ChatUiState()  // back to a fresh Connect screen
        } else {
            rebuildUi(Screen.Chat)
        }
    }

    private fun nick(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(20)

    fun connect(onion: String, nickRaw: String, password: String) {
        val cleanNick = nick(nickRaw)
        if (cleanNick.isEmpty()) { _ui.update { it.copy(error = "Nickname: letters, numbers, _ or - only") }; return }
        if (!onion.trim().endsWith(".onion")) { _ui.update { it.copy(error = ".onion address required") }; return }

        val s = SessionState(UUID.randomUUID().toString(), isHost = false, nick = cleanNick)
        s.hostOnion = onion.trim().removeSuffix("/")
        s.status = "Starting Tor…"
        sessions[s.id] = s
        activeId = s.id
        // Stay on the Connect screen showing progress until the handshake succeeds.
        _ui.update { it.copy(connecting = true, status = s.status, error = null, addingSession = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                TorManager.init(getApplication()) { pct -> if (!s.connected) setConnectProgress(s, "Bootstrapping Tor… $pct%") }
                val socksPort = TorManager.start()
                setConnectProgress(s, "Connecting to host…")
                val c = ChatClient(s.hostOnion, cleanNick, password, TorManager.LOOPBACK, socksPort) { ev -> handleEvent(s.id, ev) }
                s.client = c
                c.start()
            } catch (e: Exception) {
                s.teardown(); sessions.remove(s.id)
                if (activeId == s.id) activeId = sessions.keys.lastOrNull()
                _ui.update { it.copy(connecting = false, error = "Tor failed to start: ${e.message ?: "unknown"}") }
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
            }
        }
    }

    /** Update the progress spinner while a not-yet-active session is connecting. */
    private fun setConnectProgress(s: SessionState, msg: String) {
        s.status = msg
        if (s.id == activeId && !s.connected) {
            _ui.update { it.copy(connecting = true, status = msg) }
        }
    }

    fun host(nickRaw: String, password: String) {
        val cleanNick = nick(nickRaw)
        if (cleanNick.isEmpty()) { _ui.update { it.copy(error = "Nickname: letters, numbers, _ or - only") }; return }

        val s = SessionState(UUID.randomUUID().toString(), isHost = true, nick = cleanNick)
        s.status = "Starting Tor…"
        s.participants = listOf(cleanNick)
        sessions[s.id] = s
        activeId = s.id
        _ui.update { it.copy(connecting = true, status = s.status, error = null, addingSession = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                TorManager.init(getApplication()) { pct -> if (!s.connected) setConnectProgress(s, "Bootstrapping Tor… $pct%") }
                val localPort = (40_000..60_000).random()
                val srv = ChatServer(cleanNick, localPort, password) { ev -> handleEvent(s.id, ev) }
                s.server = srv
                srv.start()
                setConnectProgress(s, "Publishing onion service…")
                val onion = TorManager.startHiddenService(localPort)
                s.hostOnion = onion; s.connecting = false; s.connected = true; s.status = "Hosting"
                // Handshake done → enter the chat (or update the tab if not active).
                if (s.id == activeId) { _ui.update { it.copy(addingSession = false) }; rebuildUi(Screen.Chat) } else touch(s)
            } catch (e: Exception) {
                s.teardown()
                sessions.remove(s.id)
                if (activeId == s.id) activeId = sessions.keys.lastOrNull()
                _ui.update { it.copy(error = "Failed to host: ${e.message ?: "unknown"}") }
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
            }
        }
    }

    // ── Sending (operate on the active session) ─────────────────────────

    fun sendChat(text: String) {
        val content = text.trim(); if (content.isEmpty()) return
        val s = active() ?: return
        s.server?.let { it.sendChat(content); return }
        val c = s.client ?: return
        val msgId = c.sendChat(content)
        s.messages = s.messages + ChatMessage(nick = s.nick, content = content, isMe = true, msgId = msgId)
        touch(s)
    }

    fun setTyping(isTyping: Boolean) {
        val s = active() ?: return
        s.server?.sendTyping(isTyping) ?: s.client?.sendTyping(isTyping)
    }

    /** Host removes a participant from the active hosted room. */
    fun kickUser(nick: String) {
        active()?.server?.kickClient(nick)
    }

    /** Mute/unmute a participant on our side only (does not affect others). */
    fun toggleBlock(nick: String) {
        val s = active() ?: return
        if (nick in s.blocked) s.blocked.remove(nick) else s.blocked.add(nick)
        touch(s)
    }

    fun panic() {
        sessions.values.forEach { s -> runCatching { s.server?.sendPanic() }; runCatching { s.client?.sendPanic() } }
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            sessions.values.forEach { it.teardown() }
            sessions.clear()
            activeId = null
            _ui.value = ChatUiState()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun sendFile(uri: android.net.Uri) {
        val s = active() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val data = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val name = queryDisplayName(uri) ?: "file"
                sendBytesInternal(s, data, name, mime)
            } catch (_: Exception) { s.error = "Failed to send file"; touch(s) }
        }
    }

    fun sendFileBytes(data: ByteArray, filename: String, mime: String) {
        val s = active() ?: return
        viewModelScope.launch(Dispatchers.IO) { sendBytesInternal(s, data, filename, mime) }
    }

    private fun sendBytesInternal(s: SessionState, data: ByteArray, filename: String, mime: String) {
        if (data.isEmpty()) return
        if (data.size > MAX_FILE_BYTES) { s.error = "File too large (max 100 MB)"; touch(s); return }
        val fileId = UUID.randomUUID().toString()
        val srv = s.server
        if (srv != null) {
            srv.sendFile(fileId, filename, mime, data)
        } else {
            val c = s.client ?: return
            c.sendFile(fileId, filename, mime, data)
            s.messages = s.messages + ChatMessage(
                nick = s.nick, content = "", isMe = true, isFile = true,
                fileId = fileId, filename = filename, mime = mime,
                totalSize = data.size, received = data.size, fileData = data,
            )
            touch(s)
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? = try {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    override fun onCleared() {
        sessions.values.forEach { it.teardown() }
        sessions.clear()
    }

    // ── Event handling (per-session) ─────────────────────────────────────

    private fun handleEvent(sid: String, ev: JsonObject) {
        val s = sessions[sid] ?: return
        when (ev["type"]?.jsonPrimitive?.content) {
            "connected" -> {
                s.connecting = false; s.connected = true; s.status = "Connected"; s.error = null
                // Handshake done → enter the chat (or just refresh the tab if not active).
                if (s.id == activeId) { _ui.update { it.copy(addingSession = false) }; rebuildUi(Screen.Chat) } else touch(s)
                return
            }
            "auth_failed" -> {
                s.teardown(); sessions.remove(sid)
                if (activeId == sid) activeId = sessions.keys.lastOrNull()
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
                _ui.update { it.copy(connecting = false, error = "Wrong session password") }
                return
            }
            "disconnected" -> {
                if (!s.connected) {
                    // Never completed the handshake → the connection attempt failed.
                    s.teardown(); sessions.remove(sid)
                    if (activeId == sid) activeId = sessions.keys.lastOrNull()
                    rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
                    _ui.update { it.copy(connecting = false, error = "Could not reach host over Tor") }
                    return
                }
                s.connecting = false; s.connected = false; s.status = "Disconnected"
                s.messages = s.messages + ChatMessage("", "Disconnected.", isSystem = true)
            }
            "userlist" -> {
                s.participants = ev["users"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            }
            "join" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                if (n !in s.participants) s.participants = s.participants + n
                s.messages = s.messages + ChatMessage("", "$n joined", isSystem = true)
            }
            "leave" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                s.participants = s.participants.filterNot { it == n }
                s.typingUsers = s.typingUsers.filterNot { it == n }
                s.messages = s.messages + ChatMessage("", "$n left", isSystem = true)
            }
            "chat" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: "?"
                if (n in s.blocked) return          // muted user → drop message
                val content = ev["content"]?.jsonPrimitive?.content ?: ""
                val msgId = ev["msg_id"]?.jsonPrimitive?.content
                s.typingUsers = s.typingUsers.filterNot { it == n }
                s.messages = s.messages + ChatMessage(n, content, isMe = n == s.nick, msgId = msgId)
            }
            "typing" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                val state = ev["state"]?.jsonPrimitive?.content == "true"
                if (n != s.nick && n !in s.blocked) {
                    s.typingUsers = if (state) (s.typingUsers + n).distinct() else s.typingUsers.filterNot { it == n }
                }
            }
            "file_start" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                if (n in s.blocked) return          // muted user → drop file
                val fileId = ev["file_id"]?.jsonPrimitive?.content ?: return
                val filename = ev["filename"]?.jsonPrimitive?.content ?: "file"
                val mime = ev["mime"]?.jsonPrimitive?.content ?: "application/octet-stream"
                val totalSize = ev["total_size"]?.jsonPrimitive?.intOrNull ?: 0
                val totalChunks = ev["total_chunks"]?.jsonPrimitive?.intOrNull ?: 0
                if (totalSize > MAX_FILE_BYTES || totalChunks > MAX_FILE_CHUNKS) return
                s.fileBuffers[fileId] = FileBuffer(filename, mime, totalSize, totalChunks)
                s.typingUsers = s.typingUsers.filterNot { it == n }
                s.messages = s.messages + ChatMessage(
                    n, "", isMe = n == s.nick, isFile = true, fileId = fileId,
                    filename = filename, mime = mime, totalSize = totalSize,
                )
            }
            "file_chunk" -> {
                val fileId = ev["file_id"]?.jsonPrimitive?.content ?: return
                val idx = ev["chunk_index"]?.jsonPrimitive?.intOrNull ?: return
                val dataB64 = ev["data"]?.jsonPrimitive?.content ?: return
                val buf = s.fileBuffers[fileId] ?: return
                if (idx < 0 || idx >= buf.chunks.size) return
                if (buf.chunks[idx] == null) {
                    val bytes = android.util.Base64.decode(dataB64, android.util.Base64.DEFAULT)
                    buf.chunks[idx] = bytes
                    buf.receivedBytes += bytes.size
                }
                s.messages = s.messages.map { if (it.fileId == fileId) it.copy(received = buf.receivedBytes) else it }
            }
            "file_end" -> {
                val fileId = ev["file_id"]?.jsonPrimitive?.content ?: return
                val buf = s.fileBuffers.remove(fileId) ?: return
                val assembled = java.io.ByteArrayOutputStream().use { bos ->
                    buf.chunks.forEach { c -> if (c != null) bos.write(c) }
                    bos.toByteArray()
                }
                s.messages = s.messages.map { if (it.fileId == fileId) it.copy(fileData = assembled, received = assembled.size) else it }
            }
            "panic" -> {
                s.messages = s.messages + ChatMessage("", "⚠ Session ended.", isSystem = true)
                s.client?.disconnect()
            }
            "kicked" -> {
                s.messages = s.messages + ChatMessage("", "You were removed.", isSystem = true)
                s.client?.disconnect()
            }
        }
        touch(s)
    }

    // ── Vault ────────────────────────────────────────────────────────────

    fun saveToVault(password: String) {
        if (password.isBlank()) return
        val s = active() ?: return
        val name = s.nick.ifEmpty { "chat" }
        val jsonStr = messagesToJson(name, s.messages)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                com.haze.mobile.storage.VaultStore.saveSession(getApplication(), password, s.id, name, jsonStr)
            }.onSuccess {
                s.status = "Saved to vault"; touch(s)
            }.onFailure {
                s.error = "Vault save failed"; touch(s)
            }
        }
    }

    fun openVault(from: Screen) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = com.haze.mobile.storage.VaultStore.listSessions(getApplication())
            _ui.update { it.copy(screen = Screen.Vault, vaultReturnTo = from, vaultSessions = list, vaultOpenMessages = null, vaultError = null) }
        }
    }

    fun loadVaultSession(entry: com.haze.mobile.storage.VaultStore.Entry, password: String) {
        if (password.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = com.haze.mobile.storage.VaultStore.loadSession(password, entry.path)
                val (name, msgs) = jsonToMessages(jsonStr)
                _ui.update { it.copy(vaultOpenMessages = msgs, vaultOpenName = name, vaultError = null) }
            } catch (_: Exception) {
                _ui.update { it.copy(vaultError = "Wrong password") }
            }
        }
    }

    fun deleteVaultSession(entry: com.haze.mobile.storage.VaultStore.Entry) {
        com.haze.mobile.storage.VaultStore.deleteSession(entry.path)
        viewModelScope.launch(Dispatchers.IO) {
            val list = com.haze.mobile.storage.VaultStore.listSessions(getApplication())
            _ui.update { it.copy(vaultSessions = list) }
        }
    }

    fun closeVaultSession() = _ui.update { it.copy(vaultOpenMessages = null, vaultError = null) }
    fun clearVaultError() = _ui.update { it.copy(vaultError = null) }
    fun exitVault() = _ui.update { it.copy(screen = it.vaultReturnTo, vaultOpenMessages = null, vaultError = null) }

    private fun messagesToJson(name: String, messages: List<ChatMessage>): String {
        val arr = kotlinx.serialization.json.buildJsonArray {
            messages.forEach { m ->
                add(kotlinx.serialization.json.buildJsonObject {
                    put("nick", m.nick)
                    put("isMe", m.isMe)
                    put("isSystem", m.isSystem)
                    put("timestamp", m.timestamp)
                    if (m.isFile && m.fileData != null) {
                        // Persist the actual file (image / voice note / document) so it
                        // can be viewed again after unlocking the vault.
                        put("isFile", true)
                        put("filename", m.filename ?: "file")
                        put("mime", m.mime ?: "application/octet-stream")
                        put("fileData", android.util.Base64.encodeToString(m.fileData, android.util.Base64.NO_WRAP))
                        put("content", "")
                    } else if (m.isFile) {
                        // File never finished downloading — keep a placeholder only.
                        put("content", when {
                            m.mime?.startsWith("image/") == true -> "[Image]"
                            m.mime?.startsWith("audio/") == true -> "[Voice note]"
                            else -> "[File: ${m.filename ?: "file"}]"
                        })
                    } else {
                        put("content", m.content)
                    }
                })
            }
        }
        return kotlinx.serialization.json.buildJsonObject {
            put("name", name)
            put("messages", arr)
        }.toString()
    }

    private fun jsonToMessages(jsonStr: String): Pair<String, List<ChatMessage>> {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(jsonStr).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "session"
        val msgs = root["messages"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            val isFile = o["isFile"]?.jsonPrimitive?.content == "true"
            if (isFile) {
                val b64 = o["fileData"]?.jsonPrimitive?.content
                val data = b64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
                ChatMessage(
                    nick = o["nick"]?.jsonPrimitive?.content ?: "",
                    content = "",
                    isMe = o["isMe"]?.jsonPrimitive?.content == "true",
                    timestamp = o["timestamp"]?.jsonPrimitive?.content ?: "",
                    isFile = true,
                    filename = o["filename"]?.jsonPrimitive?.content,
                    mime = o["mime"]?.jsonPrimitive?.content,
                    fileData = data,
                    totalSize = data?.size ?: 0,
                    received = data?.size ?: 0,
                )
            } else {
                ChatMessage(
                    nick = o["nick"]?.jsonPrimitive?.content ?: "",
                    content = o["content"]?.jsonPrimitive?.content ?: "",
                    isMe = o["isMe"]?.jsonPrimitive?.content == "true",
                    isSystem = o["isSystem"]?.jsonPrimitive?.content == "true",
                    timestamp = o["timestamp"]?.jsonPrimitive?.content ?: "",
                )
            }
        } ?: emptyList()
        return name to msgs
    }

    companion object {
        private const val MAX_FILE_BYTES = 100 * 1024 * 1024
        private const val MAX_FILE_CHUNKS = 512
    }
}
