package com.electrokichel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.electrokichel.app.feature.*
import com.electrokichel.app.feature.tester.TesterScreen
import com.electrokichel.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElectroTheme {
                var splash by remember { mutableStateOf(true) }
                var route by remember { mutableStateOf<String?>(null) }
                when {
                    splash -> Splash { splash = false }
                    route == null -> Home { route = it }
                    else -> Feature(route!!) { route = null }
                }
            }
        }
    }
}

@Composable
private fun Feature(route: String, onBack: () -> Unit) {
    when (route) {
        "samsung" -> SamsungEditorScreen(onBack)
        "precision" -> PatchBinScreen(onBack)
        "autoscan" -> AutoScanScreen(onBack)
        "precisionbin" -> PrecisionScreen(onBack)
        "division" -> DivisionScreen(onBack)
        "pmic" -> PmicScreen(onBack)
        "samsunglive" -> SamsungLiveScreen(onBack)
        "eeprom" -> EepromScreen(onBack)
        "hex" -> HexEditorScreen(onBack)
        "hdmi" -> HdmiScreen(onBack)
        "paneld" -> PanelIdScreen(onBack)
        "uart" -> UartScreen(onBack, tvbox = false)
        "tvbox" -> UartScreen(onBack, tvbox = true)
        "ircontrols" -> IrControlsScreen(onBack)
        "adb" -> AdbScreen(onBack)
        "tester" -> TesterScreen(onBack)
        "scope" -> ScopeScreen(onBack)
        "bincmp" -> BinCompareScreen(onBack)
        "service" -> ServiceCodesScreen(onBack)
        "pmicdb" -> PmicDbScreen(onBack)
        "calc" -> CalcScreen(onBack)
        "facturas" -> FacturasScreen(onBack)
        else -> onBack()
    }
}
