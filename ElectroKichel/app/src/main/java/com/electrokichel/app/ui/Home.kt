package com.electrokichel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.R

data class Module(val route: String, val title: String, val sub: String, val icon: String, val color: Color)

val MODULES = listOf(
    Module("samsung", "EDITOR VGH/VGL SAMSUNG", "Lee y graba VGH/VGL por CH341A/SPI", "🖥️", EkBlue),
    Module("precision", "EDITOR PARCHE SAMSUNG (BIN)", "Corrige VGH/VGL en un .bin ya volcado", "🩹", EkBlue),
    Module("autoscan", "AUTOSCAN BIN SAMSUNG", "Escanea el .bin y dice el modelo exacto", "🔎", EkTeal),
    Module("precisionbin", "PRECISIÓN VGH/VGL EN BIN", "Búsqueda heurística para modelos desconocidos", "🎯", EkBrandBlue),
    Module("division", "REPARACIÓN IMAGEN DIVIDIDA", "Corrige la pantalla partida en dos (4K)", "🪟", EkPurple),
    Module("pmic", "PMIC LG / CHINOS (I2C)", "Ajusta VGH/VGL/VCOM de 114 PMIC", "🎛️", EkOrange),
    Module("samsunglive", "I2C SAMSUNG (EN VIVO)", "VGH/VGL por I2C dirección 0x20", "⚡", EkBlue),
    Module("eeprom", "PROGRAMADOR SPI / I2C", "Lee, respalda y graba 24Cxx / 25xx", "💾", EkGreen),
    Module("hex", "EDITOR HEXADECIMAL", "Edita cualquier .bin byte por byte", "🔢", EkGreen),
    Module("hdmi", "CLÍNICA HDMI (EDID)", "Lee, repara checksum y graba el EDID", "🔌", EkTeal),
    Module("paneld", "COPIA PANEL ID (SONY)", "Copia la EEPROM del panel al reemplazo", "🧬", EkTeal),
    Module("uart", "RECUPERACIÓN UART", "Consola y autorrecovery por serie/BT", "🔄", EkOrange),
    Module("tvbox", "RECOVERY TV BOX", "Comandos recovery para cajas Android", "📺", EkOrange),
    Module("ircontrols", "CONTROLES DE SERVICIO (IR)", "Samsung, LG y decenas de marcas", "📡", EkRed),
    Module("adb", "CONTROL REMOTO POR RED", "Maneja un TV Android por WiFi (ADB)", "📶", EkBlue),
    Module("tester", "SEÑALES DE PRUEBA (TV)", "Patrones para diagnosticar el panel", "🎨", EkPurple),
    Module("scope", "OSCILOSCOPIO", "Entrada de audio como osciloscopio", "📈", EkGreen),
    Module("bincmp", "COMPARADOR DE BIN", "Compara dos .bin byte por byte", "🗂️", EkDim),
    Module("service", "INGRESO MODO SERVICIO", "366 códigos por placa/modelo", "🛠️", EkDim),
    Module("pmicdb", "CENTRO DE DATOS PMIC", "55 PMIC ISP y 85 pinouts SDA/SCL", "🗄️", EkDim),
    Module("calc", "CALCULADORA ELECTRÓNICA", "Resistencias, SMD, Ohm, serie/paralelo", "🧮", EkTeal),
    Module("facturas", "FACTURAS / ÓRDENES", "Órdenes de servicio y factura PDF", "💵", EkGreen),
)

@Composable
fun Home(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(EkBg)) {
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(EkCard, EkCard2))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_electrokichel), null, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("ELECTROKICHEL", color = EkText, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp)
                Text("Soluciones en tecnología · Herramientas de servicio", color = EkDim, fontSize = 11.sp)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(MODULES) { m ->
                Surface(
                    color = EkCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, EkBorder),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).clickable { onOpen(m.route) }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Surface(color = m.color.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                            Text(m.icon, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(m.title, color = EkText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(m.sub, color = EkDim, fontSize = 10.5.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}
