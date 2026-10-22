package com.electrokichel.app.core.engine

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/** Autoscan Samsung: identifica el modelo a usar a partir de un .bin. Puerto de BinScanner. */
class AutoScan(ctx: Context) {
    private val filesDir = ctx.filesDir
    private val db = ModelDb().apply { load(ctx.assets.open("autoscan_modelos_samsung.txt").bufferedReader().readText()) }
    private val fps = FpStore().apply {
        runCatching { load(ctx.assets.open("autoscan_firmas.txt").bufferedReader().readText()) }
        File(filesDir, "firmas_usuario.txt").takeIf { it.exists() }?.let { load(it.readText()) }
    }

    enum class Mode { EXACTO, ELEGIR, NINGUNO }
    data class Candidate(val group: ModelDb.Group, val vgh: Int, val vgl: Int, val fp: FpStore.Fp?, val nameHits: List<String>)
    data class Result(val mode: Mode, val finalSet: List<Candidate>, val reason: String, val warning: String?, val log: List<String>)

    fun groups() = db.groups
    fun entries() = db.entries
    fun importModels(text: String): Int = db.load(text)
    fun importFirmas(text: String): Int = fps.load(text)
    fun exportFirmas(): String = fps.export()
    fun clearFirmas() { File(filesDir, "firmas_usuario.txt").delete(); fps.clear(); runCatching { fps.load(javaClass.classLoader!!.getResourceAsStream("autoscan_firmas.txt")?.bufferedReader()?.readText() ?: "") } }

    fun saveReference(bin: ByteArray, g: ModelDb.Group, model: String): Boolean {
        val fp = FpStore.capture(bin, g, model) ?: return false
        if (!fps.add(fp)) return false
        File(filesDir, "firmas_usuario.txt").writeText(fps.export()); return true
    }

    fun scan(bin: ByteArray, fileName: String?): Result {
        val log = ArrayList<String>()
        val normFile = ModelDb.normalize(stripExt(fileName ?: ""))
        val valid = ArrayList<Candidate>()
        for (g in db.groups) {
            val e = g.recipe
            if (g.maxAddr() >= bin.size || e.base + 16 > bin.size) { log.add("G${g.number}: fuera del tamaño"); continue }
            if (isBlank(bin, e.base, 16)) { log.add("G${g.number}: bloque vacío"); continue }
            val vgh = bin[(e.base + e.vghPos).toInt()].toInt() and 0xFF
            val vgl = bin[(e.base + e.vglPos).toInt()].toInt() and 0xFF
            if (empty(vgh) || empty(vgl)) { log.add("G${g.number}: celda vacía"); continue }
            val fp = fps.match(bin, g)
            val nameHits = g.models.filter { nameMatches(normFile, it) }
            valid.add(Candidate(g, vgh, vgl, fp, nameHits))
            log.add("G${g.number}: VÁLIDO VGH=%02X VGL=%02X".format(vgh, vgl) + if (fp != null) " [firma ${fp.model}]" else "")
        }
        if (valid.isEmpty()) return Result(Mode.NINGUNO, emptyList(), "Ninguna dirección conocida tiene VGH/VGL con datos válidos.", null, log)

        var set = ArrayList(valid)
        val why = ArrayList<String>()
        set.filter { it.fp != null }.let { x -> if (x.isNotEmpty() && (x.size < set.size || x.size == 1)) { set = ArrayList(x); why.add("coincide con la firma de un BIN real (${x[0].fp!!.model})") } }
        if (set.size > 1 && normFile.isNotEmpty())
            set.filter { it.nameHits.isNotEmpty() }.let { x -> if (x.isNotEmpty() && x.size < set.size) { set = ArrayList(x); why.add("el nombre del archivo corresponde al modelo ${x[0].nameHits[0]}") } }
        if (set.size > 1 && normFile.isNotEmpty()) {
            val x = set.filter { c -> c.group.models.any { ModelDb.seriesOf(it)?.let { s -> containsToken(normFile, s) } == true } }
            if (x.isNotEmpty() && x.size < set.size) { set = ArrayList(x); why.add("el nombre del archivo tiene la serie") }
        }
        if (set.size > 1) {
            val text = String(bin, Charsets.ISO_8859_1).uppercase(Locale.US)
            val x = set.filter { c -> c.group.models.any { ModelDb.coreOf(it).length >= 9 && text.contains(ModelDb.coreOf(it)) } }
            if (x.size == 1) { set = ArrayList(x); why.add("dentro del BIN aparece el modelo") }
        }

        val warning = computeWarning(normFile, set)
        return if (set.size == 1) {
            val reason = if (why.isEmpty()) "Única dirección con VGH/VGL válidos." else "Detectado porque ${why.joinToString(" y ")}."
            Result(Mode.EXACTO, set, reason, warning, log)
        } else {
            val reason = "Quedan ${set.size} grupos con datos válidos (OPC1/OPC2, 25V/30V). Escoge el de tu TV. Si lo guardas como referencia, la próxima vez será automático."
            Result(Mode.ELEGIR, set, reason, warning, log)
        }
    }

