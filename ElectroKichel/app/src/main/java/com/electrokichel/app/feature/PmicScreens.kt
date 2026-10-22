package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.data.PmicChip
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.engine.EepromPreset
import com.electrokichel.app.core.engine.Presets
import com.electrokichel.app.core.usb.PmicProtocol
import com.electrokichel.app.core.usb.UsbHub
import com.electrokichel.app.ui.*
import kotlinx.coroutines.launch

/** PMIC LG / Chinos (I2C): lee y graba el bloque, ajusta VGH/VGL/VCOM por byte crudo. */
@androidx.compose.runtime.Composable
fun PmicScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var chip by remember { mutableStateOf<PmicChip?>(null) }
    var block by remember { mutableStateOf<ByteArray?>(null) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }

    ScreenScaffold("PMIC LG / Chinos (I2C)", onBack) {
        InfoCard("Elige el chip exacto impreso en la placa, conecta el CH341, lee el bloque de registros y ajusta VGH/VGL (y VCOM/VGL2 si el chip los tiene). 114 chips soportados. Las tablas de voltaje no vienen incluidas: se editan por byte crudo.")
        Section("Conexión") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (connected) "CONECTADO" else "CONECTAR CH341", if (connected) EkGreen else EkBlue) {
                    scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ CH341 conectado") }.onFailure { log.add("❌ ${it.message}") } }
                }
                EkButton("ESCANEAR I2C", EkTeal, enabled = connected) {
                    scope.launch { runCatching { io { UsbHub.withCh341 { ch -> PmicProtocol(ch).scanBus() } } }
                        .onSuccess { log.add("Responden: " + it.joinToString { a -> "0x%02X".format(a) }) }.onFailure { log.add("❌ ${it.message}") } }
                }
            }
            Spacer(Modifier.height(8.dp))
            Dropdown("Chip PMIC", Repo.pmicChips, chip, { "${it.nombre}  (0x%02X)".format(it.i2cAddr) }) { chip = it }
        }
        chip?.let { c ->
            Section("Bloque de registros") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EkButton("📡 LEER BLOQUE", EkBlue, enabled = connected) {
                        scope.launch { runCatching { io { UsbHub.withCh341 { ch -> PmicProtocol(ch).readBlock(c) } } }
                            .onSuccess { if (it != null) { block = it; log.add("Bloque: ${it.hex()}") } else log.add("❌ Sin lectura válida") }
                            .onFailure { log.add("❌ ${it.message}") } }
                    }
                    EkButton("⚡ GRABAR BLOQUE", EkOrange, enabled = connected && block != null) {
                        scope.launch { runCatching { io { UsbHub.withCh341 { ch -> PmicProtocol(ch).writeBlock(c, block!!) } } }
                            .onSuccess { log.add(if (it) "✅ Grabado (trigger ${c.trigger})" else "❌ Falló la grabación") }.onFailure { log.add("❌ ${it.message}") } }
                    }
                }
                block?.let { blk ->
                    Spacer(Modifier.height(8.dp))
                    val ctx2 = LocalContext.current
                    var tick by remember { mutableStateOf(0) }
                    val vgh = PmicCal.volts(ctx2, c, "VGH", blk[c.vghOff].toInt() and 0xFF)
                    val vgl = PmicCal.volts(ctx2, c, "VGL", blk[c.vglOff].toInt() and 0xFF)
                    if (vgh != null && vgl != null) Text("VPP (pico a pico): %.1f V".format(vgh - vgl), color = EkOrange, fontSize = 16.sp)
                    Text("$tick", color = EkBg, fontSize = 1.sp)
                    RegSlider(c, "VGH", blk, c.vghOff) { tick++ }
                    RegSlider(c, "VGL", blk, c.vglOff) { tick++ }
                    if (c.hasVgl2) RegSlider(c, "VGL2", blk, c.vgl2Off) { tick++ }
                    if (c.hasVcom) RegSlider(c, "VCOM", blk, c.vcomOff) { tick++ }
                    EkButton("⚡ ESCRIBIR AL CHIP (EN VIVO)", EkOrange, enabled = connected, modifier = Modifier.fillMaxWidth()) {
                        scope.launch { runCatching { io { UsbHub.withCh341 { ch ->
                            ch.i2cWrite(c.i2cAddr, 0, blk, 1, blk.size, 0) } } }
                            .onSuccess { log.add("✅ Escrito en vivo (usa GRABAR BLOQUE para guardarlo en la EEPROM)") }.onFailure { log.add("❌ ${it.message}") } }
                    }
                }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

