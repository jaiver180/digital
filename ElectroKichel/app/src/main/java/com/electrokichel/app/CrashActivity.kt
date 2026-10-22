package com.electrokichel.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Muestra el error exacto cuando algo truena, en vez de cerrar la app en silencio. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "Sin detalle"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070B14"))
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "ElectroKichel · Error"
            setTextColor(Color.parseColor("#FF5C7A")); textSize = 18f; setPadding(0, 0, 0, 16)
        })
        root.addView(TextView(this).apply {
            text = "Copia este texto y envíalo para corregirlo:"
            setTextColor(Color.parseColor("#8896B3")); textSize = 13f; setPadding(0, 0, 0, 12)
        })
        val scroll = ScrollView(this)
        val tv = TextView(this).apply {
            text = trace; setTextColor(Color.parseColor("#E7ECF7")); textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true)
        }
        scroll.addView(tv)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Button(this).apply {
            text = "Copiar"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("error", trace))
                text = "Copiado ✓"
            }
        })
        row.addView(Button(this).apply { text = "Cerrar"; setOnClickListener { finishAffinity() } })
        root.addView(row)
        setContentView(root)
    }
}

object CrashCatcher {
    fun install(app: android.app.Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val trace = ex.stackTraceToString()
                runCatching { app.openFileOutput("last_crash.txt", Context.MODE_PRIVATE).use { it.write(trace.toByteArray()) } }
                val i = Intent(app, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("trace", trace)
                app.startActivity(i)
            } catch (_: Throwable) {}
            prev?.uncaughtException(thread, ex)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
