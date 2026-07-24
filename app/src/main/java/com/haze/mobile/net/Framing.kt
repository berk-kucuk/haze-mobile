package com.haze.mobile.net

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire framing — mirrors Haze's `network/protocol.py`:
 *   4-byte big-endian length prefix + JSON payload, max 1 MB.
 */
object Framing {
    const val MAX_SIZE = 1 * 1024 * 1024

    fun writeMessage(out: OutputStream, payload: ByteArray) {
        if (payload.size > MAX_SIZE) throw IOException("Message exceeds max size")
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    fun readMessage(inp: InputStream): ByteArray {
        val header = readExactly(inp, 4)
        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length < 0 || length > MAX_SIZE) throw IOException("Incoming message exceeds max size")
        return readExactly(inp, length)
    }

    private fun readExactly(inp: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = inp.read(buf, read, n - read)
            if (r < 0) throw EOFException("Stream closed while reading $n bytes")
            read += r
        }
        return buf
    }
}
