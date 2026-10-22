package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.engine.AutoScan
import com.electrokichel.app.ui.*

/** Autoscan BIN Samsung: identifica el modelo exacto a usar. No modifica el archivo. */
@androidx.compose.runtime.Composable
fun AutoScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scanner = remember { AutoScan(ctx) }
    val log = rememberLog()
    var result by remember { mutableStateOf<AutoScan.Result?>(null) }
    var bin by remember { mutableStateOf<ByteArray?>(null) }
    var name by remember { mutableStateOf("") }
    val open = rememberFileReader { n, b -> bin = b; name = n; result = scanner.scan(b, n)
        log.clear(); result!!.log.forEach { log.add(it) } }

    ScreenScaffold("Autoscan BIN Samsung", onBack) {
        InfoCard("Abre un .bin Samsung y te dice con qué MODELO modificar VGH/VGL. No cambia nada. Decide por: firma de un BIN guardado, nombre del archivo, serie o modelo escrito dentro del BIN.")
        Section("Archivo") {
            EkButton("📂 ABRIR BIN", EkBlue) { open() }
            if (name.isNotEmpty()) Text(name, color = EkDim, fontSize = 12.sp)
        }
        result?.let { r ->
            Section("Resultado") {
                val color = when (r.mode) { AutoScan.Mode.EXACTO -> EkGreen; AutoScan.Mode.ELEGIR -> EkOrange; AutoScan.Mode.NINGUNO -> EkRed }
                Text(when (r.mode) { AutoScan.Mode.EXACTO -> "✅ MODELO EXACTO"; AutoScan.Mode.ELEGIR -> "⚠ VARIOS CANDIDATOS"; AutoScan.Mode.NINGUNO -> "❌ NINGUNO" }, color = color, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(r.reason, color = EkText, fontSize = 12.sp)
                r.warning?.let { Spacer(Modifier.height(6.dp)); Text(it, color = EkOrange, fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                r.finalSet.forEachIndexed { i, c ->
                    Surface(color = EkCard2, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Grupo ${c.group.number} · 0x%08X".format(c.group.recipe.base), color = EkBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("VGH=0x%02X  VGL=0x%02X".format(c.vgh, c.vgl), color = EkText, fontSize = 12.sp)
                            Text(c.group.models.joinToString(", "), color = EkDim, fontSize = 11.sp)
                            if (bin != null) EkButton("💾 GUARDAR COMO REFERENCIA", EkTeal) {
                                val ok = scanner.saveReference(bin!!, c.group, c.group.models.first())
                                log.add(if (ok) "✅ Firma guardada" else "Esa firma ya estaba guardada")
                            }
                        }
                    }
                }
            }
        }
        Section("Detalle del escaneo") { Console(log.lines, Modifier.height(200.dp)) }
    }
}
