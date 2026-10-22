package com.electrokichel.app.core.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Acceso centralizado al USB: un solo dueño del CH341A para todos los módulos. */
object UsbHub {
    const val VID_WCH = 0x1A86
    val CH341_PIDS = setOf(0x5512, 0x5523, 0x7523)
    private const val ACTION_PERMISSION = "com.electrokichel.app.USB_PERMISSION"

    private lateinit var app: Context
    val lock = Mutex()
    private var ch341: Ch341? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    val manager: UsbManager get() = app.getSystemService(Context.USB_SERVICE) as UsbManager

    fun devices(): List<UsbDevice> = manager.deviceList.values.toList()

    fun findCh341(): UsbDevice? =
        devices().firstOrNull { it.vendorId == VID_WCH && it.productId in CH341_PIDS }

    fun describe(d: UsbDevice): String =
        "${d.productName ?: d.deviceName} (VID %04X PID %04X)".format(d.vendorId, d.productId)

    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (manager.hasPermission(device)) return true
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    if (intent.action != ACTION_PERMISSION) return
                    runCatching { app.unregisterReceiver(this) }
                    val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (cont.isActive) cont.resume(ok)
                }
            }
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED
            )
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                app, 0, Intent(ACTION_PERMISSION).setPackage(app.packageName), flags
            )
            manager.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
        }
    }

    /** Devuelve el CH341A abierto (lo abre si hace falta). Lanza excepción con mensaje claro si no se puede. */
    suspend fun openCh341(): Ch341 {
        ch341?.let { if (it.isOpen) return it }
        val dev = findCh341() ?: throw IllegalStateException(
            "No se encontró el CH341A. Conéctalo por OTG (VID 1A86, PID 5512/5523/7523)."
        )
        if (!requestPermission(dev)) throw IllegalStateException("Permiso USB denegado para el CH341A.")
        val c = Ch341.open(manager, dev)
            ?: throw IllegalStateException("No se pudo abrir el CH341A (endpoints bulk 0x02/0x82 no encontrados).")
        ch341 = c
        return c
    }

    fun closeCh341() {
        ch341?.close()
        ch341 = null
    }

    val isCh341Open: Boolean get() = ch341?.isOpen == true

    suspend fun <T> withCh341(block: suspend (Ch341) -> T): T = lock.withLock { block(openCh341()) }
}
