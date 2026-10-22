package com.electrokichel.app.feature

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.usb.UsbHub
import com.electrokichel.app.ui.*
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TECH = linkedMapOf(
    "HISILICON" to listOf("rec", "recovery yes", "recovery"),
    "MSTAR" to listOf("reboot recovery", "cnstar", "recovery", "recovery_wipe_partition", "boot_fastboot", "reset"),
    "MEDIATEK" to listOf("reboot recovery", "eboot recovery", "boot recovery", "recovery", "boot_recovery", "reset", "b.reboot"),
    "REALTEK" to listOf("recovery", "go r", "reboot recovery", "reboot factory_reset", "boot_fastboot", "boot-fastboot", "boot_recovery", "boot-recovery", "cus recovery", "recover", "a recover", "reset"),
    "AMLOGIC" to listOf("run update", "reboot recovery", "reboot factory_reset", "recovery", "reset"),
    "SAMSUNG" to listOf("reboot recovery", "recovery", "boot recovery", "factory_reset", "erase_all", "download", "reset"),
    "PANASONIC" to listOf("securebootcmd"),
    "ROCKCHIP (TVBOX)" to listOf("boot_recovery", "reboot recovery", "run recoverykey", "run boot_recovery", "reset"),
    "ALLWINNER (TVBOX)" to listOf("boot-recovery", "reboot recovery", "run setargs_mmc_recovery", "run recovery", "reset"),
)
private val BAUDS = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
private val KEYS = listOf("ENTER" to 0x0D, "ESC" to 0x1B, "CTRL+C" to 0x03, "SPACE" to 0x20, "TAB" to 0x09, "SUPR" to 0x7F, "CTRL+Q" to 0x11, "CTRL+P" to 0x10)

@androidx.compose.runtime.Composable
fun UartScreen(onBack: () -> Unit, tvbox: Boolean) {
    val ctx = LocalContext.current
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var port by remember { mutableStateOf<UsbSerialPort?>(null) }
    var io by remember { mutableStateOf<SerialInputOutputManager?>(null) }
    var baud by remember { mutableStateOf(115200) }
    var tech by remember { mutableStateOf(TECH.keys.first()) }
    var autoRunning by remember { mutableStateOf(false) }

    fun write(bytes: ByteArray) { runCatching { port?.write(bytes, 1000) } }

    ScreenScaffold(if (tvbox) "Recovery TV Box" else "Recuperación UART", onBack) {
        InfoCard("Conecta un conversor USB-serie (CH340/CP2102/FTDI/PL2303): TX↔RX cruzados, GND común, sin VCC. Elige velocidad, conecta y enciende el equipo. Las teclas se repiten; el sistema automático manda la secuencia de la tecnología.")
        Section("Conexión") {
            Dropdown("Baudios", BAUDS, baud, { "$it" }) { baud = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (port != null) "CONECTADO" else "CONECTAR", if (port != null) EkGreen else EkBlue) {
                    scope.launch {
                        runCatching {
                            val mgr = UsbHub.manager
                            val driver = UsbSerialProber.getDefaultProber().findAllDrivers(mgr).firstOrNull() ?: error("No se encontró un conversor USB-serie")
                            if (!UsbHub.requestPermission(driver.device)) error("Permiso USB denegado")
                            val conn = mgr.openDevice(driver.device) ?: error("No se pudo abrir el puerto")
                            val p = driver.ports[0]; p.open(conn); p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                            val m = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
                                override fun onNewData(data: ByteArray) { val s = String(data); scope.launch { log.add(s.trimEnd('\n')) } }
                                override fun onRunError(e: Exception) { scope.launch { log.add("⚠ ${e.message}") } }
                            })
                            withContext(Dispatchers.IO) { m.start() }
                            port = p; io = m
                        }.onSuccess { log.add("✅ Conectado @ $baud baudios") }.onFailure { log.add("❌ ${it.message}") }
                    }
                }
                EkButton("DESCONECTAR", EkDim) { runCatching { io?.stop(); port?.close() }; port = null; io = null; autoRunning = false; log.add("Desconectado") }
            }
        }
        Section("Teclas de recovery") {
            FlowKeys(KEYS.map { it.first }) { label ->
                val code = KEYS.first { it.first == label }.second
                scope.launch { repeat(150) { write(byteArrayOf(code.toByte())); delay(100) } }
                log.add("⚡ $label x15s")
            }
        }
        Section("Comandos por tecnología") {
            Dropdown("Tecnología", TECH.keys.toList(), tech, { it }) { tech = it }
            Spacer(Modifier.height(6.dp))
            FlowKeys(TECH[tech]!!) { cmd -> write((cmd + "\r").toByteArray()); log.add("👉 $cmd") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (autoRunning) "DETENER" else "SISTEMA AUTOMÁTICO", if (autoRunning) EkRed else EkOrange, enabled = port != null) {
                    if (autoRunning) { autoRunning = false } else {
                        autoRunning = true
                        scope.launch {
                            val cmds = TECH[tech]!!
                            while (isActive && autoRunning) {
                                for (c in cmds) { if (!autoRunning) break; write((c + "\r").toByteArray()); log.add("🤖 $c"); delay(500) }
                                delay(500)
                            }
                        }
                    }
                }
                EkButton("LIMPIAR", EkDim) { log.clear() }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(220.dp)) }
    }
}

@androidx.compose.runtime.Composable
private fun FlowKeys(labels: List<String>, onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { l -> EkButton(l, EkBlue, Modifier.weight(1f)) { onKey(l) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
