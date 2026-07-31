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

enum class Screen { Connect, Chat, Vault, Settings }

data class ChatMessage(
    val nick: String,
    val content: String,
    val isMe: Boolean = false,
    val isSystem: Boolean = false,
    val msgId: String? = null,
    val deleted: Boolean = false,
    val edited: Boolean = false,
    val disappearSecs: Int = 0,
    // Reply / quote
    val replyToNick: String? = null,
    val replyToContent: String? = null,
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
    val latencyMs: Int? = null,
    val pendingPanicNick: String? = null,
    // ── Sessions ──
    val sessions: List<SessionSummary> = emptyList(),
    val addingSession: Boolean = false,   // Connect screen shown on top of existing sessions
    // ── Vault ──
    val vaultSessions: List<com.haze.mobile.storage.VaultStore.Entry> = emptyList(),
    val vaultOpenMessages: List<ChatMessage>? = null,
    val vaultOpenName: String = "",
    val vaultError: String? = null,
    val vaultReturnTo: Screen = Screen.Connect,
    /** True while the vault-lock password prompt is showing (settings.vaultLockHash is set). */
    val vaultLocked: Boolean = false,
    val vaultLockError: String? = null,
    /** True once the duress/decoy password was used to enter — vault shows fake-empty. */
    val vaultDecoyMode: Boolean = false,
    // ── Settings ──
    val settings: com.haze.mobile.storage.SettingsStore.Settings = com.haze.mobile.storage.SettingsStore.Settings(),
    val settingsReturnTo: Screen = Screen.Connect,
)

