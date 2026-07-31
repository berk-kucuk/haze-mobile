package com.haze.mobile.net

import android.content.Context
import android.util.Log
import com.haze.mobile.BuildConfig
import IPtProxy.Controller
import IPtProxy.OnTransportEvents

/**
 * Runs the obfs4 (lyrebird) and snowflake pluggable transports in-process via
 * IPtProxy — the same Go bundle Orbot ships.
 *
 * Each transport exposes a local SOCKS5 listener. tor is then pointed at it with
 * `ClientTransportPlugin <transport> socks5 127.0.0.1:<port>` (see
 * [TorConfigCompat]), the "external proxy" form: on Android the transports are
 * a shared library rather than an executable, so tor cannot `exec` them the way
 * it does on desktop.
 */
object PluggableTransports {

    private const val TAG = "HazePT"

    private fun dlog(msg: String) { if (BuildConfig.DEBUG) Log.i(TAG, msg) }

    @Volatile private var controller: Controller? = null
    private val startedPorts = mutableMapOf<String, Int>()

    @Synchronized
    private fun controller(context: Context): Controller {
        controller?.let { return it }
        // Own directory rather than cacheDir: obfs4 keeps state between runs and
        // the cache can be evicted underneath it. Transport logging stays off in
        // release builds so bridge addresses never land on disk.
        val stateDir = context.getDir("iptproxy", Context.MODE_PRIVATE).absolutePath
        return Controller(
            stateDir,
            BuildConfig.DEBUG,      // enableLogging
            false,                  // unsafeLogging — would log connection details
            if (BuildConfig.DEBUG) "INFO" else "ERROR",
            object : OnTransportEvents {
                override fun connected(name: String?) { dlog("transport connected: $name") }
                override fun error(name: String?, error: Exception?) {
                    dlog("transport error: $name — ${error?.message}")
                }
                override fun stopped(name: String?, error: Exception?) {
                    dlog("transport stopped: $name — ${error?.message}")
                }
            },
        ).also { controller = it }
    }

    /**
     * Start the transport `mode` needs and return its local SOCKS5 port.
     * Idempotent per transport. Throws when the transport refuses to start, so
     * the caller can abort instead of quietly bootstrapping without a bridge.
     *
     * `bridgeLines` are the lines that will be handed to tor: snowflake's broker
     * URL, front domains and STUN servers are read off the controller object
     * rather than from per-connection SOCKS arguments, so they have to be lifted
     * out of the bridge line here for a user-supplied line to take effect.
     */
    @Synchronized
    fun start(context: Context, mode: String, bridgeLines: List<String>): Int {
        val transport = TorBridges.transportToken(mode)
            ?: throw IllegalArgumentException("$mode needs no pluggable transport")

        startedPorts[transport]?.let { return it }

        val ctrl = controller(context)

        if (transport == IPtProxy.IPtProxy.Snowflake) {
            val line = bridgeLines.firstOrNull()
                ?: throw TorBridges.BridgeConfigError("No snowflake bridge line configured.")
            TorBridges.param(line, "url")?.let { ctrl.snowflakeBrokerUrl = it }
            // "fronts" (plural) is current; "front" appears in older lines.
            (TorBridges.param(line, "fronts") ?: TorBridges.param(line, "front"))
                ?.let { ctrl.snowflakeFrontDomains = it }
            TorBridges.param(line, "ice")?.let { ctrl.snowflakeIceServers = it }
            TorBridges.param(line, "ampcache")?.let { ctrl.snowflakeAmpCacheUrl = it }
            ctrl.snowflakeMaxPeers = 1
        }

        try {
            // Empty string, not null: the argument crosses into Go, where a null
            // String has no representation.
            ctrl.start(transport, "")
        } catch (e: Exception) {
            throw TorBridges.BridgeConfigError(
                "Could not start the $transport transport: ${e.message ?: "unknown error"}"
            )
        }

        val port = ctrl.port(transport).toInt()
        if (port <= 0) {
            runCatching { ctrl.stop(transport) }
            throw TorBridges.BridgeConfigError("The $transport transport did not open a port.")
        }
        dlog("$transport listening on ${TorManager.LOOPBACK}:$port")
        startedPorts[transport] = port
        return port
    }

    /**
     * Stop every started transport. Not needed on the panic path — that kills
     * the process, which takes the in-process transports with it — but keeps
     * repeated start attempts from leaking listeners.
     */
    @Synchronized
    fun stopAll() {
        val ctrl = controller ?: return
        startedPorts.keys.toList().forEach { transport ->
            runCatching { ctrl.stop(transport) }
        }
        startedPorts.clear()
    }
}