@androidx.compose.runtime.Composable
private fun RegSlider(chip: PmicChip, name: String, block: ByteArray, off: Int, onChange: () -> Unit) {
    if (off !in block.indices) return
    val ctx = LocalContext.current
    var v by remember(name, block) { mutableStateOf(block[off].toInt() and 0xFF) }
    var cal by remember { mutableStateOf(false) }
    val volts = PmicCal.volts(ctx, chip, name, v)
    Text("$name = " + (volts?.let { "%+.1f V   ".format(it) } ?: "") + "(código 0x%02X = %d)".format(v, v), color = EkText, fontSize = 15.sp)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Slider(v.toFloat(), { v = it.toInt(); block[off] = v.toByte(); onChange() }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = { cal = true }) { Text("Calibrar", color = EkBlue, fontSize = 12.sp) }
    }
    if (cal) {
        val cur = PmicCal.get(ctx, chip, name)
        var base by remember { mutableStateOf(cur?.first?.toString() ?: "") }
        var step by remember { mutableStateOf(cur?.second?.toString() ?: "") }
        var meas by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { cal = false },
            title = { Text("Calibrar $name (${chip.nombre})") },
            text = { Column {
                Text("V = base + paso × código", fontSize = 12.sp)
                androidx.compose.material3.OutlinedTextField(base, { base = it }, label = { Text("Base (V)") })
                androidx.compose.material3.OutlinedTextField(step, { step = it }, label = { Text("Paso (V por código)") })
                Text("O: mide con el multímetro y escribe el voltaje actual (código $v); se calcula la base con el paso:", fontSize = 12.sp)
                androidx.compose.material3.OutlinedTextField(meas, { meas = it }, label = { Text("Voltaje medido (V)") })
            } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                val st = step.replace(',', '.').toDoubleOrNull()
                val m = meas.replace(',', '.').toDoubleOrNull()
                val b = if (m != null && st != null) m - st * v else base.replace(',', '.').toDoubleOrNull()
                if (b != null && st != null) PmicCal.set(ctx, chip, name, b, st)
                cal = false; onChange()
            }) { Text("Guardar") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cal = false }) { Text("Cancelar") } })
    }
}

/** Conversión código→voltaje por chip (V = base + paso × código), guardada en el teléfono. */
object PmicCal {
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("pmic_cal", android.content.Context.MODE_PRIVATE)
    fun get(ctx: android.content.Context, chip: PmicChip, reg: String): Pair<Double, Double>? {
        prefs(ctx).getString("${chip.id}_$reg", null)?.split(";")?.let { p ->
            if (p.size == 2) return p[0].toDouble() to p[1].toDouble()
        }
        factory(ctx, chip, reg)?.let { return it }
        // Valor de fábrica: familia SW (VGL en offset 4, 64 niveles): 0x3F = -8.1 V, 0x28 = -5.8 V
        if (reg == "VGL" && chip.trigger == com.electrokichel.app.core.data.PmicTrigger.SW_DIRECT_0x80 && chip.vglOff == 4) return -1.8 to -0.1
        return null
    }
    private var table: org.json.JSONArray? = null
    /** Calibración de fábrica desde assets/pmic_calibracion.json (por id, o por dirección I2C + registro). */
    private fun factory(ctx: android.content.Context, chip: PmicChip, reg: String): Pair<Double, Double>? {
        val t = table ?: runCatching { org.json.JSONArray(ctx.assets.open("pmic_calibracion.json").bufferedReader().use { it.readText() }) }
            .getOrNull()?.also { table = it } ?: return null
        val off = when (reg) { "VGH" -> chip.vghOff; "VGL" -> chip.vglOff; "VGL2" -> chip.vgl2Off; "VCOM" -> chip.vcomOff; else -> -1 }
        for (i in 0 until t.length()) {
            val e = t.getJSONObject(i)
            val r = e.getJSONObject("regs").optJSONObject(reg) ?: continue
            val byId = e.getString("id").equals(chip.id, true)
            val byAddr = e.getInt("i2c") == chip.i2cAddr && r.getInt("addr") == off
            if (byId || byAddr) return r.getDouble("base") to r.getDouble("paso")
        }
        return null
    }
    fun set(ctx: android.content.Context, chip: PmicChip, reg: String, base: Double, step: Double) {
        prefs(ctx).edit().putString("${chip.id}_$reg", "$base;$step").apply()
    }
    fun volts(ctx: android.content.Context, chip: PmicChip, reg: String, code: Int): Double? =
        get(ctx, chip, reg)?.let { it.first + it.second * code }
}

