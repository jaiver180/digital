package com.electrokichel.app.feature

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** Facturas / Órdenes de servicio: WebView local con puente AndroidBridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FacturasScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    AndroidView(factory = { c ->
        WebView(c).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(c, this), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (url.startsWith("file:///android_asset/")) return false
                    runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    return true
                }
            }
            loadUrl("file:///android_asset/facturas/index.html")
        }
    })
}

private class Bridge(val ctx: Context, val web: WebView) {
    private val prefs = ctx.getSharedPreferences("facturas_electrokichel", Context.MODE_PRIVATE)

    @JavascriptInterface fun getItem(key: String): String? = prefs.getString(key, null)
    @JavascriptInterface fun setItem(key: String, value: String): Boolean = prefs.edit().putString(key, value).commit()
    @JavascriptInterface fun deleteItem(key: String): Boolean = prefs.edit().remove(key).commit()

    @JavascriptInterface fun toast(msg: String) { web.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } }

    @JavascriptInterface fun openWhatsApp(phone: String, text: String) {
        web.post {
            runCatching {
                val clean = phone.filter { it.isDigit() }
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean?text=" + Uri.encode(text))))
            }.onFailure { Toast.makeText(ctx, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show() }
        }
    }

    @JavascriptInterface fun savePdfBase64(base64: String, fileName: String) {
        web.post {
            runCatching {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                    ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0); ctx.contentResolver.update(uri, cv, null, null)
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs(); File(dir, fileName).writeBytes(bytes)
                }
                Toast.makeText(ctx, "PDF guardado en Descargas: $fileName", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(ctx, "Error guardando el PDF: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    @JavascriptInterface fun printPage() {
        web.post {
            runCatching {
                val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = web.createPrintDocumentAdapter("ElectroKichel - Factura")
                pm.print("ElectroKichel - Factura", adapter, PrintAttributes.Builder().build())
            }.onFailure { Toast.makeText(ctx, "No se pudo imprimir: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
