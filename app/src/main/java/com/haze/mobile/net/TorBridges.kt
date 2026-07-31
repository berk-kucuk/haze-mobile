package com.haze.mobile.net

/**
 * Tor connection methods for networks where reaching the Tor network directly
 * is blocked (Iran, China, Russia, Turkmenistan …).
 *
 *   [MODE_DIRECT]     Normal bootstrap. Fastest, needs nothing extra, but the
 *                     guard relay addresses are public and get blocked.
 *   [MODE_VANILLA]    Plain bridge relays — `UseBridges 1` plus `Bridge <addr> <fp>`.
 *                     No pluggable transport involved, so it defeats address
 *                     blocklists but not DPI that recognises the Tor handshake.
 *   [MODE_OBFS4]      Bridges behind obfs4, which makes the stream look like
 *                     nothing in particular. Runs lyrebird via [PluggableTransports].
 *   [MODE_SNOWFLAKE]  WebRTC through volunteers' browsers, broker request fronted
 *                     by a CDN. Gets through where obfs4 is already blocked.
 *
 * The built-in lines are what Tor Browser hands out via Moat
 * (`https://bridges.torproject.org/moat/circumvention/builtin`), captured
 * 2026-07-30, and are kept byte-identical to the desktop client's
 * `haze/tor/bridges.py`. They are public, so censors know them too and they
 * rot over time — hence the settings screen always accepts pasted lines from
 * https://bridges.torproject.org, @GetBridgesBot on Telegram, or
 * bridges@torproject.org.
 */
object TorBridges {

    const val MODE_DIRECT = "direct"
    const val MODE_VANILLA = "vanilla"
    const val MODE_OBFS4 = "obfs4"
    const val MODE_SNOWFLAKE = "snowflake"

    val MODES = listOf(MODE_DIRECT, MODE_VANILLA, MODE_OBFS4, MODE_SNOWFLAKE)

    /** Transport token a mode's bridge lines must start with; null for vanilla. */
    fun transportToken(mode: String): String? = when (mode) {
        MODE_OBFS4 -> "obfs4"
        MODE_SNOWFLAKE -> "snowflake"
        else -> null
    }

    /** Tokens tor knows, used to catch obfs4 lines pasted under "vanilla". */
    private val KNOWN_TOKENS = setOf(
        "obfs2", "obfs3", "obfs4", "scramblesuit", "meek", "meek_lite",
        "snowflake", "webtunnel", "conjure", "dnstt",
    )

    private val BUILTIN_OBFS4 = listOf(
        "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0",
        "obfs4 51.222.13.177:80 5EDAC3B810E12B01F6FD8050D2FD3E277B289A08 cert=2uplIpLQ0q9+0qMFrK5pkaYRDOe460LL9WHBvatgkuRr/SL31wBOEupaMMJ6koRE6Ld0ew iat-mode=0",
        "obfs4 209.148.46.65:443 74FAD13168806246602538555B5521A0383A1875 cert=ssH+9rP8dG2NLDN2XuFw63hIO/9MNNinLmxQDpVa+7kTOa9/m+tGWT1SmSYpQ9uTBGa6Hw iat-mode=0",
        "obfs4 212.83.43.74:443 39562501228A4D5E27FCA4C0C81A01EE23AE3EE4 cert=PBwr+S8JTVZo6MPdHnkTwXJPILWADLqfMGoVvhZClMq/Urndyd42BwX9YFJHZnBB3H0XCw iat-mode=1",
        "obfs4 45.145.95.6:27015 C5B7CD6946FF10C5B3E89691A7D3F2C122D2117C cert=TD7PbUO0/0k6xYHMPW3vJxICfkMZNdkRrb63Zhl5j9dW3iRGiCx0A7mPhe5T2EDzQ35+Zw iat-mode=0",
        "obfs4 146.57.248.225:22 10A6CD36A537FCE513A322361547444B393989F0 cert=K1gDtDAIcUfeLqbstggjIw2rtgIKqdIhUlHp82XRqNSq/mtAjp1BIC9vHKJ2FAEpGssTPw iat-mode=0",
        "obfs4 212.83.43.95:443 BFE712113A72899AD685764B211FACD30FF52C31 cert=ayq0XzCwhpdysn5o0EyDUbmSOx3X/oTEbzDMvczHOdBJKlvIdHHLJGkZARtT4dcBFArPPg iat-mode=1",
    )