/** I2C Samsung en vivo (dirección 0x20). */
@androidx.compose.runtime.Composable
fun SamsungLiveScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    val log = rememberLog(); val scope = rememberCoroutineScope()
    val sl = Repo.samsungLive
    var vghIdx by remember { mutableStateOf((sl.vghTable.size / 2)) }
    var vglIdx by remember { mutableStateOf((sl.vglTable.size / 2)) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }
    ScreenScaffold("I2C Samsung (en vivo)", onBack) {
        InfoCard("PMIC Samsung en la dirección 0x20. Registro 0x0A = VGH, 0x0B = VGL. Ajusta el voltaje y escribe en vivo.")
        Section("Conexión") {
            EkButton(if (connected) "CONECTADO" else "CONECTAR CH341", if (connected) EkGreen else EkBlue) {
                scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ conectado") }.onFailure { log.add("❌ ${it.message}") } }
            }
        }
        Section("Ajuste") {
            Text("VGH: %.1f V".format(sl.vghBase + vghIdx * sl.step), color = EkText)
            Slider(vghIdx.toFloat(), { vghIdx = it.toInt() }, valueRange = 0f..(sl.vghTable.size-1).toFloat())
            Text("VGL: %.1f V".format(sl.vglBase - vglIdx * sl.step), color = EkText)
            Slider(vglIdx.toFloat(), { vglIdx = it.toInt() }, valueRange = 0f..(sl.vglTable.size-1).toFloat())
            EkButton("⚡ ESCRIBIR", EkOrange, enabled = connected, modifier = Modifier.fillMaxWidth()) {
                scope.launch { runCatching { io { UsbHub.withCh341 { ch ->
                    ch.i2cWriteReg(sl.addr, sl.regVgh, sl.vghTable[vghIdx]); ch.i2cWriteReg(sl.addr, sl.regVgl, sl.vglTable[vglIdx]) } } }
                    .onSuccess { log.add("✅ VGH=0x%02X VGL=0x%02X".format(sl.vghTable[vghIdx], sl.vglTable[vglIdx])) }.onFailure { log.add("❌ ${it.message}") } }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(140.dp)) }
    }
}

