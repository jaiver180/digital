package com.electrokichel.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.ui.*

/** Ingreso a modo de servicio: 366 códigos por placa/modelo (solo consulta). */
@androidx.compose.runtime.Composable
fun ServiceCodesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    var q by remember { mutableStateOf("") }
    val filtered = remember(q) { if (q.isBlank()) Repo.serviceCodes else Repo.serviceCodes.filter { it.placaModelo.contains(q, true) || it.procedimiento.contains(q, true) } }
    ScreenScaffold("Ingreso Modo de Servicios", onBack) {
        InfoCard("Listado de referencia: la combinación de teclas o el código de menú que abre el modo de fábrica en distintos TV. Solo consulta.")
        OutlinedTextField(q, { q = it }, label = { Text("Buscar por placa/modelo…") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        LazyColumn(Modifier.fillMaxWidth().height(520.dp)) {
            items(filtered) { e ->
                Surface(color = EkCard, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(e.placaModelo, color = EkBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(e.procedimiento, color = EkText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Centro de datos PMIC: ISP y pinouts. */
@androidx.compose.runtime.Composable
fun PmicDbScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    var q by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    ScreenScaffold("Centro de Datos PMIC", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EkButton("ISP (55)", if (tab == 0) EkGreen else EkDim) { tab = 0 }
            EkButton("PINOUT (85)", if (tab == 1) EkGreen else EkDim) { tab = 1 }
        }
        OutlinedTextField(q, { q = it }, label = { Text("Buscar…") }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        if (tab == 0) {
            val f = remember(q) { if (q.isBlank()) Repo.pmicIsp else Repo.pmicIsp.filter { it.pmic.contains(q, true) || it.fabricante.contains(q, true) } }
            LazyColumn(Modifier.height(520.dp)) {
                items(f) { e -> Surface(color = EkCard, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${e.pmic}  ·  ${e.fabricante}", color = EkBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("I2C ${e.i2cAddr}  ·  VGH ${e.regVgh}  ·  VGL ${e.regVgl}", color = EkText, fontSize = 12.sp)
                        Text("VGH ${e.rangoVgh}  ·  VGL ${e.rangoVgl}", color = EkDim, fontSize = 11.sp)
                    }
                } }
            }
        } else {
            val f = remember(q) { if (q.isBlank()) Repo.pmicPinouts else Repo.pmicPinouts.filter { it.referencia.contains(q, true) } }
            LazyColumn(Modifier.height(520.dp)) {
                items(f) { e -> Surface(color = EkCard, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(e.referencia, color = EkBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("SCL: ${e.scl}   ·   SDA: ${e.sda}", color = EkText, fontSize = 12.sp)
                    }
                } }
            }
        }
    }
}

/** Calculadora electrónica (resistencias, Ohm, serie/paralelo, SMD). */
@androidx.compose.runtime.Composable
fun CalcScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    ScreenScaffold("Calculadora Electrónica", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("OHM", "SERIE/PAR", "SMD").forEachIndexed { i, t -> EkButton(t, if (tab == i) EkGreen else EkDim) { tab = i } }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) { 0 -> OhmCalc(); 1 -> SeriesParallel(); 2 -> SmdCalc() }
    }
}

@androidx.compose.runtime.Composable
private fun OhmCalc() {
    var v by remember { mutableStateOf("") }; var i by remember { mutableStateOf("") }; var r by remember { mutableStateOf("") }
    Section("Ley de Ohm (deja un campo vacío)") {
        OutlinedTextField(v, { v = it }, label = { Text("Voltaje (V)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(i, { i = it }, label = { Text("Corriente (A)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(r, { r = it }, label = { Text("Resistencia (Ω)") }, modifier = Modifier.fillMaxWidth())
        EkButton("CALCULAR", EkBlue, Modifier.fillMaxWidth()) {
            val vv = v.toDoubleOrNull(); val ii = i.toDoubleOrNull(); val rr = r.toDoubleOrNull()
            when { vv == null && ii != null && rr != null -> v = (ii * rr).toString()
                ii == null && vv != null && rr != null && rr != 0.0 -> i = (vv / rr).toString()
                rr == null && vv != null && ii != null && ii != 0.0 -> r = (vv / ii).toString() }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SeriesParallel() {
    var vals by remember { mutableStateOf("100, 220, 330") }
    var res by remember { mutableStateOf("") }
    Section("Serie / Paralelo") {
        OutlinedTextField(vals, { vals = it }, label = { Text("Resistencias separadas por coma") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EkButton("SERIE", EkBlue) { val l = parse(vals); res = "Serie: %.2f Ω".format(l.sum()) }
            EkButton("PARALELO", EkBlue) { val l = parse(vals).filter { it != 0.0 }; res = if (l.isEmpty()) "—" else "Paralelo: %.2f Ω".format(1.0 / l.sumOf { 1.0 / it }) }
        }
        if (res.isNotEmpty()) Text(res, color = EkGreen)
    }
}
private fun parse(s: String) = s.split(",").mapNotNull { it.trim().toDoubleOrNull() }

@androidx.compose.runtime.Composable
private fun SmdCalc() {
    var code by remember { mutableStateOf("103") }; var res by remember { mutableStateOf("") }
    Section("Código SMD (3/4 dígitos o R)") {
        OutlinedTextField(code, { code = it }, label = { Text("Ej: 103, 4R7, 1002") }, modifier = Modifier.fillMaxWidth())
        EkButton("DESCIFRAR", EkBlue, Modifier.fillMaxWidth()) { res = decodeSmd(code.trim()) }
        if (res.isNotEmpty()) Text(res, color = EkGreen)
    }
}
private fun decodeSmd(c: String): String {
    if (c.contains('R', true)) { val v = c.uppercase().replace('R', '.').toDoubleOrNull(); return if (v != null) "%.2f Ω".format(v) else "inválido" }
    return when (c.length) {
        3 -> c.toIntOrNull()?.let { val d = c.substring(0,2).toInt(); val m = c[2].digitToInt(); fmt(d * Math.pow(10.0, m.toDouble())) } ?: "inválido"
        4 -> c.toIntOrNull()?.let { val d = c.substring(0,3).toInt(); val m = c[3].digitToInt(); fmt(d * Math.pow(10.0, m.toDouble())) } ?: "inválido"
        else -> "inválido"
    }
}
private fun fmt(ohm: Double) = when { ohm >= 1e6 -> "%.2f MΩ".format(ohm/1e6); ohm >= 1e3 -> "%.2f kΩ".format(ohm/1e3); else -> "%.0f Ω".format(ohm) }
