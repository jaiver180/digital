package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.electrokichel.app.core.adb.AdbConnection
import com.electrokichel.app.core.adb.AdbCrypto
import com.electrokichel.app.ui.*
import kotlinx.coroutines.launch

/** Control remoto por red (ADB WiFi). */
@androidx.compose.runtime.Composable
fun AdbScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var ip by remember { mutableStateOf("192.168.1.") }
    var portTxt by remember { mutableStateOf("5555") }
    var conn by remember { mutableStateOf<AdbConnection?>(null) }
    var free by remember { mutableStateOf("") }

    fun key(code: Int, name: String) {
        val c = conn ?: return
        scope.launch { runCatching { io { c.shell("input keyevent $code") } }.onSuccess { log.add("⌨ $name") }.onFailure { log.add("❌ ${it.message}") } }
    }

    ScreenScaffold("Control Remoto por Red", onBack) {
        InfoCard("Maneja un TV/caja Android por WiFi con ADB. Activa 'Depuración ADB por red' en el equipo. La primera vez acepta 'Permitir depuración USB' en la pantalla del TV.")
        Section("Conexión") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ip, { ip = it }, label = { Text("IP del TV") }, modifier = Modifier.weight(2f))
                OutlinedTextField(portTxt, { portTxt = it }, label = { Text("Puerto") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (conn != null) "CONECTADO" else "CONECTAR", if (conn != null) EkGreen else EkBlue) {
                    scope.launch {
                        runCatching { io { val c = AdbConnection(ip.trim(), portTxt.toIntOrNull() ?: 5555, AdbCrypto.load(ctx)); c.connect(); c } }
                            .onSuccess { conn = it; log.add("✅ Conectado (acepta el permiso en el TV si aparece)") }
                            .onFailure { log.add("❌ ${it.message}") }
                    }
                }
                EkButton("DESCONECTAR", EkDim) { conn?.close(); conn = null; log.add("Desconectado") }
            }
        }
        Section("Navegación") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f)); EkButton("▲", EkBlue, Modifier.weight(1f)) { key(19, "Arriba") }; Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Dpad3("◀" to 21, "OK" to 23, "▶" to 22) { c, n -> key(c, n) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f)); EkButton("▼", EkBlue, Modifier.weight(1f)) { key(20, "Abajo") }; Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            FlowKeyPairs(listOf("ATRÁS" to 4, "INICIO" to 3, "MENÚ" to 82, "POWER" to 26, "VOL+" to 24, "VOL-" to 25, "SILENCIO" to 164)) { c, n -> key(c, n) }
        }
        Section("Comando libre") {
            OutlinedTextField(free, { free = it }, label = { Text("shell …") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            EkButton("ENVIAR", EkBlue, enabled = conn != null) {
                val c = conn ?: return@EkButton
                scope.launch { runCatching { io { c.shell(free) } }.onSuccess { log.add("» $free"); log.add(it.trim()) }.onFailure { log.add("❌ ${it.message}") } }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

@androidx.compose.runtime.Composable
private fun Dpad3(l: Pair<String, Int>, c: Pair<String, Int>, r: Pair<String, Int>, onKey: (Int, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EkButton(l.first, EkBlue, Modifier.weight(1f)) { onKey(l.second, l.first) }
        EkButton(c.first, EkGreen, Modifier.weight(1f)) { onKey(c.second, c.first) }
        EkButton(r.first, EkBlue, Modifier.weight(1f)) { onKey(r.second, r.first) }
    }
}

@androidx.compose.runtime.Composable
private fun FlowKeyPairs(pairs: List<Pair<String, Int>>, onKey: (Int, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pairs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (n, c) -> EkButton(n, EkBlue, Modifier.weight(1f)) { onKey(c, n) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