    private fun computeWarning(normFile: String, set: List<Candidate>): String? {
        if (normFile.isEmpty()) return null
        val named = db.entries.filter { nameMatches(normFile, it.name) }.map { it.name }.distinct()
        if (named.isEmpty()) return null
        val inResult = set.any { c -> named.any { c.group.models.contains(it) } }
        return if (!inResult) "OJO: el nombre indica ${named.joinToString(", ")}, pero en ESAS direcciones el BIN no tiene VGH/VGL válidos." else null
    }

    private fun nameMatches(normFile: String, model: String): Boolean {
        if (normFile.isEmpty()) return false
        val core = ModelDb.normalize(ModelDb.coreOf(model))
        if (core.length >= 7 && containsToken(normFile, core)) return true
        if (core.length >= 9 && core[0].isLetter() && core[1].isLetter()) {
            val np = core.substring(2); var i = normFile.indexOf(np)
            while (i >= 0) {
                val okB = i == 0 || !normFile[i - 1].isDigit()
                val end = i + np.length; val okA = end >= normFile.length || !normFile[end].isDigit()
                if (okB && okA) return true; i = normFile.indexOf(np, i + 1)
            }
        }
        return false
    }
    private fun containsToken(hay: String, token: String): Boolean {
        var i = hay.indexOf(token)
        while (i >= 0) {
            val okB = i == 0 || !hay[i - 1].isLetter()
            val end = i + token.length; val okA = end >= hay.length || !hay[end].isDigit()
            if (okB && okA) return true; i = hay.indexOf(token, i + 1)
        }
        return false
    }
    private fun stripExt(n: String) = n.lastIndexOf('.').let { if (it > 0) n.substring(0, it) else n }
    private fun empty(v: Int) = v == 0xFF || v == 0
    private fun isBlank(b: ByteArray, start: Long, len: Int): Boolean {
        val s = start.toInt(); val end = minOf(b.size, s + len); if (s >= end) return true
        val first = b[s]; if (first.toInt() and 0xFF != 0xFF && first.toInt() != 0) return false
        for (i in s + 1 until end) if (b[i] != first) return false
        return true
    }
}

