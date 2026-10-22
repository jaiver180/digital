package com.electrokichel.app.feature

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LogState {
    val lines = mutableStateListOf<String>()
    fun add(s: String) { lines.add(s) }
    fun clear() = lines.clear()
}

@Composable
fun rememberLog() = remember { LogState() }

/** Lee un archivo del selector (SAF). */
@Composable
fun rememberFileReader(onRead: (name: String, bytes: ByteArray) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val name = queryName(ctx, uri)
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            onRead(name, bytes)
        }.onFailure { Toast.makeText(ctx, "No se pudo abrir: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

fun queryName(ctx: Context, uri: Uri): String {
    var name = "archivo.bin"
    runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i)
        }
    }
    return name
}

/** Guarda bytes en Descargas. */
fun saveToDownloads(ctx: Context, name: String, bytes: ByteArray): String? = try {
    if (Build.VERSION.SDK_INT >= 29) {
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0); ctx.contentResolver.update(uri, cv, null, null)
        name
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeBytes(bytes); File(dir, name).absolutePath
    }
} catch (e: Exception) { null }

suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

fun ByteArray.hex(max: Int = size): String =
    (0 until minOf(max, size)).joinToString(" ") { "%02X".format(this[it].toInt() and 0xFF) }
