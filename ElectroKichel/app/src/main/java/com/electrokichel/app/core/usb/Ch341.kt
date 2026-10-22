package com.electrokichel.app.core.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlin.math.min

/**
 * Driver del CH341A en modo SPI e I2C, reimplementado a partir de la especificación.
 * Todo va por transferencias bulk: OUT al endpoint 0x02, IN desde el 0x82, paquetes de hasta 32 bytes.
 */
class Ch341 private constructor(
    private val conn: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val epOut: UsbEndpoint,
    private val epIn: UsbEndpoint,
) {
    @Volatile var isOpen = true; private set

    // ---- Comandos del CH341 ----
    private val CMD_SPI = 0xA8.toByte()
    private val CMD_I2C = 0xAA.toByte()
    private val CMD_UIO = 0xAB.toByte()
    private val I2C_STA = 0x74.toByte()
    private val I2C_STO = 0x75.toByte()
    private val I2C_OUT = 0x80
    private val I2C_IN = 0xC0

    companion object {
        const val VID = 0x1A86
        val PIDS = setOf(0x5512, 0x5523, 0x7523)

        fun open(mgr: UsbManager, dev: UsbDevice): Ch341? {
            if (dev.vendorId != VID) return null
            val conn = mgr.openDevice(dev) ?: return null
            var chosen: UsbInterface? = null
            var out: UsbEndpoint? = null
            var inn: UsbEndpoint? = null
            for (i in 0 until dev.interfaceCount) {
                val itf = dev.getInterface(i)
                var o: UsbEndpoint? = null; var n: UsbEndpoint? = null
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_OUT) o = ep
                        if (ep.direction == UsbConstants.USB_DIR_IN) n = ep
                    }
                }
                if (o != null && n != null) { chosen = itf; out = o; inn = n; break }
            }
            if (chosen == null || out == null || inn == null) { conn.close(); return null }
            if (!conn.claimInterface(chosen, true)) { conn.close(); return null }
            conn.controlTransfer(0x40, 0xA1, 0, 0, null, 0, 100)
            conn.controlTransfer(0xC0, 0x5F, 0, 0, ByteArray(8), 2, 1000)
            val c = Ch341(conn, chosen, out, inn)
            c.bulkOut(byteArrayOf(0xAA.toByte(), 0x61.toByte(), 0))       // I2C set 100 kHz
            c.bulkOut(byteArrayOf(0xAB.toByte(), 0xB7.toByte(), 0x7F, 0x20)) // UIO: CS alto
            return c
        }
    }

    private fun bulkOut(data: ByteArray): Int =
        if (!isOpen) -1 else conn.bulkTransfer(epOut, data, data.size, 2000)

    private fun bulkIn(buf: ByteArray, len: Int): Int =
        if (!isOpen) -1 else conn.bulkTransfer(epIn, buf, len, 3000)

    /** Vacía bytes viejos del endpoint IN (evita lecturas desalineadas). */
    fun drainIn() {
        val buf = ByteArray(64)
        repeat(4) { if (!isOpen || conn.bulkTransfer(epIn, buf, buf.size, 5) <= 0) return }
    }

    fun close() {
        if (!isOpen) return
        runCatching { bulkOut(byteArrayOf(CMD_UIO, 0xB7.toByte(), 0x40, 0x20)) }
        runCatching { conn.releaseInterface(iface) }
        runCatching { conn.close() }
        isOpen = false
    }

    // =========================== SPI ===========================

    private fun swap(x: Int): Int {
        var v = x and 0xFF
        v = ((v shr 1) and 0x55) or ((v shl 1) and 0xAA)
        v = ((v shr 2) and 0x33) or ((v shl 2) and 0xCC)
        return ((v shr 4) and 0x0F) or ((v shl 4) and 0xF0) and 0xFF
    }
    private fun csLow() = bulkOut(byteArrayOf(CMD_UIO, 0xB7.toByte(), 0xB7.toByte(), 0xB6.toByte(), 0x20))
    private fun csHigh() { bulkOut(byteArrayOf(CMD_UIO, 0xB7.toByte(), 0x20)) }

    @Synchronized
    fun spi(write: ByteArray, readCount: Int): ByteArray {
        check(isOpen) { "El CH341A no está conectado. Reconéctalo y pulsa Conectar." }
        drainIn()
        csLow()
        val total = write.size + readCount
        val received = ByteArray(total)
        var writeLeft = write.size; var readLeft = readCount
        var srcIdx = 0; var recvIdx = 0
        while (writeLeft > 0 || readLeft > 0) {
            val writeNow = min(31, writeLeft)
            val readNow = min(31 - writeNow, readLeft)
            if (writeNow == 0 && readNow == 0) break
            val packet = ByteArray(writeNow + 1 + readNow)
            packet[0] = CMD_SPI
            for (i in 0 until writeNow) packet[i + 1] = swap(write[srcIdx + i].toInt() and 0xFF).toByte()
            for (i in 0 until readNow) packet[writeNow + 1 + i] = 0xFF.toByte()
            bulkOut(packet)
            val replyLen = writeNow + readNow
            val reply = ByteArray(replyLen)
            var got = 0; var attempts = 0
            while (got < replyLen && attempts < 8) {
                val chunk = ByteArray(replyLen - got)
                val n = bulkIn(chunk, chunk.size)
                if (n <= 0) break
                System.arraycopy(chunk, 0, reply, got, n); got += n; attempts++
            }
            if (got < replyLen) { csHigh(); error("Lectura/escritura SPI incompleta por USB (esperados $replyLen, llegaron $got). Revisa el cable OTG y el contacto del CH341A.") }
            System.arraycopy(reply, 0, received, recvIdx, replyLen)
            recvIdx += replyLen; srcIdx += writeNow; writeLeft -= writeNow; readLeft -= readNow
        }
        csHigh()
        val out = ByteArray(readCount)
        val offset = total - readCount
        for (i in 0 until readCount) out[i] = swap(received[offset + i].toInt() and 0xFF).toByte()
        return out
    }

    fun releasePowerDown() { runCatching { spi(byteArrayOf(0xAB.toByte()), 0); Thread.sleep(1) } }

    /** JEDEC ID -> Triple(fabricante, tipo, capacidad). */
    fun readJedec(): Triple<Int, Int, Int> {
        releasePowerDown()
        val r = spi(byteArrayOf(0x9F.toByte()), 3)
        return Triple(r[0].toInt() and 0xFF, r[1].toInt() and 0xFF, r[2].toInt() and 0xFF)
    }

    fun detectCapacityBytes(): Int? {
        val (man, _, cap) = readJedec()
        if (man == 0 || man == 0xFF) return null
        if (cap < 16 || cap > 32) return null
        return 1 shl cap
    }

    fun readStatus(): Int = spi(byteArrayOf(0x05), 1)[0].toInt() and 0xFF
    fun waitBusy() {
        repeat(20000) { if (readStatus() and 1 == 0) return; Thread.sleep(1) }
        error("El chip nunca reportó 'listo' (bit BUSY). Revisa el cableado SPI y la alimentación.")
    }
    fun writeEnable() { spi(byteArrayOf(0x06), 0) }
    private fun unlock() { writeEnable(); spi(byteArrayOf(0x01, 0x00), 0); waitBusy() }

    fun readBytes(addr: Long, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val thisLen = min(64, length - offset)
            val a = addr + offset
            val cmd = byteArrayOf(0x03, ((a shr 16) and 0xFF).toByte(), ((a shr 8) and 0xFF).toByte(), (a and 0xFF).toByte())
            var chunk: ByteArray? = null; var attempt = 1; var last: Exception? = null
            while (attempt < 4) {
                try { chunk = spi(cmd, thisLen); last = null; break }
                catch (e: Exception) { last = e; if (attempt < 3) Thread.sleep((attempt * 25).toLong()); attempt++ }
            }
            chunk ?: throw (last ?: IllegalStateException("Fallo de lectura SPI"))
            System.arraycopy(chunk, 0, result, offset, thisLen); offset += thisLen
        }
        return result
    }

    fun readSector(addr: Long): ByteArray = readBytes(addr and 0xFFFFF000L.toLong() and 0xFFFFFFFFL, 4096)

    fun writeSector(addr: Long, data: ByteArray) {
        require(data.size == 4096) { "El buffer debe tener exactamente 4096 bytes" }
        val sectorStart = addr and 0xFFFFF000L
        var attempt = 1; var lastMismatch = -1; var lastCount = 0
        while (attempt < 3) {
            unlock(); writeEnable()
            spi(byteArrayOf(0x20, ((sectorStart shr 16) and 0xFF).toByte(), ((sectorStart shr 8) and 0xFF).toByte(), (sectorStart and 0xFF).toByte()), 0)
            waitBusy()
            var p = 0
            while (p < 4096) {
                val pageAddr = sectorStart + p
                writeEnable()
                val header = byteArrayOf(0x02, ((pageAddr shr 16) and 0xFF).toByte(), ((pageAddr shr 8) and 0xFF).toByte(), (pageAddr and 0xFF).toByte())
                spi(header + data.copyOfRange(p, p + 256), 0)
                waitBusy(); p += 256
            }
            Thread.sleep(3)
            val readBack = readBytes(sectorStart, 4096)
            var count = 0; var first = -1
            for (i in 0 until 4096) if (readBack[i] != data[i]) { count++; if (first < 0) first = i }
            if (count == 0) return
            lastMismatch = first; lastCount = count; attempt++
        }
        error("La escritura no se pudo verificar: $lastCount de 4096 bytes no coinciden (primera diferencia en 0x${lastMismatch.toString(16)}). Revisa el pin WP, el cableado SPI o el formato del registro de estado.")
    }

    // =========================== I2C ===========================

    fun setI2cSpeed(speed: Int) { bulkOut(byteArrayOf(CMD_I2C, (0x60 or (speed and 3)).toByte(), 0)) }

    /** Lectura I2C con dirección de memoria de 1 o 2 bytes (24Cxx, EDID, PMIC). */
    fun i2cRead(dev: Int, memAddr: Int, length: Int, addrBytes: Int): ByteArray {
        drainIn()
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val addr = memAddr + offset
            var chunk = min(27, length - offset)
            if (addrBytes == 1) chunk = min(chunk, 256 - (addr and 0xFF))
            val effAddr = if (addrBytes == 1) dev or ((addr shr 8) and 7) else dev
            val w = arrayListOf(CMD_I2C, I2C_STA, (I2C_OUT or (1 + addrBytes)).toByte(), ((effAddr shl 1) or 0).toByte())
            if (addrBytes == 2) w.add(((addr shr 8) and 0xFF).toByte())
            w.add((addr and 0xFF).toByte()); w.add(0)
            bulkOut(w.toByteArray())
            val r = arrayListOf(CMD_I2C, I2C_STA, (I2C_OUT or 1).toByte(), ((effAddr shl 1) or 1).toByte())
            repeat(chunk - 1) { r.add((I2C_IN or 1).toByte()) }
            r.add(I2C_IN.toByte()); r.add(I2C_STO); r.add(0)
            if (bulkOut(r.toByteArray()) >= 0) {
                val resp = ByteArray(chunk); var got = 0; var attempts = 0
                while (got < chunk && attempts < 8) {
                    val piece = ByteArray(chunk - got)
                    val n = bulkIn(piece, piece.size)
                    if (n <= 0) { attempts++; continue }
                    System.arraycopy(piece, 0, resp, got, n); got += n; attempts++
                }
                System.arraycopy(resp, 0, result, offset, min(chunk, got))
            }
            offset += chunk
        }
        return result
    }

    /** Escritura I2C por páginas. */
    fun i2cWrite(dev: Int, memAddr: Int, data: ByteArray, addrBytes: Int, pageSize: Int, pageDelayMs: Long) {
        var offset = 0
        while (offset < data.size) {
            val addr = memAddr + offset
            var n = min(pageSize, data.size - offset)
            if (addrBytes == 1) n = min(n, 256 - (addr and 0xFF))
            val effAddr = if (addrBytes == 1) dev or ((addr shr 8) and 7) else dev
            val w = arrayListOf(CMD_I2C, I2C_STA, (I2C_OUT or (1 + addrBytes + n)).toByte(), ((effAddr shl 1) or 0).toByte())
            if (addrBytes == 2) w.add(((addr shr 8) and 0xFF).toByte())
            w.add((addr and 0xFF).toByte())
            for (i in 0 until n) w.add(data[offset + i])
            w.add(I2C_STO); w.add(0)
            bulkOut(w.toByteArray())
            if (pageDelayMs > 0) Thread.sleep(pageDelayMs)
            offset += n
        }
    }

    /** Escribe un registro de 1 byte (PMIC). */
    fun i2cWriteReg(dev: Int, reg: Int, value: Int) =
        i2cWrite(dev, reg, byteArrayOf(value.toByte()), 1, 1, 0)

    /** Lee N bytes desde un registro (PMIC, bloque). */
    fun i2cReadReg(dev: Int, reg: Int, length: Int): ByteArray = i2cRead(dev, reg, length, 1)

    /** ¿Responde (ACK) la dirección? */
    fun i2cProbe(dev: Int): Boolean? {
        drainIn()
        val pkt = byteArrayOf(CMD_I2C, I2C_STA, I2C_OUT.toByte(), ((dev shl 1) or 0).toByte(), I2C_STO, 0)
        if (bulkOut(pkt) < 0) return null
        val buf = ByteArray(32)
        val n = bulkIn(buf, buf.size)
        if (n <= 0) return null
        return (buf[0].toInt() and 0x80) == 0
    }
}
