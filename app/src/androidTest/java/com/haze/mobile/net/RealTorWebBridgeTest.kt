package com.haze.mobile.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * End-to-end reproduction of "hosting from mobile, can't reach it from Tor
 * Browser": boots the app's REAL embedded Tor, publishes a REAL onion service
 * with the port-80 web mapping (exactly what MainActivity's host() flow
 * does), then fetches it through a SEPARATE, independent Tor client — the
 * host machine's system tor.service, reached via the emulator's 10.0.2.2
 * host-loopback alias — the same way Tor Browser would reach it. This is the
 * only way to actually prove the ADD_ONION multi-port mapping and the Ktor
 * web server work together over live Tor; WebChatServerTest only proves the
 * web server itself works over plain loopback, never through Tor at all.
 */
@RunWith(AndroidJUnit4::class)
class RealTorWebBridgeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun onionPublishedFromMobile_isReachableOverTorPort80() = runBlocking {
        val chatPort = ServerSocketFreePort()
        val httpPort = ServerSocketFreePort()

        val chat = ChatServer("hostnick", chatPort, "") { }
        chat.start()
        val web = WebChatServer(
            context = context,
            hostNick = "hostnick",
            httpPort = httpPort,
            chatServer = chat,
            onEvent = { },
        )
        web.start()

        var onion: String? = null
        try {
            TorManager.init(context) { pct -> println("bootstrap: $pct%") }
            // Real bootstrap: give it generous time on a cold emulator.
            onion = withTimeoutLog("startHiddenService") {
                TorManager.startHiddenService(chatPort, httpPort)
            }
            println("published onion: $onion")
            assertTrue("expected a .onion hostname, got: $onion", onion!!.endsWith(".onion"))

            // Descriptor propagation isn't instant — retry through a SEPARATE,
            // independent Tor client (the host machine's own tor.service,
            // reached via the emulator's host-loopback alias) for up to 3
            // minutes, exactly like a real Tor Browser user would have to.
            val html = fetchOverTorWithRetry(onion!!, totalTimeoutMs = 180_000)
            assertTrue(
                "fetched page didn't look like the Haze web UI:\n${html.take(300)}",
                html.contains("<!DOCTYPE html>") && html.contains("/ws"),
            )
        } finally {
            web.stop()
            chat.stop()
        }
    }

    private fun ServerSocketFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private suspend fun <T> withTimeoutLog(label: String, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return kotlinx.coroutines.withTimeout(150_000) { block() }
        } finally {
            println("$label took ${System.currentTimeMillis() - start} ms")
        }
    }

    /**
     * Fetch `http://$onionHost/` through the host machine's Tor SOCKS proxy at
     * 10.0.2.2:9050 (the emulator's alias for the host's loopback — exactly
     * what a developer's real desktop `tor.service` exposes), retrying while
     * the freshly-published onion descriptor propagates through the network.
     *
     * Deliberately a raw Socket + hand-rolled HTTP/1.1 request rather than
     * HttpURLConnection: Android's per-app Network Security Config blocks
     * cleartext http:// through the platform's URL/HttpURLConnection/OkHttp
     * stack regardless of proxy, which would fail here even on a perfectly
     * reachable onion — that policy is scoped to THIS app's process (Haze's
     * manifest), not to Tor Browser, which is a separate app with its own
     * policy and isn't what's actually being tested. A raw socket bypasses
     * that layer entirely, same as a plain SOCKS-aware HTTP client would.
     */
    private fun fetchOverTorWithRetry(onionHost: String, totalTimeoutMs: Long): String {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("10.0.2.2", 9050))
        val deadline = System.currentTimeMillis() + totalTimeoutMs
        var lastError: Exception? = null
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt++
            try {
                Socket(proxy).use { socket ->
                    socket.connect(InetSocketAddress.createUnresolved(onionHost, 80), 25_000)
                    socket.soTimeout = 25_000
                    socket.getOutputStream().apply {
                        write("GET / HTTP/1.1\r\nHost: $onionHost\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                    val raw = socket.getInputStream().bufferedReader().use { it.readText() }
                    val body = raw.substringAfter("\r\n\r\n", raw)
                    if (!raw.startsWith("HTTP/1.1 200") && !raw.startsWith("HTTP/1.0 200")) {
                        throw Exception("unexpected response: ${raw.take(60)}")
                    }
                    return body
                }
            } catch (e: Exception) {
                lastError = e
            }
            println("attempt $attempt: not reachable yet (${lastError?.message}), retrying…")
            Thread.sleep(5_000)
        }
        throw AssertionError("onion never became reachable over Tor after $attempt attempts", lastError)
    }
}
