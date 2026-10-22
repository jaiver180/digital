package com.electrokichel.app.feature

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.electrokichel.app.core.data.IrBrand
import com.electrokichel.app.core.data.Repo
import com.electrokichel.app.core.ir.Ir
import com.electrokichel.app.ui.*

/** Controles de servicio por infrarrojo (todas las marcas de ir_codes.json). */
@androidx.compose.runtime.Composable
fun IrControlsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current; Repo.ensure(ctx)
    var brand by remember { mutableStateOf<IrBrand?>(null) }
    val mgr = remember { Ir.manager(ctx) }

    ScreenScaffold("Controles de servicio (IR)", onBack) {
        if (mgr == null) InfoCard("⚠️ Este teléfono no tiene emisor IR. Necesitas un dongle IR USB-C para que los botones funcionen.")
        else InfoCard("Elige la marca y pulsa los botones para enviar las señales de servicio por infrarrojo.")
        Section("Marca") {
            Dropdown("Control", Repo.irBrands.values.toList(), brand, { it.titulo }) { brand = it }
        }
        brand?.let { b ->
            Section("${b.titulo}  ·  ${b.frecuenciaHz} Hz  ·  ${b.botones.size} botones") {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(420.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(b.botones.keys.toList()) { key ->
                        EkButton(key, EkRed, Modifier.fillMaxWidth()) {
                            if (mgr == null) { Toast.makeText(ctx, "Sin emisor IR", Toast.LENGTH_SHORT).show(); return@EkButton }
                            runCatching { Ir.transmit(mgr, b.frecuenciaHz, b.botones.getValue(key)) }
                                .onFailure { Toast.makeText(ctx, "Error IR: ${it.message}", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }
    }
}
