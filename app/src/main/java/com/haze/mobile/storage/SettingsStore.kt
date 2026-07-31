package com.haze.mobile.storage

import android.content.Context

/**
 * Lightweight persisted app preferences.
 *
 * These are UI/behaviour toggles only — no secrets are stored here, so plain
 * SharedPreferences is fine. Chat contents live encrypted in [VaultStore].
 */
object SettingsStore {

    private const val PREFS = "haze_settings"
    private const val KEY_SCREENSHOTS = "allow_screenshots"
    private const val KEY_TYPING = "send_typing"
    private const val KEY_TIMESTAMPS = "show_timestamps"
    private const val KEY_ENTER_SEND = "enter_to_send"
    private const val KEY_BLUR = "blur_messages"
    private const val KEY_PERSIST_NOTIF = "persistent_notification"
    private const val KEY_MSG_NOTIF = "message_notifications"
    private const val KEY_NOTIF_CONTENT = "notifications_show_content"
    private const val KEY_DISAPPEAR = "disappearing_seconds"
    private const val KEY_VAULT_LOCK_HASH = "vault_lock_hash"
    private const val KEY_VAULT_DECOY_HASH = "vault_decoy_hash"
    private const val KEY_WEB_ACCESS = "allow_web_access"
    private const val KEY_TOR_BRIDGE_MODE = "tor_bridge_mode"
    private const val KEY_TOR_BRIDGES_CUSTOM = "tor_bridges_custom"

    data class Settings(
        /** When false (default) FLAG_SECURE is set → screenshots/recording blocked. */
        val allowScreenshots: Boolean = false,
        /** Broadcast a "typing…" notice to peers while composing. */
        val sendTypingIndicators: Boolean = true,
        /** Show the time under each message bubble. */
        val showTimestamps: Boolean = true,
        /** Keyboard Enter/Send action sends the message instead of a newline. */
        val enterToSend: Boolean = true,
        /** Blur message contents shortly after they appear; tap to reveal. */
        val blurMessages: Boolean = false,
        /** Ongoing notification that keeps connections alive in the background. */
        val persistentNotification: Boolean = true,
        /** Notify when a message arrives while the app is in the background. */
        val messageNotifications: Boolean = true,
        /** Show sender + message text in the notification itself (vs. a generic "New message"). */
        val notificationsShowContent: Boolean = true,
        /** Auto-delete messages after N seconds (0 = off). */
        val disappearingSeconds: Int = 0,
        /** PBKDF2 hash gating the vault session list (empty = no lock, vault opens directly). */
        val vaultLockHash: String = "",
        /** PBKDF2 hash of the duress password — shows an empty vault and wipes real sessions. */
        val vaultDecoyHash: String = "",
        /**
         * When hosting, also publish onion virtual port 80 so the session can be
         * joined from Tor Browser. Off by default: browser clients can't run the
         * group key exchange, so their traffic is only protected by the onion
         * connection itself (see WebChatServer's SECURITY note).
         */
        val allowWebAccess: Boolean = false,
        /**
         * How Tor reaches the network: "direct", "vanilla", "obfs4" or
         * "snowflake" (see [com.haze.mobile.net.TorBridges]). Anything other
         * than direct routes the bootstrap through a bridge, for networks where
         * Tor is blocked outright.
         */
        val torBridgeMode: String = com.haze.mobile.net.TorBridges.MODE_DIRECT,
        /** User-supplied bridge lines, one per line; replaces the built-ins. */
        val torBridgesCustom: String = "",
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Settings {
        val p = prefs(context)
        return Settings(
            allowScreenshots = p.getBoolean(KEY_SCREENSHOTS, false),
            sendTypingIndicators = p.getBoolean(KEY_TYPING, true),
            showTimestamps = p.getBoolean(KEY_TIMESTAMPS, true),
            enterToSend = p.getBoolean(KEY_ENTER_SEND, true),
            blurMessages = p.getBoolean(KEY_BLUR, false),
            persistentNotification = p.getBoolean(KEY_PERSIST_NOTIF, true),
            messageNotifications = p.getBoolean(KEY_MSG_NOTIF, true),
            notificationsShowContent = p.getBoolean(KEY_NOTIF_CONTENT, true),
            disappearingSeconds = p.getInt(KEY_DISAPPEAR, 0),
            vaultLockHash = p.getString(KEY_VAULT_LOCK_HASH, "") ?: "",
            vaultDecoyHash = p.getString(KEY_VAULT_DECOY_HASH, "") ?: "",
            allowWebAccess = p.getBoolean(KEY_WEB_ACCESS, false),
            torBridgeMode = com.haze.mobile.net.TorBridges.normalizeMode(
                p.getString(KEY_TOR_BRIDGE_MODE, null)
            ),
            torBridgesCustom = p.getString(KEY_TOR_BRIDGES_CUSTOM, "") ?: "",
        )
    }

    fun save(context: Context, s: Settings) {
        prefs(context).edit()
            .putBoolean(KEY_SCREENSHOTS, s.allowScreenshots)
            .putBoolean(KEY_TYPING, s.sendTypingIndicators)
            .putBoolean(KEY_TIMESTAMPS, s.showTimestamps)
            .putBoolean(KEY_ENTER_SEND, s.enterToSend)
            .putBoolean(KEY_BLUR, s.blurMessages)
            .putBoolean(KEY_PERSIST_NOTIF, s.persistentNotification)
            .putBoolean(KEY_MSG_NOTIF, s.messageNotifications)
            .putBoolean(KEY_NOTIF_CONTENT, s.notificationsShowContent)
            .putInt(KEY_DISAPPEAR, s.disappearingSeconds)
            .putString(KEY_VAULT_LOCK_HASH, s.vaultLockHash)
            .putString(KEY_VAULT_DECOY_HASH, s.vaultDecoyHash)
            .putBoolean(KEY_WEB_ACCESS, s.allowWebAccess)
            .putString(KEY_TOR_BRIDGE_MODE, s.torBridgeMode)
            .putString(KEY_TOR_BRIDGES_CUSTOM, s.torBridgesCustom)
            .apply()
    }
}
