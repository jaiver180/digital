package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.data.FlashModel
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.engine.SamsungEditor
import com.electrokichel.app.core.usb.UsbHub
import com.electrokichel.app.ui.*
import kotlinx.coroutines.launch

@androidx.compose.runtime.Composable
fun SamsungEditorScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Repo.ensure(ctx)
    val log = rememberLog()
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf<FlashModel?>(null) }
    var vghIdx by remember { mutableStateOf(0) }
    var vglIdx by remember { mutableStateOf(0) }
    var vghChk by remember { mutableStateOf<Int?>(null) }
    var vglChk by remember { mutableStateOf<Int?>(null) }
    var connected by remember { mutableStateOf(UsbHub.isCh341Open) }

    ScreenScaffold("Editor VGH/VGL Samsung", onBack) {
        InfoCard("Conecta el CH341A por OTG y la pinza SOP8 al chip SPI del panel. Elige el modelo, lee, ajusta VGH/VGL y graba solo esos bytes.")
        Section("Conexión") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (connected) "CONECTADO" else "CONECTAR CH341", if (connected) EkGreen else EkBlue) {
                    scope.launch { runCatching { io { UsbHub.openCh341() } }.onSuccess { connected = true; log.add("✅ CH341A conectado") }.onFailure { log.add("❌ ${it.message}") } }
                }
                EkButton("DESCONECTAR", EkDim) { UsbHub.closeCh341(); connected = false; log.add("Desconectado") }
            }
        }
        Section("Modelo") {
            Dropdown("Modelo de panel", Repo.samsungModels, model, { it.name }) { m -> model = m }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton("📡 LEER DEL CHIP", EkBlue, enabled = connected && model != null) {
                    val m = model ?: return@EkButton
                    scope.launch {
                        runCatching { io { UsbHub.withCh341 { ch -> SamsungEditor.readFromChip(ch, m) } } }
                            .onSuccess { r -> vghIdx = r.vghIdx; vglIdx = r.vglIdx; vghChk = r.vghChk; vglChk = r.vglChk
                                log.add("Leído: VGH=%.1fV (idx %d) · VGL=%.1fV (idx %d)".format(r.vghV, r.vghIdx, r.vglV, r.vglIdx)) }
                            .onFailure { log.add("❌ ${it.message}") }
                    }
                }
                EkButton("🔎 AUTODETECTAR", EkTeal, enabled = connected) {
                    scope.launch {
                        runCatching { io { UsbHub.withCh341 { ch -> SamsungEditor.detectModel(ch.readBytes(Repo.samsungModels.first().baseMain, 16)) } } }
                            .onSuccess { if (it != null) { model = it; log.add("Detectado: ${it.name}") } else log.add("❌ No se pudo autodetectar") }
                            .onFailure { log.add("❌ ${it.message}") }
                    }
                }
            }
        }
        model?.let { m ->
            val vt = SamsungEditor.vghTable(m); val lt = SamsungEditor.vglTable(m)
            Section("Ajuste de voltaje") {
                Text("VGH: %.1f V  (0x%02X)".format(vt[vghIdx.coerceIn(0, vt.size-1)].volts, vt[vghIdx.coerceIn(0, vt.size-1)].hex), color = EkText, fontSize = 15.sp)
                Slider(value = vghIdx.toFloat(), onValueChange = { vghIdx = it.toInt() }, valueRange = 0f..(vt.size - 1).toFloat())
                Text("VGL: %.1f V  (0x%02X)".format(lt[vglIdx.coerceIn(0, lt.size-1)].volts, lt[vglIdx.coerceIn(0, lt.size-1)].hex), color = EkText, fontSize = 15.sp)
                Slider(value = vglIdx.toFloat(), onValueChange = { vglIdx = it.toInt() }, valueRange = 0f..(lt.size - 1).toFloat())
                EkButton("⚡ GRABAR AL CHIP", EkOrange, enabled = connected, modifier = Modifier.fillMaxWidth()) {
                    scope.launch {
                        runCatching { io { UsbHub.withCh341 { ch -> SamsungEditor.writeToChip(ch, m, vghIdx, vglIdx, vghChk, vglChk) } } }
                            .onSuccess { log.add("✅ Grabado y verificado") }.onFailure { log.add("❌ ${it.message}") }
                    }
                }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(180.dp)) }
    }
}
