package com.electrokichel.app.core.engine

import com.electrokichel.app.core.data.FlashModel
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.data.VgEntry
import com.electrokichel.app.core.usb.Ch341

/** Editor VGH/VGL de paneles Samsung (lee/escribe solo los bytes del voltaje, con checksum). */
object SamsungEditor {

    fun vghTable(m: FlashModel): List<VgEntry> = m.customVgh ?: Repo.samsungVgh
    fun vglTable(m: FlashModel): List<VgEntry> = m.customVgl ?: Repo.samsungVgl

    fun indexOfHex(table: List<VgEntry>, hex: Int): Int =
        table.indexOfFirst { it.hex == hex }.let { if (it < 0) 0 else it }

    fun clamp(table: List<VgEntry>, idx: Int) = idx.coerceIn(0, table.size - 1)

    /** Autodetección por firma de los primeros 16 bytes del bloque. */
    fun detectModel(scanBuf: ByteArray): FlashModel? {
        if (scanBuf.size < 16) return null
        val allFF = (0 until 16).all { (scanBuf[it].toInt() and 0xFF) == 0xFF }
        val all00 = (0 until 16).all { (scanBuf[it].toInt() and 0xFF) == 0 }
        if (allFF || all00) return null
        for (sig in Repo.signatures) {
            val len = if (sig.len in 1..64) sig.len else 16
            var match = true
            for (i in 0 until len) if ((scanBuf[i].toInt() and 0xFF) != sig.pattern[i]) { match = false; break }
            if (match) return Repo.samsungModels.firstOrNull { it.name == sig.modelName }
        }
        return null
    }

    data class Reading(val vghIdx: Int, val vglIdx: Int, val vghV: Double, val vglV: Double, val vghChk: Int?, val vglChk: Int?)

    fun readFromChip(ch: Ch341, m: FlashModel): Reading {
        val vghByte: Int; val vglByte: Int; var vc: Int? = null; var lc: Int? = null
        if (m.i2c) {
            vghByte = ch.i2cRead(m.i2cAddr, m.baseMain.toInt() + m.vghPos, 1, 2)[0].toInt() and 0xFF
            vglByte = ch.i2cRead(m.i2cAddr, m.baseMain.toInt() + m.vglPos, 1, 2)[0].toInt() and 0xFF
        } else {
            vghByte = ch.readBytes(m.baseMain + m.vghPos, 1)[0].toInt() and 0xFF
            vglByte = ch.readBytes(m.baseMain + m.vglPos, 1)[0].toInt() and 0xFF
            if (m.checksum) {
                vc = ch.readBytes(m.baseCheck + m.vghChk, 1)[0].toInt() and 0xFF
                lc = ch.readBytes(m.baseCheck + m.vglChk, 1)[0].toInt() and 0xFF
            }
        }
        val vt = vghTable(m); val lt = vglTable(m)
        val vi = indexOfHex(vt, vghByte); val li = indexOfHex(lt, vglByte)
        return Reading(vi, li, vt[vi].volts, lt[li].volts, vc, lc)
    }

    /** Graba en el chip los nuevos índices (SPI: lee el sector, modifica, reescribe). */
    fun writeToChip(ch: Ch341, m: FlashModel, vghIdx: Int, vglIdx: Int, prevVghChk: Int?, prevVglChk: Int?) {
        val vt = vghTable(m); val lt = vglTable(m)
        val newVgh = vt[clamp(vt, vghIdx)].hex
        val newVgl = lt[clamp(lt, vglIdx)].hex
        if (m.i2c) {
            ch.i2cWrite(m.i2cAddr, m.baseMain.toInt() + m.vghPos, byteArrayOf(newVgh.toByte()), 2, 1, 5)
            ch.i2cWrite(m.i2cAddr, m.baseMain.toInt() + m.vglPos, byteArrayOf(newVgl.toByte()), 2, 1, 5)
            return
        }
        val full = ch.readSector(m.baseMain)
        val oM = (m.baseMain and 0xFFF).toInt()
        val oldVgh = full[m.vghPos + oM].toInt() and 0xFF
        val oldVgl = full[m.vglPos + oM].toInt() and 0xFF
        full[m.vghPos + oM] = newVgh.toByte()
        full[m.vglPos + oM] = newVgl.toByte()
        if (m.checksum && prevVghChk != null && prevVglChk != null) {
            // Compensa el checksum por la diferencia del valor hex grabado
            val oC = (m.baseCheck and 0xFFF).toInt()
            full[m.vghChk + oC] = ((prevVghChk - (newVgh - oldVgh)) and 0xFF).toByte()
            full[m.vglChk + oC] = ((prevVglChk - (newVgl - oldVgl)) and 0xFF).toByte()
        }
        ch.writeSector(m.baseMain, full)
    }
}
