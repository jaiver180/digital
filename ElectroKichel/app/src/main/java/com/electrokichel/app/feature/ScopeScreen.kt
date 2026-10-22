package com.electrokichel.app.feature

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.electrokichel.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Osciloscopio por entrada de audio. */
@androidx.compose.runtime.Composable
fun ScopeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(FloatArray(0)) }
    var vpp by remember { mutableStateOf(0f) }
    var freq by remember { mutableStateOf(0f) }
    val perm = remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { perm.value = it }

    ScreenScaffold("Osciloscopio", onBack) {
        InfoCard("Convierte la entrada de micrófono en un osciloscopio sencillo (20 Hz–20 kHz). NUNCA conectes la señal directo: usa un divisor resistivo que baje el voltaje a unos pocos milivoltios.")
        Section("Captura") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EkButton(if (running) "DETENER" else "INICIAR", if (running) EkRed else EkGreen) {
                    if (!perm.value) { launcher.launch(Manifest.permission.RECORD_AUDIO); return@EkButton }
                    if (running) { running = false } else {
                        running = true
                        scope.launch {
                            val sr = 48000
                            val min = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                            if (min <= 0) { running = false; return@launch }
                            val rec = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
                            withContext(Dispatchers.IO) {
                                rec.startRecording()
                                val buf = ShortArray(512)
                                while (isActive && running) {
                                    val n = rec.read(buf, 0, buf.size)
                                    if (n > 0) {
                                        val norm = FloatArray(n) { buf[it] / 32768f }
                                        var mx = -1f; var mn = 1f; var cross = 0
                                        for (i in 0 until n) { if (norm[i] > mx) mx = norm[i]; if (norm[i] < mn) mn = norm[i]; if (i > 0 && norm[i-1] < 0 && norm[i] >= 0) cross++ }
                                        val dur = n.toFloat() / sr
                                        wave = norm; vpp = mx - mn; freq = if (dur > 0) (cross / dur) else 0f
                                    }
                                }
                                rec.stop(); rec.release()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Vpp: %.3f   ·   Frecuencia: %.0f Hz".format(vpp, freq), color = EkText)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                drawRect(Color(0xFF05070D))
                drawLine(EkBorder, Offset(0f, size.height/2), Offset(size.width, size.height/2), 1f)
                val w = wave
                if (w.size > 1) for (i in 1 until w.size) {
                    drawLine(EkGreen,
                        Offset((i-1) * size.width / w.size, size.height/2 - w[i-1] * size.height/2),
                        Offset(i * size.width / w.size, size.height/2 - w[i] * size.height/2), 1.5f)
                }
            }
        }
    }
}
