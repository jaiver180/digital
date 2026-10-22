package com.electrokichel.app.core.adb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Cliente ADB mínimo sobre TCP: CNXN, AUTH (firma RSA) y shell. */
class AdbConnection(private val host: String, private val port: Int, private val crypto: AdbCrypto) {
    private lateinit var socket: Socket
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private var nextLocalId = 1

    companion object {
        const val A_CNXN = 0x4E584E43; const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4E45504F; const val A_OKAY = 0x59414B4F
        const val A_WRTE = 0x45545257; const val A_CLSE = 0x45534C43
        const val AUTH_TOKEN = 1; const val AUTH_SIGNATURE = 2; const val AUTH_RSAPUBLICKEY = 3
    }

    class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    fun connect() {
        socket = Socket(); socket.connect(InetSocketAddress(host, port), 8000); socket.tcpNoDelay = true
        input = DataInputStream(socket.getInputStream()); output = DataOutputStream(socket.getOutputStream())
        val payload = "host::features=cmd,shell_v2\u0000".toByteArray()
        writeMessage(A_CNXN, 0x01000000, 1048576, payload)
        var authAttempts = 0
        while (true) {
            val m = readMessage()
            when (m.command) {
                A_CNXN -> return
                A_AUTH -> {
                    if (m.arg0 == AUTH_TOKEN) {
                        if (authAttempts++ == 0) writeMessage(A_AUTH, AUTH_SIGNATURE, 0, crypto.signPayload(m.data))
                        else writeMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0, crypto.adbPublicKey())
                    }
                }
                else -> throw RuntimeException("Respuesta inesperada del protocolo ADB (0x%08X)".format(m.command))
            }
        }
    }

    fun shell(command: String): String {
        val localId = nextLocalId++
        writeMessage(A_OPEN, localId, 0, "shell:$command\u0000".toByteArray())
        val sb = StringBuilder()
        while (true) {
            val m = readMessage()
            when (m.command) {
                A_WRTE -> { sb.append(String(m.data)); writeMessage(A_OKAY, localId, m.arg0, ByteArray(0)) }
                A_CLSE -> { writeMessage(A_CLSE, localId, m.arg0, ByteArray(0)); break }
                A_OKAY -> {}
            }
        }
        return sb.toString()
    }

    fun close() { runCatching { socket.close() } }

    private fun writeMessage(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command); header.putInt(arg0); header.putInt(arg1)
        header.putInt(data.size)
        header.putInt(checksum(data))
        header.putInt(command xor -0x1)
        output.write(header.array()); if (data.isNotEmpty()) output.write(data); output.flush()
    }

    private fun readMessage(): Message {
        val head = ByteArray(24); input.readFully(head)
        val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val command = bb.int; val arg0 = bb.int; val arg1 = bb.int; val len = bb.int
        bb.int; bb.int
        val data = if (len > 0) ByteArray(len).also { input.readFully(it) } else ByteArray(0)
        return Message(command, arg0, arg1, data)
    }

    private fun checksum(data: ByteArray): Int { var s = 0; for (b in data) s += b.toInt() and 0xFF; return s }
}
