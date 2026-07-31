package com.haze.mobile.net

import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the bridge configuration layer, whose whole point is to work on
 * networks where the user cannot fall back to anything.
 *
 * The [TorConfigCompat] assertions matter most: bridges have no public config
 * API in kmp-tor (upstream issue #626), so those settings are built through
 * internal factories reached via `-Xfriend-paths`. If a kmp-tor upgrade reshapes
 * them, this test fails here rather than silently leaving a censored user
 * without a working transport.
 */
class TorBridgeConfigTest {

    // ── TorBridges ───────────────────────────────────────────────────────────

    @Test
    fun parseLinesToleratesWhatPeopleActuallyPaste() {
        val parsed = TorBridges.parseLines(
            """
            # my bridges
            Bridge obfs4 1.2.3.4:443 ABCD cert=xyz iat-mode=0

              obfs4   5.6.7.8:80   EFGH   cert=abc iat-mode=1   # inline comment
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "obfs4 1.2.3.4:443 ABCD cert=xyz iat-mode=0",
                "obfs4 5.6.7.8:80 EFGH cert=abc iat-mode=1",
            ),
            parsed,
        )
    }

    @Test
    fun directModeNeedsNothing() {
        assertEquals(emptyList<String>(), TorBridges.validate(TorBridges.MODE_DIRECT, ""))
        assertTrue(!TorBridges.usesBridges(TorBridges.MODE_DIRECT))
    }

    @Test
    fun unknownModeFallsBackToDirect() {
        assertEquals(TorBridges.MODE_DIRECT, TorBridges.normalizeMode("bogus"))
        assertEquals(TorBridges.MODE_DIRECT, TorBridges.normalizeMode(null))
    }

    @Test
    fun obfs4FallsBackToBuiltinBridges() {
        val lines = TorBridges.validate(TorBridges.MODE_OBFS4, "")
        assertEquals(TorBridges.BUILTIN[TorBridges.MODE_OBFS4]!!.size, lines.size)
        assertTrue(lines.all { it.startsWith("obfs4 ") })
    }

    @Test
    fun vanillaWithoutLinesIsRejected() {
        // No built-in vanilla bridges exist, so this mode must not silently pass.
        val error = runCatching { TorBridges.validate(TorBridges.MODE_VANILLA, "") }
            .exceptionOrNull()
        assertTrue(error is TorBridges.BridgeConfigError)
    }

    @Test
    fun transportLinePastedUnderVanillaIsRejected() {
        val error = runCatching {
            TorBridges.validate(
                TorBridges.MODE_VANILLA,
                TorBridges.BUILTIN[TorBridges.MODE_OBFS4]!!.first(),
            )
        }.exceptionOrNull()
        assertTrue(error is TorBridges.BridgeConfigError)
    }

    @Test
    fun mismatchedTransportLineIsRejected() {
        val error = runCatching {
            TorBridges.validate(TorBridges.MODE_OBFS4, "snowflake 192.0.2.3:80 ABCD url=https://x/")
        }.exceptionOrNull()
        assertTrue(error is TorBridges.BridgeConfigError)
    }

    @Test
    fun vanillaAcceptsAPlainBridgeLine() {
        val lines = TorBridges.validate(
            TorBridges.MODE_VANILLA,
            "1.2.3.4:443 ABCDEF0123456789ABCDEF0123456789ABCDEF01",
        )
        assertEquals(listOf("1.2.3.4:443 ABCDEF0123456789ABCDEF0123456789ABCDEF01"), lines)
    }

    @Test
    fun snowflakeParametersAreLiftedOutOfTheBridgeLine() {
        // IPtProxy's snowflake reads these off the controller, not the SOCKS args,
        // so PluggableTransports has to find them in the line.
        val line = TorBridges.BUILTIN[TorBridges.MODE_SNOWFLAKE]!!.first()
        assertEquals("https://1098762253.rsc.cdn77.org/", TorBridges.param(line, "url"))
        assertEquals("app.datapacket.com,www.datapacket.com", TorBridges.param(line, "fronts"))
        assertNotNull(TorBridges.param(line, "ice"))
        assertNull(TorBridges.param(line, "nonexistent"))
    }

    @Test
    fun onlyTransportModesNeedAPluggableTransport() {
        assertTrue(TorBridges.needsTransport(TorBridges.MODE_OBFS4))
        assertTrue(TorBridges.needsTransport(TorBridges.MODE_SNOWFLAKE))
        assertTrue(!TorBridges.needsTransport(TorBridges.MODE_VANILLA))
        assertTrue(!TorBridges.needsTransport(TorBridges.MODE_DIRECT))
    }

    // ── TorConfigCompat (the -Xfriend-paths shim) ────────────────────────────

    @Test
    fun shimBuildsTheThreeOptionsKmpTorCannotExpress() {
        assertEquals(TorOption.UseBridges, TorConfigCompat.useBridges().items.first().option)
        assertEquals("1", TorConfigCompat.useBridges().items.first().argument)

        val plugin = TorConfigCompat.clientTransportPlugin("obfs4", "127.0.0.1", 41000)
        assertEquals(TorOption.ClientTransportPlugin, plugin.items.first().option)
        assertEquals("obfs4 socks5 127.0.0.1:41000", plugin.items.first().argument)

        val line = TorBridges.BUILTIN[TorBridges.MODE_OBFS4]!!.first()
        val bridge = TorConfigCompat.bridge(line)
        assertEquals(TorOption.Bridge, bridge.items.first().option)
        assertEquals(line, bridge.items.first().argument)
    }

    @Test
    fun bridgeSettingsSurviveTheConfigBuilder() {
        val lines = TorBridges.BUILTIN[TorBridges.MODE_OBFS4]!!.take(3)

        val config = TorConfig.Builder {
            TorOption.__SocksPort.configure { auto() }
            put(TorConfigCompat.useBridges())
            put(TorConfigCompat.clientTransportPlugin("obfs4", "127.0.0.1", 41000))
            lines.forEach { put(TorConfigCompat.bridge(it)) }
        }

        // Bridge is not a unique option: every line has to be kept, not collapsed
        // into the last one.
        assertEquals(lines.size, config.settings.count { it.items.first().option == TorOption.Bridge })
        assertEquals(1, config.settings.count { it.items.first().option == TorOption.UseBridges })
        assertEquals(
            1,
            config.settings.count { it.items.first().option == TorOption.ClientTransportPlugin },
        )

        val rendered = config.settings.joinToString("") { it.toString() }
        assertTrue("UseBridges missing from torrc", rendered.contains("UseBridges 1"))
        assertTrue(
            "ClientTransportPlugin missing from torrc",
            rendered.contains("ClientTransportPlugin obfs4 socks5 127.0.0.1:41000"),
        )
        lines.forEach {
            assertTrue("bridge line missing from torrc: $it", rendered.contains("Bridge $it"))
        }
    }
}
