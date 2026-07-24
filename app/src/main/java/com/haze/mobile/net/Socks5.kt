package com.haze.mobile.net

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal SOCKS5 client used to reach a `.onion` host through Tor's SOCKS proxy
 * (Orbot, default 127.0.0.1:9050). The hostname is sent as a SOCKS domain
 * (ATYP 0x03) so Tor — not the device — resolves the onion address.
 */
object Socks5 {

    fun connect(
        proxyHost: String,
        proxyPort: Int,
        destHost: String,
        destPort: Int,
        connectTimeoutMs: Int,
    ): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress(proxyHost, proxyPort), connectTimeoutMs)
        try {
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            // Greeting: VER=5, 1 method, METHOD=0 (no auth)
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val greeting = readExactly(inp, 2)
            if (greeting[0].toInt() != 0x05 || greeting[1].toInt() != 0x00) {
                throw IOException("SOCKS5 method negotiation failed")
            }

            // CONNECT: VER=5, CMD=1, RSV=0, ATYP=3 (domain), LEN, host, port
            val host = destHost.toByteArray(Charsets.US_ASCII)
            if (host.size > 255) throw IOException("Onion host too long")
            val req = ByteArray(4 + 1 + host.size + 2)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03
            req[4] = host.size.toByte()
            System.arraycopy(host, 0, req, 5, host.size)
            req[5 + host.size] = ((destPort shr 8) and 0xFF).toByte()
            req[6 + host.size] = (destPort and 0xFF).toByte()
            out.write(req)
            out.flush()

            // Reply: VER, REP, RSV, ATYP, BND.ADDR, BND.PORT
            val reply = readExactly(inp, 4)
            if (reply[1].toInt() != 0x00) {
                throw IOException("SOCKS5 connect failed (code ${reply[1].toInt() and 0xFF})")
            }
            when (reply[3].toInt() and 0xFF) {
                0x01 -> readExactly(inp, 4 + 2)   // IPv4 + port
                0x04 -> readExactly(inp, 16 + 2)  // IPv6 + port
                0x03 -> {
                    val len = readExactly(inp, 1)[0].toInt() and 0xFF
                    readExactly(inp, len + 2)     // domain + port
                }
                else -> throw IOException("SOCKS5 unknown address type")
            }
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    private fun readExactly(inp: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = inp.read(buf, read, n - read)
            if (r < 0) throw EOFException("SOCKS stream closed early")
            read += r
        }
        return buf
    }
}
