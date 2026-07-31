package com.haze.mobile.net

import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting

/**
 * Emits `TorSetting`s for the three options kmp-tor cannot express yet:
 * `UseBridges`, `ClientTransportPlugin` and `Bridge`.
 *
 * WHY THIS FILE EXISTS
 * --------------------
 * kmp-tor 2.6.0 declares those options as `TorOption` objects (so they can be
 * queried) but implements no `Configure*` contract for any of them, and its
 * maintainer has ruled out letting consumers write torrc directly — see
 * https://github.com/05nelsonm/kmp-tor/issues/626. The only public way into the
 * config is `TorConfig.BuilderScope.put(TorSetting)`, and the factories that
 * build a `TorSetting` (`TorOption.toLineItem`, `Set<LineItem>.toSetting`) are
 * `internal`.
 *
 * Kotlin's `internal` is module-scoped, so the build grants this module friend
 * access to kmp-tor's runtime-core jar via `-Xfriend-paths` (wired up in
 * app/build.gradle.kts). That keeps the calls below **compile-time checked**:
 * reflection would have been the alternative, and it would break silently at
 * runtime — on a censored network, silently — whereas a kmp-tor upgrade that
 * changes these factories fails the build instead.
 *
 * When kmp-tor ships first-class bridge support, delete this file and configure
 * the options through its DSL.
 */
internal object TorConfigCompat {

    private fun setting(option: TorOption, argument: String): TorSetting {
        val item = with(TorSetting.LineItem.Companion) { option.toLineItem(argument) }
        return with(TorSetting.Companion) { setOf(item).toSetting() }
    }

    /** `UseBridges 1` — without it tor ignores every `Bridge` line. */
    fun useBridges(): TorSetting = setting(TorOption.UseBridges, "1")

    /**
     * `ClientTransportPlugin <transport> socks5 <host>:<port>` — the "external
     * proxy" form. tor speaks SOCKS5 to an already-running transport instead of
     * spawning one, which is the only option on Android where the transports
     * live in-process inside IPtProxy rather than as executable files.
     */
    fun clientTransportPlugin(transport: String, host: String, port: Int): TorSetting =
        setting(TorOption.ClientTransportPlugin, "$transport socks5 $host:$port")

    /** One `Bridge <line>` entry. `Bridge` is not unique, so call per line. */
    fun bridge(line: String): TorSetting = setting(TorOption.Bridge, line)
}
