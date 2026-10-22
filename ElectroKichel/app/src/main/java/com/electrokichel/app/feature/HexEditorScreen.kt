package com.electrokichel.app.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.engine.Presets
import com.electrokichel.app.core.usb.UsbHub
import com.electrokichel.app.ui.*
import kotlinx.coroutines.launch

/** Editor hexadecimal completo: archivo o chip (SPI/I2C), edición byte a byte. */
@androidx.compose.runtime.Composable
fun HexEditorScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<ByteArray?>(null) }
    val changed = remember { mutableStateMapOf<Int, Boolean>() }
    var editIdx by remember { mutableStateOf(-1) }
    var editVal by remember { mutableStateOf("") }
    var i2cMode by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(Presets.i2c.first()) }
    val open = rememberFileReader { n, b -> data = b; changed.clear(); log.add("Cargado $n (${b.size} bytes)") }

    ScreenScaffold("Editor Hexadecimal", onBack) {
        InfoCard("Muestra cualquier .bin completo. Soporta SPI (25xx, tamaño por JEDEC) e I2C (24Cxx). Toca un byte para editarlo; los cambios quedan en rojo hasta guardar o grabar.")
        Section("Origen") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("I2C", color = EkDim); Switch(i2cMode, { i2cMode = it }); Text("SPI", color = EkDim)
            }
            if (i2cMode) Dropdown("Chip I2C", Presets.i2c, preset, { it.name }) { preset = it }
            Spacer(Modifier.height(8.dp))
            FlowButtons(
                "📂 ABRIR ARCHIVO" to { open() },
                "📥 LEER DEL CHIP" to {
                    scope.launch {
                        runCatching { io { UsbHub.withCh341 { ch ->
                            if (i2cMode) ch.i2cRead(preset.defaultAddr, 0, preset.sizeBytes, preset.addrBytes)
                            else { val size = ch.detectCapacityBytes() ?: 0; if (size == 0) error("No se detectó el tamaño (JEDEC)"); ch.readBytes(0, size) }
                        } } }.onSuccess { data = it; changed.clear(); log.add("Leído del chip (${it.size} bytes)") }.onFailure { log.add("❌ ${it.message}") }
                    }
                },
            )
        }
        data?.let { d ->
            Section("Acciones") {
                FlowButtons(
                    "💾 GUARDAR ARCHIVO" to { scope.launch { val s = io { saveToDownloads(ctx, "edit.bin", d) }; log.add(if (s!=null) "✅ Guardado: $s" else "❌ error") } },
                    "📤 GRABAR AL CHIP" to {
                        scope.launch {
                            runCatching { io { UsbHub.withCh341 { ch ->
                                if (i2cMode) ch.i2cWrite(preset.defaultAddr, 0, d, preset.addrBytes, preset.pageSize, 5)
                                else { var i = 0L; while (i < d.size) { ch.writeSector(i, d.copyOfRange(i.toInt(), minOf(i.toInt()+4096, d.size)).copyOf(4096)); i += 4096 } }
                            } } }.onSuccess { changed.clear(); log.add("✅ Grabado") }.onFailure { log.add("❌ ${it.message}") }
                        }
                    },
                )
            }
            Section("Contenido (${d.size} bytes)") {
                HexGrid(d, changed) { idx -> editIdx = idx; editVal = "%02X".format(d[idx].toInt() and 0xFF) }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(120.dp)) }
    }

    if (editIdx >= 0) AlertDialog(
        onDismissRequest = { editIdx = -1 },
        title = { Text("Byte 0x%06X".format(editIdx)) },
        text = { OutlinedTextField(editVal, { editVal = it.uppercase().take(2) }, label = { Text("Nuevo valor HEX (00-FF)") }) },
        confirmButton = { TextButton(onClick = {
            editVal.toIntOrNull(16)?.let { v -> data?.let { it[editIdx] = v.toByte(); changed[editIdx] = true } }; editIdx = -1
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { editIdx = -1 }) { Text("Cancelar") } }
    )
}

@androidx.compose.runtime.Composable
private fun HexGrid(d: ByteArray, changed: Map<Int, Boolean>, onEdit: (Int) -> Unit) {
    val rows = (d.size + 15) / 16
    LazyColumn(Modifier.height(400.dp)) {
        items(minOf(rows, 4096)) { r ->
            val base = r * 16
            Row {
                Text("%06X ".format(base), color = EkDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                for (c in 0 until 16) {
                    val i = base + c
                    if (i < d.size) Text(
                        "%02X ".format(d[i].toInt() and 0xFF),
                        color = if (changed[i] == true) EkRed else EkText,
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        modifier = Modifier.clickable { onEdit(i) }
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun FlowButtons(vararg pairs: Pair<String, () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (t, a) -> EkButton(t, EkBlue, Modifier.weight(1f)) { a() } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
