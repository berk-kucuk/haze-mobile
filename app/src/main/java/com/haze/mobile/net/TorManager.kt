package com.haze.mobile.net

import android.content.Context
import android.util.Log
import com.haze.mobile.BuildConfig
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Embedded Tor — the app runs its own Tor daemon (via kmp-tor), so no Orbot
 * install is required. The daemon's auto-assigned SOCKS proxy is what the
 * [ChatClient] dials through to reach `.onion` hosts.
 *
 * Call [init] once with an application Context, then [start] to boot Tor and
 * obtain the local SOCKS port. Bootstrap progress is reported via [onProgress].
 */
object TorManager {

    const val LOOPBACK = "127.0.0.1"
    private const val TAG = "HazeTor"

    /** Debug-only log. Compiled out (and never emitted) in release builds so
     *  the onion address / SOCKS port / activity never reach logcat. */
    private fun dlog(msg: String) { if (BuildConfig.DEBUG) Log.i(TAG, msg) }

    @Volatile private var runtime: TorRuntime? = null
    @Volatile private var socksPort: Int = -1

    private var socksDeferred = CompletableDeferred<Int>()
    private var bootstrapDeferred = CompletableDeferred<Unit>()

    /** Bootstrap progress callback (0–100). Reset on each [init]. */
    @Volatile private var onProgress: ((Int) -> Unit)? = null

    @Synchronized
    fun init(context: Context, onProgress: (Int) -> Unit) {
        this.onProgress = onProgress
        if (runtime != null) return

        val appDir = context.getDir("kmptor", Context.MODE_PRIVATE).absolutePath.toFile()
        val env = TorRuntime.Environment.Builder(
            workDirectory = appDir.resolve("work"),
            cacheDirectory = appDir.resolve("cache"),
            loader = ResourceLoaderTorExec::getOrCreate,
        )

        runtime = TorRuntime.Builder(env) {
            // Let Tor pick a free SOCKS port — safest on mobile.
            config {
                TorOption.__SocksPort.configure { auto() }
            }

            // SOCKS listener opened → capture the port.
            observerStatic(RuntimeEvent.LISTENERS, OnEvent.Executor.Immediate) { listeners ->
                val sa = listeners.socks.firstOrNull()
                if (sa != null && !socksDeferred.isCompleted) {
                    socksPort = sa.port.value
                    dlog("SOCKS listener open on port ${sa.port.value}")
                    socksDeferred.complete(sa.port.value)
                }
            }

            // Bootstrap progress comes through NOTICE log lines like
            // "Bootstrapped 100% (done): Done".
            observerStatic(TorEvent.NOTICE, OnEvent.Executor.Immediate) { text ->
                val pct = parseBootstrap(text)
                if (pct != null) {
                    dlog("Bootstrapped $pct%")
                    this@TorManager.onProgress?.invoke(pct)
                    if (pct >= 100 && !bootstrapDeferred.isCompleted) {
                        bootstrapDeferred.complete(Unit)
                    }
                }
            }

            // Tor's own logs go to logcat only in debug builds.
            if (BuildConfig.DEBUG) {
                observerStatic(RuntimeEvent.LOG.WARN, OnEvent.Executor.Immediate) { Log.w(TAG, "tor: $it") }
                observerStatic(RuntimeEvent.ERROR, OnEvent.Executor.Immediate) { Log.e(TAG, "tor-err: $it") }
            }

            required(TorEvent.NOTICE)
        }.apply { environment().debug = BuildConfig.DEBUG }
    }

    /**
     * Boot the Tor daemon (idempotent) and wait until it is bootstrapped and a
     * SOCKS port is available. Returns the local SOCKS port.
     */
    suspend fun start(bootstrapTimeoutMs: Long = 120_000): Int {
        val rt = runtime ?: error("TorManager.init() not called")
        if (socksPort > 0 && bootstrapDeferred.isCompleted) return socksPort

        dlog("startDaemonAsync()…")
        rt.startDaemonAsync()
        dlog("daemon started, waiting for bootstrap…")

        return try {
            withTimeout(bootstrapTimeoutMs) {
                val port = socksDeferred.await()
                bootstrapDeferred.await()
                dlog("Tor ready — SOCKS port $port")
                port
            }
        } catch (e: TimeoutCancellationException) {
            dlog("bootstrap timed out")
            throw IllegalStateException("Tor bootstrap timed out", e)
        }
    }

    /**
     * Publish a v3 onion hidden service that forwards its virtual chat port
     * (5222) to the local [localPort] where [ChatServer] is listening. Boots
     * Tor first if needed. Returns the ".onion" hostname to share with peers.
     */
    suspend fun startHiddenService(localPort: Int): String {
        val rt = runtime ?: error("TorManager.init() not called")
        start() // ensure Tor is bootstrapped before adding the service
        dlog("publishing hidden service → 127.0.0.1:$localPort")
        val entry = rt.executeAsync(
            TorCmd.Onion.Add.new(ED25519_V3) {
                port(virtual = Protocol.CHAT_PORT.toPort()) {
                    target(port = localPort.toPort())
                }
            }
        )
        val onion = entry.publicKey.address().canonicalHostName()
        dlog("hidden service online: $onion")
        return onion
    }

    fun isRunning(): Boolean = socksPort > 0

    private fun parseBootstrap(text: String): Int? {
        // e.g. "Bootstrapped 45% (requesting_descriptors): ..."
        val idx = text.indexOf("Bootstrapped ")
        if (idx < 0) return null
        val after = text.substring(idx + "Bootstrapped ".length)
        val pctStr = after.takeWhile { it.isDigit() }
        return pctStr.toIntOrNull()
    }
}
