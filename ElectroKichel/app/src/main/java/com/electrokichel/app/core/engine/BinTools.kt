package com.electrokichel.app.core.engine

import com.electrokichel.app.core.data.*
import kotlin.math.abs

/** Búsqueda heurística de VGH/VGL en archivos BIN desconocidos (Precisión). */
object PrecisionSearch {
    data class Candidate(val offset: Int, val vghV: Double, val vglV: Double, val score: Double)

    fun search(bytes: ByteArray, targetVgh: Double, targetVgl: Double, bruteForce: Boolean): List<Candidate> {
        val vgh = Repo.samsungVgh.associateBy { it.hex }
        val vgl = Repo.samsungVgl.associateBy { it.hex }
        val found = ArrayList<Candidate>()
        var sectorStart = 0
        while (sectorStart < bytes.size) {
            val sectorEnd = minOf(sectorStart + 4096, bytes.size)
            var pos = sectorStart
            while (pos < sectorEnd) {
                val ve = vgh[bytes[pos].toInt() and 0xFF]
                if (ve != null) {
                    var d = 1
                    while (d < 17 && pos + d < sectorEnd) {
                        val le = vgl[bytes[pos + d].toInt() and 0xFF]
                        if (le != null) {
                            val score = if (bruteForce) 0.0 else abs(ve.volts - targetVgh) + abs(le.volts - targetVgl)
                            found.add(Candidate(pos, ve.volts, le.volts, score))
                        }
                        d++
                    }
                }
                pos++
            }
            sectorStart += 4096
        }
        return found.sortedBy { it.score }.take(3)
    }
}

/** Reparación de "pantalla partida" (perfiles por tamaño y patrón). */
object DivisionRepair {
    data class Match(val profile: DivisionProfile, val position: Int)

    fun find(data: ByteArray): Match? {
        for (p in Repo.divisionProfiles) {
            if (p.size != data.size) continue
            if (p.startPos != null && p.startPos >= 0 && p.pattern == null) return Match(p, p.startPos)
            if (p.pattern != null) {
                val pos = searchPattern(data, p.pattern)
                if (pos >= 0) return Match(p, pos)
            }
        }
        return null
    }

    fun apply(data: ByteArray, m: Match) {
        val p = m.profile
        if (p.parches != null) {
            for (part in p.parches) when (val d = part.data) {
                is PatchData.Bytes -> for (i in d.v.indices) data[part.offset + i] = d.v[i].toByte()
                is PatchData.Text -> { val b = d.v.toByteArray(); for (i in b.indices) data[part.offset + i] = b[i] }
                is PatchData.Byte -> data[part.offset] = d.v.toByte()
            }
            return
        }
        val target = p.vghOffset + m.position
        if (target in data.indices) data[target] = p.vghValue.toByte()
    }

    fun searchPattern(data: ByteArray, pattern: IntArray): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1
        val limit = data.size - pattern.size
        for (start in 0..limit) {
            var ok = true
            for (j in pattern.indices) if ((data[start + j].toInt() and 0xFF) != pattern[j]) { ok = false; break }
            if (ok) return start
        }
        return -1
    }
}

/** Comparador de dos BIN byte a byte. */
object BinCompare {
    data class Diff(val offset: Int, val a: Int, val b: Int)
    fun compare(a: ByteArray, b: ByteArray, max: Int = 500): List<Diff> {
        val out = ArrayList<Diff>()
        val n = minOf(a.size, b.size)
        for (i in 0 until n) if (a[i] != b[i]) {
            out.add(Diff(i, a[i].toInt() and 0xFF, b[i].toInt() and 0xFF))
            if (out.size >= max) break
        }
        return out
    }
}

/** EDID HDMI (I2C 0x50). */
object Edid {
    val HEADER = intArrayOf(0, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0)
    fun headerOk(d: ByteArray) = d.size >= 8 && (0 until 8).all { (d[it].toInt() and 0xFF) == HEADER[it] }
    fun checksumOk(d: ByteArray): Boolean = d.size >= 128 && (0 until 128).sumOf { d[it].toInt() and 0xFF } % 256 == 0
    fun fixChecksum(d: ByteArray) { if (d.size >= 128) { val s = (0 until 127).sumOf { d[it].toInt() and 0xFF }; d[127] = ((256 - s % 256) % 256).toByte() } }
}
