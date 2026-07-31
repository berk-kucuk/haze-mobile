package com.haze.mobile.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Inner-payload constructors, mirroring the `make_*` helpers in `protocol.py`. */
object Protocol {

    const val CHAT_PORT = 5222
    /** Onion virtual port for the optional Tor Browser web UI (matches desktop). */
    const val WEB_PORT = 80
    const val CHUNK_SIZE = 256 * 1024   // 256 KB raw per file chunk (matches protocol.py)

    /**
     * Transfer ceilings, enforced by every receiver rather than trusted from
     * the sender's header. Match protocol.py and the browser client, so a
     * transfer refused on one platform is refused on all of them.
     */
    const val MAX_FILE_BYTES = 100 * 1024 * 1024
    const val MAX_FILE_CHUNKS = 512

    fun hello(publicKeyB64: String, nick: String, passwordHash: String): JsonObject = buildJsonObject {
        put("type", "hello")
        put("public_key", publicKeyB64)
        put("nick", nick)
        put("password_hash", passwordHash)
    }

    fun chat(
        nick: String,
        content: String,
        msgId: String,
        replyToNick: String? = null,
        replyToContent: String? = null,
        disappearSecs: Int = 0,
    ): JsonObject = buildJsonObject {
        put("type", "chat")
        put("nick", nick)
        put("content", content)
        put("msg_id", msgId)
        if (replyToNick != null && replyToContent != null) {
            put("reply_to_nick", replyToNick)
            put("reply_to_content", replyToContent)
        }
        if (disappearSecs > 0) {
            put("disappear_secs", disappearSecs)
        }
    }

    fun delete(nick: String, msgId: String): JsonObject = buildJsonObject {
        put("type", "delete")
        put("nick", nick)
        put("msg_id", msgId)
    }

    fun edit(nick: String, msgId: String, content: String): JsonObject = buildJsonObject {
        put("type", "edit")
        put("nick", nick)
        put("msg_id", msgId)
        put("content", content)
    }

    fun typing(nick: String, isTyping: Boolean): JsonObject = buildJsonObject {
        put("type", "typing")
        put("nick", nick)
        put("state", isTyping)
    }

    fun leave(nick: String): JsonObject = buildJsonObject {
        put("type", "leave")
        put("nick", nick)
    }

    fun panic(nick: String): JsonObject = buildJsonObject {
        put("type", "panic")
        put("nick", nick)
    }

    fun kicked(): JsonObject = buildJsonObject {
        put("type", "kicked")
    }

    fun ping(ts: Double): JsonObject = buildJsonObject {
        put("type", "ping")
        put("ts", ts)
    }

    // ── Host-side payloads ──────────────────────────────────────────────

    fun welcome(hostPubB64: String, nonceB64: String, ctB64: String): JsonObject = buildJsonObject {
        put("type", "welcome")
        put("public_key", hostPubB64)
        put("nonce", nonceB64)
        put("ciphertext", ctB64)
    }

    fun authFailed(): JsonObject = buildJsonObject {
        put("type", "auth_failed")
    }

    fun userlist(users: List<String>): JsonObject = buildJsonObject {
        put("type", "userlist")
        put("users", buildJsonArray { users.forEach { add(it) } })
    }

    fun join(nick: String): JsonObject = buildJsonObject {
        put("type", "join")
        put("nick", nick)
    }

    fun pong(ts: Double): JsonObject = buildJsonObject {
        put("type", "pong")
        put("ts", ts)
    }

    fun encrypted(nonceB64: String, ctB64: String): JsonObject = buildJsonObject {
        put("type", "encrypted")
        put("nonce", nonceB64)
        put("ciphertext", ctB64)
    }

    // ── File transfer ────────────────────────────────────────────────────

    fun fileStart(
        nick: String, fileId: String, filename: String, mime: String,
        totalSize: Int, totalChunks: Int,
    ): JsonObject = buildJsonObject {
        put("type", "file_start")
        put("nick", nick)
        put("file_id", fileId)
        put("filename", filename)
        put("mime", mime)
        put("total_size", totalSize)
        put("total_chunks", totalChunks)
    }

    fun fileChunk(nick: String, fileId: String, chunkIndex: Int, dataB64: String): JsonObject = buildJsonObject {
        put("type", "file_chunk")
        put("nick", nick)
        put("file_id", fileId)
        put("chunk_index", chunkIndex)
        put("data", dataB64)
    }

    fun fileEnd(nick: String, fileId: String): JsonObject = buildJsonObject {
        put("type", "file_end")
        put("nick", nick)
        put("file_id", fileId)
    }
}