/** Programador SPI / I2C (EEPROM 24Cxx, Flash 25xx). */
@androidx.compose.runtime.Composable
fun EepromScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var i2cMode by remember { mutableStateOf(true) }
    var preset by remember { mutableStateOf(Presets.i2c.first()) }
    var data by remember { mutableStateOf<ByteArray?>(null) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }
    val open = rememberFileReader { n, b -> data = b; log.add("Cargado $n (${b.size})") }
    ScreenScaffold("Programador SPI / I2C", onBack) {
        InfoCard("Lee, respalda y graba EEPROM 24Cxx (I2C) o Flash 25xx (SPI) con el CH341. Elige el modo y el chip.")
        Section("Modo") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("I2C", color = EkDim); androidx.compose.material3.Switch(i2cMode, { i2cMode = !it }); Text("SPI", color = EkDim) }
            if (i2cMode) Dropdown("Chip 24Cxx", Presets.i2c, preset, { it.name }) { preset = it }
            Spacer(Modifier.height(6.dp))
            EkButton(if (connected) "CONECTADO" else "CONECTAR CH341", if (connected) EkGreen else EkBlue) {
                scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ conectado") }.onFailure { log.add("❌ ${it.message}") } }
            }
        }
        Section("Operaciones") {
            FlowButtons(
                "📥 LEER" to { scope.launch { runCatching { io { UsbHub.withCh341 { ch ->
                    if (i2cMode) ch.i2cRead(preset.defaultAddr, 0, preset.sizeBytes, preset.addrBytes)
                    else { val s = ch.detectCapacityBytes() ?: error("Sin JEDEC válido"); ch.readBytes(0, s) } } } }
                    .onSuccess { data = it; log.add("Leído ${it.size} bytes") }.onFailure { log.add("❌ ${it.message}") } } },
                "💾 GUARDAR RESPALDO" to { scope.launch { data?.let { val s = io { saveToDownloads(ctx, "backup.bin", it) }; log.add(if (s!=null) "✅ $s" else "❌") } } },
                "📂 CARGAR ARCHIVO" to { open() },
                "📤 GRABAR" to { scope.launch { data?.let { d -> runCatching { io { UsbHub.withCh341 { ch ->
                    if (i2cMode) ch.i2cWrite(preset.defaultAddr, 0, d, preset.addrBytes, preset.pageSize, 5)
                    else { var i = 0L; while (i < d.size) { ch.writeSector(i, d.copyOfRange(i.toInt(), minOf(i.toInt()+4096, d.size)).copyOf(4096)); i += 4096 } } } } }
                    .onSuccess { log.add("✅ Grabado y verificado") }.onFailure { log.add("❌ ${it.message}") } } } },
            )
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

/** Clínica HDMI (EDID). */
@androidx.compose.runtime.Composable
fun HdmiScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var edid by remember { mutableStateOf<ByteArray?>(null) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }
    ScreenScaffold("Clínica HDMI (EDID)", onBack) {
        InfoCard("Lee los 128 bytes del EDID (I2C 0x50), revisa cabecera y checksum, repara el checksum y graba de vuelta.")
        Section("Conexión") {
            EkButton(if (connected) "CONECTADO" else "VERIFICAR / CONECTAR", if (connected) EkGreen else EkBlue) {
                scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ CH341 enlazado") }.onFailure { log.add("❌ ${it.message}") } }
            }
        }
        Section("EDID") {
            FlowButtons(
                "LEER EDID" to { scope.launch { runCatching { io { UsbHub.withCh341 { ch -> ch.i2cRead(0x50, 0, 128, 1) } } }
                    .onSuccess { edid = it; log.add("EDID leído."); log.add(if (com.electrokichel.app.core.engine.Edid.headerOk(it)) "✅ Cabecera OK" else "❌ Cabecera inválida"); log.add(if (com.electrokichel.app.core.engine.Edid.checksumOk(it)) "✅ Checksum OK" else "❌ Checksum inválido") }
                    .onFailure { log.add("❌ ${it.message}") } } },
                "REPARAR CHECKSUM" to { edid?.let { com.electrokichel.app.core.engine.Edid.fixChecksum(it); log.add("Checksum recalculado: 0x%02X".format(it[127].toInt() and 0xFF)) } },
                "GRABAR AL TV" to { scope.launch { edid?.let { e -> runCatching { io { UsbHub.withCh341 { ch -> ch.i2cWrite(0x50, 0, e, 1, 8, 5) } } }.onSuccess { log.add("✅ EDID grabado") }.onFailure { log.add("❌ ${it.message}") } } } },
                "GUARDAR .bin" to { scope.launch { edid?.let { val s = io { saveToDownloads(ctx, "edid.bin", it) }; log.add(if (s!=null) "✅ $s" else "❌") } } },
            )
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

/** Copia de Panel ID (Sony) — EEPROM 24Cxx original -> nuevo. */
@androidx.compose.runtime.Composable
fun PanelIdScreen(onBack: () -> Unit) {
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var preset by remember { mutableStateOf<EepromPreset>(Presets.i2c[2]) }
    var backup by remember { mutableStateOf<ByteArray?>(null) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }
    ScreenScaffold("Copia Panel ID (Sony)", onBack) {
        InfoCard("Copia la EEPROM de identificación (I2C 24Cxx) del panel ORIGINAL al panel de reemplazo. Paso 1: leer el original. Paso 2: grabar el nuevo.")
        Section("Conexión y chip") {
            EkButton(if (connected) "CONECTADO" else "CONECTAR CH341", if (connected) EkGreen else EkBlue) {
                scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ conectado") }.onFailure { log.add("❌ ${it.message}") } }
            }
            Spacer(Modifier.height(6.dp))
            Dropdown("Chip EEPROM", Presets.i2c, preset, { it.name }) { preset = it }
        }
        Section("Pasos") {
            EkButton("1 · LEER PANEL ORIGINAL", EkBlue, enabled = connected, modifier = Modifier.fillMaxWidth()) {
                scope.launch { runCatching { io { UsbHub.withCh341 { ch -> ch.i2cRead(preset.defaultAddr, 0, preset.sizeBytes, preset.addrBytes) } } }
                    .onSuccess { backup = it; log.add("✅ Original leído (${it.size} bytes)") }.onFailure { log.add("❌ ${it.message}") } }
            }
            Spacer(Modifier.height(6.dp))
            EkButton("2 · GRABAR EN PANEL NUEVO", EkOrange, enabled = connected && backup != null, modifier = Modifier.fillMaxWidth()) {
                scope.launch { val b = backup ?: return@launch; runCatching { io { UsbHub.withCh341 { ch -> ch.i2cWrite(preset.defaultAddr, 0, b, preset.addrBytes, preset.pageSize, 5) } } }
                    .onSuccess { log.add("✅ Panel nuevo grabado y verificado") }.onFailure { log.add("❌ ${it.message}") } }
            }
            if (backup == null) Text("⚠ Primero leé el panel ORIGINAL", color = EkOrange, fontSize = 12.sp)
        }
        Section("Consola") { Console(log.lines, Modifier.height(140.dp)) }
    }
}
