package com.haze.mobile.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.URL
import java.util.Collections

/**
 * Exercises the Tor Browser bridge end to end on-device: the HTTP page, the
 * WebSocket join/chat/typing protocol, the session-password gate and the
 * nick-collision handling. Tor is deliberately not involved — [WebChatServer]
 * binds to loopback and the onion service is only a port forward in front of
 * it, so the whole bridge is testable without bootstrapping Tor.
 */
@RunWith(AndroidJUnit4::class)
class WebChatServerTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun client() = HttpClient(CIO) { install(WebSockets) }

    /** Spin up a ChatServer + WebChatServer pair and run [block] against them. */
    private fun withBridge(
        password: String = "",
        block: suspend (httpPort: Int, hostEvents: MutableList<JsonObject>) -> Unit,
    ) = runBlocking {
        val chatPort = freePort()
        val httpPort = freePort()
        val hostEvents: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())
        val chat = ChatServer("hostnick", chatPort, password) { hostEvents += it }
        chat.start()
        val web = WebChatServer(
            context = context,
            hostNick = "hostnick",
            httpPort = httpPort,
            chatServer = chat,
            onEvent = { hostEvents += it },
            sessionPassword = password,
        )
        web.start()
        try {
            awaitPort(httpPort)
            withTimeout(30_000) { block(httpPort, hostEvents) }
        } finally {
            web.stop()
            chat.stop()
        }
    }

    /** Wait until the embedded server is actually accepting connections. */
    private suspend fun awaitPort(port: Int) {
        repeat(100) {
            val ok = runCatching {
                java.net.Socket("127.0.0.1", port).close(); true
            }.getOrDefault(false)
            if (ok) return
            delay(100)
        }
        throw AssertionError("web server never bound to port $port")
    }

    @Test
    fun servesTheChatPageOverHttp() = withBridge { httpPort, _ ->
        val html = URL("http://127.0.0.1:$httpPort/").readText()
        assertTrue("expected the Haze web UI", html.contains("<!DOCTYPE html>"))
        // The page must be self-contained and talk to our own endpoints.
        assertTrue("expected the /ws endpoint", html.contains("/ws"))
        assertTrue("expected the join form", html.contains("type:\"join\""))
    }

    @Test
    fun joinReturnsUserlistAndBridgesChatToHost() = withBridge { httpPort, hostEvents ->
        client().use { c ->
            c.webSocket("ws://127.0.0.1:$httpPort/ws") {
                send(Frame.Text("""{"type":"join","nick":"tester"}"""))

                val userlist = (incoming.receive() as Frame.Text).readText()
                assertTrue("expected a userlist frame, got: $userlist", userlist.contains("\"userlist\""))
                assertTrue("host should be listed", userlist.contains("hostnick"))
                // Browser guests are tagged so they can't be confused with app users.
                assertTrue("web nick should be tagged, got: $userlist", userlist.contains("[web]tester"))

                send(Frame.Text("""{"type":"chat","content":"hello from browser","msg_id":"m1"}"""))
                send(Frame.Text("""{"type":"typing","state":true}"""))
                delay(1500)
            }
        }

        // The host UI must have observed the join, the chat and the typing notice.
        val types = hostEvents.map { it["type"]?.jsonPrimitive?.content }
        assertTrue("host missed join, saw: $types", types.contains("join"))
        assertTrue("host missed chat, saw: $types", types.contains("chat"))
        assertTrue("host missed typing, saw: $types", types.contains("typing"))

        val chatEv = hostEvents.first { it["type"]?.jsonPrimitive?.content == "chat" }
        assertEquals("hello from browser", chatEv["content"]?.jsonPrimitive?.content)
        assertEquals("m1", chatEv["msg_id"]?.jsonPrimitive?.content)
        assertEquals("[web]tester", chatEv["nick"]?.jsonPrimitive?.content)
    }

    @Test
    fun wrongSessionPasswordIsRejected() = withBridge(password = "correct-horse") { httpPort, hostEvents ->
        client().use { c ->
            c.webSocket("ws://127.0.0.1:$httpPort/ws") {
                // Bare nick, no password_hash → must be refused.
                send(Frame.Text("""{"type":"join","nick":"intruder"}"""))
                val reply = (incoming.receive() as Frame.Text).readText()
                assertTrue("expected auth_failed, got: $reply", reply.contains("auth_failed"))
            }
        }
        val types = hostEvents.map { it["type"]?.jsonPrimitive?.content }
        assertTrue("a rejected client must not produce a join, saw: $types", !types.contains("join"))
    }

    @Test
    fun correctSessionPasswordIsAccepted() = withBridge(password = "correct-horse") { httpPort, _ ->
        val hash = com.haze.mobile.crypto.PasswordHash.hash("correct-horse")
        client().use { c ->
            c.webSocket("ws://127.0.0.1:$httpPort/ws") {
                send(Frame.Text("""{"type":"join","nick":"guest","password_hash":"$hash"}"""))
                val reply = (incoming.receive() as Frame.Text).readText()
                assertTrue("expected userlist, got: $reply", reply.contains("\"userlist\""))
            }
        }
    }

    @Test
    fun duplicateNicksAreMadeUnique() = withBridge { httpPort, _ ->
        client().use { c ->
            c.webSocket("ws://127.0.0.1:$httpPort/ws") {
                send(Frame.Text("""{"type":"join","nick":"dup"}"""))
                val first = (incoming.receive() as Frame.Text).readText()
                assertTrue(first.contains("[web]dup"))

                // Second browser asking for the same nick must get a suffixed one.
                client().use { c2 ->
                    c2.webSocket("ws://127.0.0.1:$httpPort/ws") {
                        send(Frame.Text("""{"type":"join","nick":"dup"}"""))
                        val second = (incoming.receive() as Frame.Text).readText()
                        assertTrue("expected a de-duplicated nick, got: $second", second.contains("[web]dup_2"))
                    }
                }
            }
        }
    }
}
