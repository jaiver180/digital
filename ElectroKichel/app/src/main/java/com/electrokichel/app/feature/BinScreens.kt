package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.data.FlashModel
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.engine.*
import com.electrokichel.app.ui.*
import kotlinx.coroutines.launch

/** Editor Parche Samsung: corrige VGH/VGL en un .bin ya volcado. */
@androidx.compose.runtime.Composable
fun PatchBinScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var bin by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var model by remember { mutableStateOf<FlashModel?>(null) }
    var vgh by remember { mutableStateOf(0) }; var vgl by remember { mutableStateOf(0) }
    val open = rememberFileReader { n, b -> bin = b; fileName = n; log.add("Cargado $n (${b.size} bytes)")
        SamsungEditor.detectModel(b.copyOfRange(0, 16))?.let { model = it; log.add("Autodetectado: ${it.name}") } }

    ScreenScaffold("Editor Parche Samsung (BIN)", onBack) {
        InfoCard("Carga el .bin ya leído, elige el modelo, ajusta VGH/VGL (voltaje real de la tabla del modelo), aplica el parche y guarda el archivo.")
        Section("Archivo") {
            EkButton("📂 CARGAR BINARIO", EkBlue) { open() }
            if (fileName.isNotEmpty()) Text(fileName, color = EkDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Dropdown("Modelo", Repo.samsungModels, model, { it.name }) { model = it }
        }
        val b = bin; val m = model
        if (b != null && m != null) {
            val vt = SamsungEditor.vghTable(m); val lt = SamsungEditor.vglTable(m)
            LaunchedEffect(m, b) {
                val gi = (m.baseMain + m.vghPos).toInt(); val li = (m.baseMain + m.vglPos).toInt()
                if (gi in b.indices && li in b.indices) {
                    vgh = SamsungEditor.indexOfHex(vt, b[gi].toInt() and 0xFF)
                    vgl = SamsungEditor.indexOfHex(lt, b[li].toInt() and 0xFF)
                    log.add("Valores en el BIN: VGH=%.1fV VGL=%.1fV".format(vt[vgh].volts, lt[vgl].volts))
                } else log.add("⚠ Las direcciones del modelo caen fuera del archivo (${b.size} bytes)")
            }
            Section("Ajuste") {
                Text("VGH: %.1f V (0x%02X)".format(vt[vgh.coerceIn(0,vt.size-1)].volts, vt[vgh.coerceIn(0,vt.size-1)].hex), color = EkText)
                Slider(vgh.toFloat(), { vgh = it.toInt() }, valueRange = 0f..(vt.size-1).toFloat())
                Text("VGL: %.1f V (0x%02X)".format(lt[vgl.coerceIn(0,lt.size-1)].volts, lt[vgl.coerceIn(0,lt.size-1)].hex), color = EkText)
                Slider(vgl.toFloat(), { vgl = it.toInt() }, valueRange = 0f..(lt.size-1).toFloat())
                EkButton("▶ APLICAR Y GUARDAR", EkGreen, Modifier.fillMaxWidth()) {
                    scope.launch {
                        val copy = b.copyOf()
                        val oM = (m.baseMain).toInt()
                        val oldVgh = copy[oM + m.vghPos].toInt() and 0xFF
                        val oldVgl = copy[oM + m.vglPos].toInt() and 0xFF
                        copy[oM + m.vghPos] = vt[vgh].hex.toByte(); copy[oM + m.vglPos] = lt[vgl].hex.toByte()
                        if (m.checksum) {
                            val oC = m.baseCheck.toInt()
                            copy[oC + m.vghChk] = (((copy[oC + m.vghChk].toInt() and 0xFF) - (vt[vgh].hex - oldVgh)) and 0xFF).toByte()
                            copy[oC + m.vglChk] = (((copy[oC + m.vglChk].toInt() and 0xFF) - (lt[vgl].hex - oldVgl)) and 0xFF).toByte()
                        }
                        val saved = io { saveToDownloads(ctx, "patch_${m.name}.bin", copy) }
                        log.add(if (saved != null) "✅ Guardado en Descargas: $saved" else "❌ No se pudo guardar")
                    }
                }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

/** Precisión VGH/VGL en archivos BIN (heurística). */
@androidx.compose.runtime.Composable
fun PrecisionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var bin by remember { mutableStateOf<ByteArray?>(null) }
    var tVgh by remember { mutableStateOf("25") }; var tVgl by remember { mutableStateOf("-8") }
    var results by remember { mutableStateOf<List<PrecisionSearch.Candidate>>(emptyList()) }
    val open = rememberFileReader { n, b -> bin = b; log.add("Cargado $n (${b.size} bytes)") }
    ScreenScaffold("Precisión VGH/VGL en BIN", onBack) {
        InfoCard("Analiza un .bin de un panel que NO está en la lista, buscando posiciones candidatas de VGH/VGL. Es una ayuda heurística: confírmalo con osciloscopio.")
        Section("Archivo y objetivo") {
            EkButton("📂 ABRIR BIN", EkBlue) { open() }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tVgh, { tVgh = it }, label = { Text("VGH objetivo") }, modifier = Modifier.weight(1f))
                OutlinedTextField(tVgl, { tVgl = it }, label = { Text("VGL objetivo") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton("🎯 BUSCAR", EkBrandBlue, enabled = bin != null) {
                    scope.launch { results = io { PrecisionSearch.search(bin!!, tVgh.toDoubleOrNull() ?: 25.0, tVgl.toDoubleOrNull() ?: -8.0, false) }; log.add("${results.size} candidatos") }
                }
                EkButton("FUERZA BRUTA (ADN)", EkDim, enabled = bin != null) {
                    scope.launch { results = io { PrecisionSearch.search(bin!!, 0.0, 0.0, true) }; log.add("🚀 Fuerza bruta: ${results.size}") }
                }
            }
        }
        if (results.isNotEmpty()) Section("Resultados (máx. 3)") {
            results.forEach { c -> Text("offset 0x%06X  ·  VGH %.1fV  ·  VGL %.1fV".format(c.offset, c.vghV, c.vglV), color = EkText, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
        Section("Consola") { Console(log.lines, Modifier.height(140.dp)) }
    }
}

/** Reparación de imagen dividida. */
@androidx.compose.runtime.Composable
fun DivisionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    val log = rememberLog(); val scope = rememberCoroutineScope()
    var bin by remember { mutableStateOf<ByteArray?>(null) }
    var match by remember { mutableStateOf<DivisionRepair.Match?>(null) }
    val open = rememberFileReader { n, b -> bin = b; match = DivisionRepair.find(b)
        log.add("Cargado $n (${b.size} bytes)"); log.add(match?.let { "Detectado: ${it.profile.nombre} @ 0x${it.position.toString(16)}" } ?: "❌ No coincide con ningún perfil") }
    ScreenScaffold("Reparación Imagen Dividida", onBack) {
        InfoCard("Corrige la 'pantalla partida en dos' de algunos paneles 4K usando 67 perfiles conocidos. Carga el volcado, detecta y aplica.")
        Section("Archivo") {
            EkButton("⬆ CARGAR BINARIO", EkBlue) { open() }
            match?.let { Text("Perfil: ${it.profile.nombre}", color = EkGreen, fontSize = 12.sp) }
            Spacer(Modifier.height(8.dp))
            EkButton("▶ APLICAR Y GUARDAR", EkPurple, enabled = bin != null && match != null, modifier = Modifier.fillMaxWidth()) {
                scope.launch {
                    val copy = bin!!.copyOf(); DivisionRepair.apply(copy, match!!)
                    val saved = io { saveToDownloads(ctx, "fix_${match!!.profile.nombre.replace(" ","_")}.bin", copy) }
                    log.add(if (saved != null) "✅ Guardado: $saved" else "❌ No se pudo guardar")
                }
            }
        }
        Section("Consola") { Console(log.lines, Modifier.height(160.dp)) }
    }
}

/** Comparador de dos BIN. */
@androidx.compose.runtime.Composable
fun BinCompareScreen(onBack: () -> Unit) {
    val log = rememberLog()
    var a by remember { mutableStateOf<ByteArray?>(null) }
    var b by remember { mutableStateOf<ByteArray?>(null) }
    var diffs by remember { mutableStateOf<List<BinCompare.Diff>>(emptyList()) }
    val openA = rememberFileReader { n, x -> a = x; log.add("A: $n (${x.size})") }
    val openB = rememberFileReader { n, x -> b = x; log.add("B: $n (${x.size})") }
    ScreenScaffold("Comparador de BIN", onBack) {
        InfoCard("Compara dos .bin byte por byte y muestra las posiciones que cambian (hasta 500).")
        Section("Archivos") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton("📂 ARCHIVO A", EkBlue) { openA() }
                EkButton("📂 ARCHIVO B", EkBlue) { openB() }
            }
            Spacer(Modifier.height(8.dp))
            EkButton("COMPARAR", EkGreen, enabled = a != null && b != null) {
                diffs = BinCompare.compare(a!!, b!!); log.add("${diffs.size} diferencia(s)")
            }
        }
        if (diffs.isNotEmpty()) Section("Diferencias") {
            SelectionContainer { androidx.compose.foundation.layout.Column {
                diffs.take(200).forEach { d -> Text("0x%06X: 0x%02X -> 0x%02X".format(d.offset, d.a, d.b), color = EkText, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            } }
        }
        Section("Consola") { Console(log.lines, Modifier.height(120.dp)) }
    }
}