    private val BUILTIN_SNOWFLAKE = listOf(
        "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn",
        "snowflake 192.0.2.4:80 8838024498816A039FCBBAB14E6F40A0843051FA fingerprint=8838024498816A039FCBBAB14E6F40A0843051FA url=https://1098762253.rsc.cdn77.org/ fronts=app.datapacket.com,www.datapacket.com ice=stun:stun.epygi.com:3478,stun:stun.uls.co.za:3478,stun:stun.voipgate.com:3478,stun:stun.mixvoip.com:3478,stun:stun.telnyx.com:3478,stun:stun.hot-chilli.net:3478,stun:stun.fitauto.ru:3478,stun:stun.m-online.net:3478 utls-imitate=hellorandomizedalpn",
    )

    /** Bridges shipped with the app. Vanilla has none: an unobfuscated bridge is
     *  only useful while its address is unpublished, so the user must supply it. */
    val BUILTIN: Map<String, List<String>> = mapOf(
        MODE_VANILLA to emptyList(),
        MODE_OBFS4 to BUILTIN_OBFS4,
        MODE_SNOWFLAKE to BUILTIN_SNOWFLAKE,
    )

    class BridgeConfigError(message: String) : Exception(message)

    fun normalizeMode(mode: String?): String =
        if (mode != null && mode in MODES) mode else MODE_DIRECT

    fun usesBridges(mode: String?): Boolean = normalizeMode(mode) != MODE_DIRECT

    /** Whether the mode needs a pluggable-transport process ([PluggableTransports]). */
    fun needsTransport(mode: String?): Boolean =
        normalizeMode(mode).let { it == MODE_OBFS4 || it == MODE_SNOWFLAKE }

    /**
     * Split pasted text into bridge lines, tolerating what people actually
     * paste: a leading `Bridge` keyword copied out of a torrc, `#` comments,
     * blank lines and doubled whitespace.
     */
    fun parseLines(text: String?): List<String> =
        (text ?: "").lines().mapNotNull { raw ->
            var line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@mapNotNull null
            if (line.length > 7 && line.substring(0, 7).lowercase() == "bridge ") {
                line = line.substring(7).trim()
            }
            line = line.split(Regex("\\s+")).joinToString(" ")
            line.ifEmpty { null }
        }

    /** The user's lines if they supplied any, otherwise the built-ins. */
    fun effectiveLines(mode: String, customText: String?): List<String> {
        val custom = parseLines(customText)
        return if (custom.isNotEmpty()) custom else BUILTIN[normalizeMode(mode)] ?: emptyList()
    }

    /**
     * Check a mode/lines combination the way the settings screen and
     * [TorManager] both need, throwing [BridgeConfigError] with a message fit
     * for display. Never falls back to a direct connection: on a filtered
     * network that fallback emits exactly the traffic a bridge was chosen to
     * avoid.
     */
    fun validate(mode: String, customText: String?): List<String> {
        val normalized = normalizeMode(mode)
        if (normalized == MODE_DIRECT) return emptyList()

        val lines = effectiveLines(normalized, customText)
        if (lines.isEmpty()) throw BridgeConfigError("No bridge lines configured.")

        val token = transportToken(normalized)
        lines.forEach { line ->
            val first = line.substringBefore(' ').lowercase()
            if (token != null) {
                if (first != token) {
                    throw BridgeConfigError("This bridge line is not an $token line: ${line.take(40)}…")
                }
            } else if (first in KNOWN_TOKENS) {
                throw BridgeConfigError(
                    "This is a $first bridge line — select the $first connection method to use it."
                )
            }
        }
        return lines
    }

    /**
     * Value of a `key=value` parameter in a bridge line, or null.
     *
     * Needed for snowflake: IPtProxy's snowflake client takes its broker URL,
     * front domains and STUN servers from the controller object rather than
     * from per-connection SOCKS arguments, so those have to be lifted out of
     * the bridge line and handed over separately.
     */
    fun param(line: String, key: String): String? =
        line.split(' ').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
}