class ModelDb {
    val entries = ArrayList<Entry>(); val groups = ArrayList<Group>()
    class Entry(val name: String, val base: Long, val vghPos: Int, val vglPos: Int, val hasSecond: Boolean, val base2: Long, val vgh2: Int, val vgl2: Int) {
        fun key() = if (hasSecond) "%08X|%02X|%02X|%08X|%02X|%02X".format(base, vghPos, vglPos, base2, vgh2, vgl2) else "%08X|%02X|%02X|-".format(base, vghPos, vglPos)
    }
    class Group(val number: Int, val recipe: Entry) {
        val models = ArrayList<String>()
        fun maxAddr(): Long { var m = maxOf(recipe.base + recipe.vghPos, recipe.base + recipe.vglPos); if (recipe.hasSecond) m = maxOf(m, recipe.base2 + recipe.vgh2, recipe.base2 + recipe.vgl2); return m }
    }
    fun load(text: String): Int {
        var count = 0
        for (line in text.lineSequence()) { val m = LINE.matcher(line); while (m.find()) { entries.add(Entry(m.group(1).trim(), num(m.group(2)), num(m.group(3)).toInt(), num(m.group(4)).toInt(), m.group(5).toBoolean(), num(m.group(6)), num(m.group(7)).toInt(), num(m.group(8)).toInt())); count++ } }
        rebuild(); return count
    }
    private fun rebuild() {
        groups.clear(); val map = LinkedHashMap<String, Group>()
        for (e in entries) { val g = map.getOrPut(e.key()) { Group(map.size + 1, e) }; if (!g.models.contains(e.name)) g.models.add(e.name) }
        groups.addAll(map.values)
    }
    companion object {
        private val LINE = Pattern.compile("""\{\s*"([^"]+)"\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*,\s*(true|false)\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*,\s*(0[xX][0-9A-Fa-f]+|\d+)\s*\}""")
        private val SERIES = Pattern.compile("""^[A-Z]{2}\d{2}([A-Z]{1,2})(\d{2,4})([A-Z]?)""")
        fun num(s: String): Long { val t = s.trim(); return if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2).toLong(16) else t.toLong() }
        fun normalize(s: String) = s.uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        fun coreOf(m: String) = m.uppercase(Locale.US).trim().split(Regex("[-_ ]"))[0]
        fun seriesOf(m: String): String? { val mm = SERIES.matcher(coreOf(m)); if (!mm.find()) return null; var s = mm.group(1) + mm.group(2); if (mm.group(2).length == 2) s += mm.group(3); return if (s.length >= 4) s else null }
    }
}

class FpStore {
    class Fp(val configKey: String, val model: String, val main: ByteArray, val second: ByteArray?) {
        fun line() = "$configKey;$model;${hex(main)};${second?.let { hex(it) } ?: "-"}"
    }
    private val list = ArrayList<Fp>()
    fun clear() = list.clear()
    fun add(f: Fp): Boolean { if (list.any { it.line() == f.line() }) return false; list.add(f); return true }
    fun match(bin: ByteArray, g: ModelDb.Group): Fp? {
        val e = g.recipe; val key = e.key()
        return list.firstOrNull { it.configKey == key && blockEquals(bin, e.base, it.main, e.vghPos, e.vglPos) && (!e.hasSecond || (it.second != null && blockEquals(bin, e.base2, it.second, e.vgh2, e.vgl2))) }
    }
    fun load(text: String): Int { var n = 0; for (raw in text.lineSequence()) { val l = raw.trim(); if (l.isEmpty() || l.startsWith("#")) continue; val p = l.split(";"); if (p.size == 4) runCatching { val main = fromHex(p[2]); val sec = if (p[3] == "-") null else fromHex(p[3]); if (main.size == 16 && (sec == null || sec.size == 16) && add(Fp(p[0], p[1], main, sec))) n++ } }; return n }
    fun export(): String { val sb = StringBuilder("# Firmas VGH/VGL ElectroKichel\n"); list.forEach { sb.append(it.line()).append('\n') }; return sb.toString() }
    companion object {
        fun capture(bin: ByteArray, g: ModelDb.Group, model: String): Fp? {
            val e = g.recipe; if (e.base + 16 > bin.size) return null
            val main = bin.copyOfRange(e.base.toInt(), e.base.toInt() + 16)
            val sec = if (e.hasSecond) { if (e.base2 + 16 > bin.size) return null; bin.copyOfRange(e.base2.toInt(), e.base2.toInt() + 16) } else null
            return Fp(e.key(), model, main, sec)
        }
        private fun blockEquals(bin: ByteArray, base: Long, ref: ByteArray, skipA: Int, skipB: Int): Boolean {
            if (16 + base > bin.size) return false
            for (i in 0 until 16) if (i != skipA && i != skipB && bin[base.toInt() + i] != ref[i]) return false
            return true
        }
        private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        private fun fromHex(s: String): ByteArray { val t = s.trim(); return ByteArray(t.length / 2) { (t.substring(it * 2, it * 2 + 2).toInt(16)).toByte() } }
    }
}
