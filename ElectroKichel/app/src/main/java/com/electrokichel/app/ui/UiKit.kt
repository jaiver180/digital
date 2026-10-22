package com.electrokichel.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = EkCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, EkBorder), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), color = EkGreen, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun EkButton(text: String, color: Color = EkBlue, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF04101F), disabledContainerColor = EkBorder),
        shape = RoundedCornerShape(999.dp)
    ) { Text(text, fontWeight = FontWeight.Black, fontSize = 13.sp) }
}

@Composable
fun Console(lines: List<String>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) { scroll.animateScrollTo(scroll.maxValue) }
    Surface(color = Color(0xFF05070D), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, EkBorder), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp).verticalScroll(scroll)) {
            lines.forEach { Text(it, color = EkText, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            if (lines.isEmpty()) Text("—", color = EkDim, fontSize = 12.sp)
        }
    }
}

@Composable
fun <T> Dropdown(label: String, items: List<T>, selected: T?, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), color = EkDim, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Surface(color = EkCard2, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, EkBorder), modifier = Modifier.fillMaxWidth().clickable { open = true }) {
            Text(selected?.let(labelOf) ?: "— elegir —", color = EkText, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            items.forEach { it0 -> DropdownMenuItem(text = { Text(labelOf(it0)) }, onClick = { onSelect(it0); open = false }) }
        }
    }
}

@Composable
fun InfoCard(text: String) {
    Surface(color = EkCard2, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, EkBorder), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text, color = EkDim, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(EkBg)) {
        Row(Modifier.fillMaxWidth().background(EkCard).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Atrás", color = EkBlue, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
            Text(title, color = EkText, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) { content() }
    }
}