/** All network + UI state for one active chat connection (host or join). */
private class SessionState(
    val id: String,
    val isHost: Boolean,
    val nick: String,
    val password: String,
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
    /** Optional Tor Browser bridge, host mode only (settings.allowWebAccess). */
    var webServer: com.haze.mobile.net.WebChatServer? = null
    val fileBuffers = ConcurrentHashMap<String, FileBuffer>()
    val blocked = mutableSetOf<String>()   // nicks muted client-side
    /** Round-trip time from the last heartbeat pong, join-mode only (matches desktop's title-bar latency dot). */
    var latencyMs: Int? = null
    /** Nick of a peer/host whose panic we just received, pending the user's wipe confirmation. */
    var pendingPanicNick: String? = null

    val label: String get() = "${if (isHost) "HOST" else "JOIN"} · $nick"

    fun teardown() {
        runCatching { client?.shutdown() }
        runCatching { webServer?.stop() }
        runCatching { server?.stop() }
        client = null
        webServer = null
        server = null
        fileBuffers.clear()
    }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private val sessions = LinkedHashMap<String, SessionState>()
    private var activeId: String? = null

    init {
        _ui.update { it.copy(settings = com.haze.mobile.storage.SettingsStore.load(getApplication())) }
    }

    private fun active(): SessionState? = activeId?.let { sessions[it] }

    // ── Foreground keep-alive service ─────────────────────────────────────

    /**
     * Reflect the current session state into the ongoing notification. Starting
     * the foreground service raises the process to foreground priority so Android
     * keeps it (and the live Tor sockets) alive while the app is backgrounded.
     * With no sessions left, the service is torn down.
     */
    @Volatile private var appInForeground = true

    /** Called from the Activity so we know whether to raise message notifications. */
    fun onAppForeground() {
        appInForeground = true
        com.haze.mobile.service.Notifier.cancelAll(getApplication())
    }

    fun onAppBackground() { appInForeground = false }

    /** Raise a heads-up notification for an incoming message when backgrounded. */
    private fun maybeNotifyMessage(s: SessionState, sender: String, preview: String) {
        if (sender == s.nick) return                        // our own echo
        if (appInForeground) return                         // user is looking at the app
        if (!_ui.value.settings.messageNotifications) return
        com.haze.mobile.service.Notifier.notifyMessage(
            getApplication(), s.id, sender, preview,
            showContent = _ui.value.settings.notificationsShowContent,
        )
    }

    private fun updateForegroundService() {
        val app = getApplication<Application>()
        if (sessions.isEmpty() || !_ui.value.settings.persistentNotification) {
            com.haze.mobile.service.ConnectionService.stop(app)
            return
        }
        val a = active()
        val role = when {
            a == null -> "Active"
            a.isHost -> "Hosting"
            else     -> "Connected"
        }
        val text = when {
            a == null -> "${sessions.size} active sessions"
            sessions.size > 1 -> "$role as ${a.nick}  ·  +${sessions.size - 1} more"
            else -> "$role as ${a.nick}"
        }
        com.haze.mobile.service.ConnectionService.start(app, text)
    }

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
                    latencyMs = null,
                    pendingPanicNick = null,
                    sessions = summaries(),
                )
            } else {
                cur.copy(
                    screen = screen ?: cur.screen,
                    connecting = a.connecting, connected = a.connected, status = a.status, error = a.error,
                    myNick = a.nick, hostOnion = a.hostOnion, isHost = a.isHost,
                    participants = a.participants, messages = a.messages, typingUsers = a.typingUsers,
                    blockedUsers = a.blocked.toSet(),
                    latencyMs = a.latencyMs,
                    pendingPanicNick = a.pendingPanicNick,
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
        updateForegroundService()
    }

    /** Leave (close) the active session. */
    fun leaveActive() {
        val a = active() ?: return
        a.teardown()
        sessions.remove(a.id)
        activeId = sessions.keys.lastOrNull()
        if (activeId == null) {
            val keep = _ui.value.settings          // preserve settings across reset
            _ui.value = ChatUiState(settings = keep)  // back to a fresh Connect screen
        } else {
            rebuildUi(Screen.Chat)
        }
        updateForegroundService()
    }

    private fun nick(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(20)

    fun connect(onion: String, nickRaw: String, password: String) {
        val cleanNick = nick(nickRaw)
        if (cleanNick.isEmpty()) { _ui.update { it.copy(error = "Nickname: letters, numbers, _ or - only") }; return }
        val trimmed = onion.trim().removeSuffix("/")
        if (trimmed.isEmpty()) { _ui.update { it.copy(error = "Session address required") }; return }
        // Auto-append .onion if the user didn't type it.
        val onionAddr = if (trimmed.endsWith(".onion")) trimmed else "$trimmed.onion"
        if (!onionAddr.endsWith(".onion")) { _ui.update { it.copy(error = "Invalid address") }; return }

        val s = SessionState(UUID.randomUUID().toString(), isHost = false, nick = cleanNick, password = password)
        s.hostOnion = onionAddr
        s.status = "Starting Tor…"
        sessions[s.id] = s
        activeId = s.id
        // Stay on the Connect screen showing progress until the handshake succeeds.
        _ui.update { it.copy(connecting = true, status = s.status, error = null, addingSession = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socksPort = startTor(s)
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

    /**
     * Boot Tor with the configured connection method and return its SOCKS port.
     *
     * Centralised so all three entry points (join, host, reconnect) apply the
     * same bridge settings and the same bootstrap budget — going through a
     * bridge takes far longer than a direct bootstrap. Throws when the selected
     * bridge cannot be set up, which the callers surface as a connection error
     * instead of retrying without it.
     */
    private suspend fun startTor(s: SessionState): Int {
        val cfg = _ui.value.settings
        val label = if (com.haze.mobile.net.TorBridges.usesBridges(cfg.torBridgeMode)) {
            "Bootstrapping Tor via ${cfg.torBridgeMode}…"
        } else {
            "Bootstrapping Tor…"
        }
        TorManager.init(
            getApplication(),
            bridgeMode = cfg.torBridgeMode,
            bridgesCustom = cfg.torBridgesCustom,
        ) { pct -> if (!s.connected) setConnectProgress(s, "$label $pct%") }
        return TorManager.start(TorManager.bootstrapTimeoutFor(cfg.torBridgeMode))
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

        val s = SessionState(UUID.randomUUID().toString(), isHost = true, nick = cleanNick, password = password)
        s.status = "Starting Tor…"
        s.participants = listOf(cleanNick)
        sessions[s.id] = s
        activeId = s.id
        _ui.update { it.copy(connecting = true, status = s.status, error = null, addingSession = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Bootstrap here rather than letting startHiddenService() do it,
                // so a bridged connection gets the longer timeout.
                startTor(s)
                val localPort = (40_000..60_000).random()
                val srv = ChatServer(cleanNick, localPort, password) { ev -> handleEvent(s.id, ev) }
                s.server = srv
                srv.start()

                // Optional Tor Browser bridge on onion virtual port 80. Every
                // event the host UI sees is also forwarded to browser clients
                // (mirrors desktop's _NetBridge), so they observe the same
                // stream native clients do.
                var httpPort: Int? = null
                if (_ui.value.settings.allowWebAccess) {
                    var p = (40_000..60_000).random()
                    while (p == localPort) p = (40_000..60_000).random()
                    httpPort = p
                    s.webServer = com.haze.mobile.net.WebChatServer(
                        context = getApplication(),
                        hostNick = cleanNick,
                        httpPort = p,
                        chatServer = srv,
                        onEvent = { ev -> handleEvent(s.id, ev) },
                        sessionPassword = password,
                    ).also { it.start() }
                }

                setConnectProgress(s, "Publishing onion service…")
                val onion = TorManager.startHiddenService(localPort, httpPort)
                s.hostOnion = onion; s.connecting = false; s.connected = true; s.status = "Hosting"
                // Handshake done → enter the chat (or update the tab if not active).
                if (s.id == activeId) { _ui.update { it.copy(addingSession = false) }; rebuildUi(Screen.Chat) } else touch(s)
                updateForegroundService()
            } catch (e: Exception) {
                s.teardown()
                sessions.remove(s.id)
                if (activeId == s.id) activeId = sessions.keys.lastOrNull()
                _ui.update { it.copy(error = "Failed to host: ${e.message ?: "unknown"}") }
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
            }
        }
    }

    /** Reconnect a session that dropped mid-chat. Only works for join mode. */
    fun reconnect() {
        val s = active() ?: return
        if (s.isHost) return   // host can't reconnect (session is gone)
        val onion = s.hostOnion; if (onion.isBlank()) return
        s.connecting = true; s.connected = false; s.status = "Reconnecting…"; s.error = null
        s.teardown()
        // Clear the disconnected notice so the message list reflects the attempt.
        s.messages = s.messages.filter { it.content != "Disconnected." }
        rebuildUi(Screen.Chat)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socksPort = startTor(s)
                setConnectProgress(s, "Connecting to host…")
                val c = ChatClient(s.hostOnion, s.nick, s.password, TorManager.LOOPBACK, socksPort) { ev -> handleEvent(s.id, ev) }
                s.client = c
                c.start()
            } catch (e: Exception) {
                s.teardown(); sessions.remove(s.id)
                if (activeId == s.id) activeId = sessions.keys.lastOrNull()
                _ui.update { it.copy(error = "Reconnect failed: ${e.message ?: "unknown"}") }
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
            }
        }
    }

    // ── Sending (operate on the active session) ─────────────────────────

    fun sendChat(text: String, replyToNick: String? = null, replyToContent: String? = null) {
        val content = text.trim(); if (content.isEmpty()) return
        val s = active() ?: return
        val secs = _ui.value.settings.disappearingSeconds
        s.server?.let {
            it.sendChat(content, replyToNick, replyToContent, secs); return
        }
        val c = s.client ?: return
        val msgId = c.sendChat(content, replyToNick, replyToContent, secs)
        s.messages = s.messages + ChatMessage(
            nick = s.nick, content = content, isMe = true, msgId = msgId,
            replyToNick = replyToNick, replyToContent = replyToContent,
            disappearSecs = secs,
        )
        touch(s)
        if (secs > 0) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(secs * 1000L)
                // Broadcast delete so all clients remove it — not just locally.
                s.server?.sendDelete(msgId) ?: s.client?.sendDelete(msgId)
                s.messages = s.messages.filterNot { it.msgId == msgId }
                touch(s)
            }
        }
    }

    fun setTyping(isTyping: Boolean) {
        if (!_ui.value.settings.sendTypingIndicators) return
        val s = active() ?: return
        s.server?.sendTyping(isTyping) ?: s.client?.sendTyping(isTyping)
    }

    // ── Settings ─────────────────────────────────────────────────────────

    fun openSettings(from: Screen) {
        _ui.update { it.copy(screen = Screen.Settings, settingsReturnTo = from) }
    }

    fun exitSettings() {
        _ui.update { it.copy(screen = it.settingsReturnTo) }
    }

    fun updateSettings(newSettings: com.haze.mobile.storage.SettingsStore.Settings) {
        com.haze.mobile.storage.SettingsStore.save(getApplication(), newSettings)
        _ui.update { it.copy(settings = newSettings) }
        // Apply the notification prefs immediately.
        updateForegroundService()
        if (!newSettings.messageNotifications) {
            com.haze.mobile.service.Notifier.cancelAll(getApplication())
        }
    }

    /** Delete one of our own messages, signalling peers to remove it too. */
    fun deleteMessage(msg: ChatMessage) {
        if (!msg.isMe) return
        val id = msg.msgId ?: return
        val s = active() ?: return
        s.server?.sendDelete(id) ?: s.client?.sendDelete(id)
        s.messages = s.messages.map { if (it.msgId == id) it.copy(deleted = true) else it }
        touch(s)
    }

    /** Edit one of our own messages, signalling peers to update it too. */
    fun editMessage(msg: ChatMessage, newContent: String) {
        if (!msg.isMe) return
        val id = msg.msgId ?: return
        if (newContent.isBlank()) return
        val s = active() ?: return
        s.server?.sendEdit(id, newContent) ?: s.client?.sendEdit(id, newContent)
        s.messages = s.messages.map { if (it.msgId == id) it.copy(content = newContent, edited = true) else it }
        touch(s)
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
        wipeAndExit()
    }

    /**
     * Confirmed wipe after a *peer's* panic (see the "pong"-adjacent "panic"
     * case in [applyEvent]) — no broadcast, since we're reacting to someone
     * else's panic rather than announcing our own (mirrors desktop's
     * _execute_panic_wipe(), which the received-panic dialog also calls
     * directly without going through _trigger_panic()'s send_panic()).
     */
    fun confirmReceivedPanicWipe() {
        wipeAndExit()
    }

    /** Decline the received-panic wipe — just dismiss the dialog and carry on. */
    fun dismissReceivedPanic() {
        val s = active() ?: return
        s.pendingPanicNick = null
        touch(s)
    }

    private fun wipeAndExit() {
        runCatching { com.haze.mobile.service.ConnectionService.stop(getApplication()) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            sessions.values.forEach { it.teardown() }
            sessions.clear()
            activeId = null
            _ui.value = ChatUiState()
            purgePlaybackCache()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /**
     * Delete the cache copies inline playback needs.
     *
     * MediaPlayer and VideoView both want a real file, so received voice notes
     * and videos are written to the private cache (see AudioPlayer/VideoPlayer
     * in ChatScreen). Killing the process leaves those bytes on disk, which is
     * exactly what a panic wipe is supposed to prevent.
     */
    private fun purgePlaybackCache() {
        runCatching {
            getApplication<Application>().cacheDir
                .listFiles { f -> f.name.startsWith("play_") }
                ?.forEach { it.delete() }
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
        runCatching { com.haze.mobile.service.ConnectionService.stop(getApplication()) }
    }

    // ── Event handling (per-session) ─────────────────────────────────────

    private fun handleEvent(sid: String, ev: JsonObject) {
        val s = sessions[sid] ?: return
        // Mirror every event to Tor Browser clients so they see the same stream
        // native clients do (matches desktop's _NetBridge). Runs before the
        // local handling below, and off the caller's thread, so a slow or dead
        // browser socket can never stall the host's own UI update.
        s.webServer?.let { web ->
            viewModelScope.launch(Dispatchers.IO) { runCatching { web.broadcastEvent(ev) } }
        }
        when (ev["type"]?.jsonPrimitive?.content) {
            "connected" -> {
                s.connecting = false; s.connected = true; s.status = "Connected"; s.error = null
                // Handshake done → enter the chat (or just refresh the tab if not active).
                if (s.id == activeId) { _ui.update { it.copy(addingSession = false) }; rebuildUi(Screen.Chat) } else touch(s)
                updateForegroundService()
                return
            }
            "auth_failed" -> {
                s.teardown(); sessions.remove(sid)
                if (activeId == sid) activeId = sessions.keys.lastOrNull()
                rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
                _ui.update { it.copy(connecting = false, error = "Wrong session password") }
                updateForegroundService()
                return
            }
            "disconnected" -> {
                if (!s.connected) {
                    // Never completed the handshake → the connection attempt failed.
                    s.teardown(); sessions.remove(sid)
                    if (activeId == sid) activeId = sessions.keys.lastOrNull()
                    rebuildUi(if (sessions.isEmpty()) Screen.Connect else Screen.Chat)
                    _ui.update { it.copy(connecting = false, error = "Could not reach host over Tor") }
                    updateForegroundService()
                    return
                }
                s.connecting = false; s.connected = false; s.status = "Disconnected"
                s.messages = s.messages + ChatMessage("", "Disconnected.", isSystem = true)
                updateForegroundService()
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
                if (n in s.blocked) return
                val content = ev["content"]?.jsonPrimitive?.content ?: ""
                val msgId = ev["msg_id"]?.jsonPrimitive?.content
                val replyToNick = ev["reply_to_nick"]?.jsonPrimitive?.content
                val replyToContent = ev["reply_to_content"]?.jsonPrimitive?.content
                val disapSecs = ev["disappear_secs"]?.jsonPrimitive?.intOrNull ?: 0
                s.typingUsers = s.typingUsers.filterNot { it == n }
                val newMsg = ChatMessage(
                    n, content, isMe = n == s.nick, msgId = msgId,
                    replyToNick = replyToNick, replyToContent = replyToContent,
                    disappearSecs = disapSecs,
                )
                s.messages = s.messages + newMsg
                maybeNotifyMessage(s, n, content)
                // Disappearing: all clients start their own timer. When it fires,
                // they broadcast delete so it disappears for everyone.
                if (disapSecs > 0 && msgId != null) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(disapSecs * 1000L)
                        s.server?.sendDelete(msgId) ?: s.client?.sendDelete(msgId)
                        s.messages = s.messages.filterNot { it.msgId == msgId }
                        touch(s)
                    }
                }
            }
            "delete" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                val id = ev["msg_id"]?.jsonPrimitive?.content ?: return
                // Only let a sender delete their own message.
                s.messages = s.messages.map {
                    if (it.msgId == id && it.nick == n) it.copy(deleted = true) else it
                }
            }
            "edit" -> {
                val n = ev["nick"]?.jsonPrimitive?.content ?: return
                val id = ev["msg_id"]?.jsonPrimitive?.content ?: return
                val newContent = ev["content"]?.jsonPrimitive?.content ?: return
                s.messages = s.messages.map {
                    if (it.msgId == id && it.nick == n) it.copy(content = newContent, edited = true) else it
                }
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
                val preview = when {
                    mime.startsWith("image/") -> "📷 Photo"
                    mime.startsWith("audio/") -> "🎤 Voice note"
                    else -> "📎 $filename"
                }
                maybeNotifyMessage(s, n, preview)
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
                val n = ev["nick"]?.jsonPrimitive?.content ?: "?"
                if (n == s.nick) {
                    // I pressed panic — handled by panic().
                } else if (s.isHost) {
                    // A client panicked — session continues.
                    s.messages = s.messages + ChatMessage("", "⚠ $n pressed panic and left", isSystem = true)
                } else {
                    // Host or another client panicked — ask before wiping,
                    // matching desktop's _show_panic_dialog rather than
                    // force-disconnecting the user with no choice.
                    s.messages = s.messages + ChatMessage("", "⚠ Session ended.", isSystem = true)
                    s.pendingPanicNick = n
                }
            }
            "kicked" -> {
                s.messages = s.messages + ChatMessage("", "You were removed.", isSystem = true)
                s.client?.disconnect()
            }
            "pong" -> {
                // Round-trip time for our own heartbeat ping — the host echoes
                // back the exact ts we sent, so no per-session "sent at" state
                // is needed (matches desktop's title-bar latency dot).
                val ts = ev["ts"]?.jsonPrimitive?.content?.toDoubleOrNull()
                if (ts != null) {
                    val rttMs = ((System.currentTimeMillis() / 1000.0 - ts) * 1000).toInt()
                    if (rttMs >= 0) s.latencyMs = rttMs
                }
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
        // A vault lock password, if set, gates the session *list* itself —
        // separate from each session's own save password below. Mirrors
        // desktop's _VaultPopup, which shows its lock page first unless
        // settings["vault_lock_hash"] is empty.
        if (_ui.value.settings.vaultLockHash.isNotEmpty()) {
            _ui.update {
                it.copy(
                    screen = Screen.Vault, vaultReturnTo = from,
                    vaultLocked = true, vaultLockError = null, vaultDecoyMode = false,
                    vaultSessions = emptyList(), vaultOpenMessages = null, vaultError = null,
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val list = com.haze.mobile.storage.VaultStore.listSessions(getApplication())
            _ui.update {
                it.copy(
                    screen = Screen.Vault, vaultReturnTo = from, vaultSessions = list,
                    vaultLocked = false, vaultDecoyMode = false, vaultOpenMessages = null, vaultError = null,
                )
            }
        }
    }

    /**
     * Submit the vault-lock password prompt. The duress/decoy password (if
     * set) takes priority: it destroys every real saved session and shows an
     * empty vault, for use if someone is forcing you to unlock it — matches
     * desktop's _VaultPopup._do_unlock, which checks check_decoy() before
     * check_lock() for the same reason.
     */
    fun unlockVault(password: String) {
        val settings = _ui.value.settings
        if (com.haze.mobile.storage.VaultLock.checkDecoy(password, settings.vaultDecoyHash)) {
            viewModelScope.launch(Dispatchers.IO) {
                com.haze.mobile.storage.VaultStore.listSessions(getApplication()).forEach {
                    com.haze.mobile.storage.VaultStore.deleteSession(it.path)
                }
                _ui.update {
                    it.copy(vaultLocked = false, vaultDecoyMode = true, vaultSessions = emptyList(), vaultLockError = null)
                }
            }
            return
        }
        if (com.haze.mobile.storage.VaultLock.checkLock(password, settings.vaultLockHash)) {
            viewModelScope.launch(Dispatchers.IO) {
                // Now that the password is in hand, re-store a PBKDF2-era hash
                // with scrypt. The password itself is unchanged; only the stored
                // digest gets harder to attack offline. The decoy hash can't be
                // upgraded here — that would take the decoy password, which by
                // definition is not the one just entered.
                if (com.haze.mobile.storage.VaultLock.isLegacyHash(settings.vaultLockHash)) {
                    val upgraded = settings.copy(
                        vaultLockHash = com.haze.mobile.storage.VaultLock.makeLockHash(password)
                    )
                    com.haze.mobile.storage.SettingsStore.save(getApplication(), upgraded)
                    _ui.update { it.copy(settings = upgraded) }
                }
                val list = com.haze.mobile.storage.VaultStore.listSessions(getApplication())
                _ui.update {
                    it.copy(vaultLocked = false, vaultDecoyMode = false, vaultSessions = list, vaultLockError = null)
                }
            }
            return
        }
        _ui.update { it.copy(vaultLockError = "Wrong vault password.") }
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
    fun exitVault() = _ui.update {
        it.copy(
            screen = it.vaultReturnTo, vaultOpenMessages = null, vaultError = null,
            // Re-lock on the way out so the next open always re-prompts.
            vaultLocked = false, vaultLockError = null, vaultDecoyMode = false,
        )
    }

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
