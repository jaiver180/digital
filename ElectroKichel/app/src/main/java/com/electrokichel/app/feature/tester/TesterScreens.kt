package com.electrokichel.app.feature.tester

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.ui.*
import kotlin.math.roundToInt

/** Patrones de prueba (índice y nombre). */
object Patterns {
    const val ROJO = 0; const val VERDE = 1; const val BLANCO = 2; const val NEGRO = 3
    const val SMPTE = 4; const val AZUL = 5; const val GRISES = 6; const val GRID = 7; const val LINES_H = 8; const val LINES_V = 9
    val ORDER = intArrayOf(4, 0, 1, 5, 2, 3, 6, 7, 8, 9)
    val NAME = mapOf(0 to "Canal Rojo 100%", 1 to "Canal Verde 100%", 2 to "Campo Blanco", 3 to "Campo Negro",
        4 to "Barras SMPTE", 5 to "Canal Azul 100%", 6 to "Rampa de Grises 16", 7 to "Cuadrícula", 8 to "Franjas Horizontales", 9 to "Franjas Verticales")
    private val SMPTE_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFF00FFFF.toInt(), 0xFF00FF00.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF000000.toInt())
    fun colors(p: Int) = SMPTE_COLORS
    fun next(p: Int, step: Int): Int { val pos = ORDER.indexOf(p).coerceAtLeast(0); val n = ORDER.size; return ORDER[((pos + step) % n + n) % n] }
}

@androidx.compose.runtime.Composable
fun PatternCanvas(pattern: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (pattern) {
            Patterns.ROJO -> drawRect(Color.Red); Patterns.VERDE -> drawRect(Color(0xFF00FF00))
            Patterns.BLANCO -> drawRect(Color.White); Patterns.NEGRO -> drawRect(Color.Black)
            Patterns.AZUL -> drawRect(Color(0xFF0000FF))
            Patterns.SMPTE -> { val c = Patterns.colors(pattern); val n = c.size; for (i in 0 until n) drawRect(Color(c[i]), topLeft = androidx.compose.ui.geometry.Offset((i * w / n), 0f), size = androidx.compose.ui.geometry.Size(w / n + 1, h)) }
            Patterns.GRISES -> { for (i in 0 until 16) { val v = i * 17; drawRect(Color(v, v, v), topLeft = androidx.compose.ui.geometry.Offset(i * w / 16, 0f), size = androidx.compose.ui.geometry.Size(w / 16 + 1, h)) } }
            Patterns.GRID -> { drawRect(Color.Black); val cols = 16; val rows = 10
                for (c in 0..cols) drawLine(Color.White, androidx.compose.ui.geometry.Offset(c * w / cols, 0f), androidx.compose.ui.geometry.Offset(c * w / cols, h), 1f)
                for (r in 0..rows) drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, r * h / rows), androidx.compose.ui.geometry.Offset(w, r * h / rows), 1f) }
            Patterns.LINES_H -> for (i in 0 until 20) drawRect(if (i % 2 == 0) Color.White else Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, i * h / 20), size = androidx.compose.ui.geometry.Size(w, h / 20 + 1))
            Patterns.LINES_V -> for (i in 0 until 30) drawRect(if (i % 2 == 0) Color.White else Color.Black, topLeft = androidx.compose.ui.geometry.Offset(i * w / 30, 0f), size = androidx.compose.ui.geometry.Size(w / 30 + 1, h))
        }
    }
}

@androidx.compose.runtime.Composable
fun TesterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(Patterns.SMPTE) }
    ScreenScaffold("Señales de Prueba", onBack) {
        InfoCard("Generador de patrones para diagnosticar paneles LCD/LED/OLED: color, luz de fondo, gamma, geometría y drivers. Lleva la señal al TV por USB-C→HDMI o duplicando la pantalla.")
        Section("Vista previa") {
            Surface(shape = RoundedCornerShape(8.dp)) {
                PatternCanvas(selected, Modifier.fillMaxWidth().height(180.dp)
                    .pointerInput(Unit) { detectTapGestures { selected = Patterns.next(selected, 1) } })
            }
            Spacer(Modifier.height(6.dp))
            Text(Patterns.NAME[selected] ?: "", color = EkText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            EkButton("▶ PANTALLA COMPLETA", EkPurple, Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(ctx, TesterFullscreenActivity::class.java).putExtra("p", selected))
            }
        }
        Section("Señales") {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Patterns.ORDER.toList()) { p ->
                    Surface(color = if (p == selected) EkCard2 else EkCard, shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (p == selected) EkOrange else EkBorder),
                        modifier = Modifier.fillMaxWidth().height(70.dp)) {
                        Column(Modifier.padding(10.dp).pointerInput(Unit) { detectTapGestures { selected = p } }) {
                            Text(Patterns.NAME[p] ?: "", color = EkText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Pantalla completa, horizontal: 1 toque = siguiente, toque largo = anterior, 2 toques = salir. */
class TesterFullscreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            var p by remember { mutableStateOf(intent.getIntExtra("p", Patterns.SMPTE)) }
            Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                detectTapGestures(
                    onTap = { p = Patterns.next(p, 1) },
                    onLongPress = { p = Patterns.next(p, -1) },
                    onDoubleTap = { finish() }
                )
            }) { PatternCanvas(p, Modifier.fillMaxSize()) }
        }
    }
}
