package com.electrokichel.app.core.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import com.electrokichel.app.core.data.IrCode

object Ir {
    fun manager(ctx: Context): ConsumerIrManager? =
        (ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager)?.takeIf { it.hasIrEmitter() }

    /** Codificador NEC/Samsung32: hex de 32 bits -> patrón marca/espacio en microsegundos. */
    fun necPattern(hex: String, headerMark: Int = 9000, headerSpace: Int = 4500): IntArray {
        val code = hex.padStart(8, '0').toLong(16)
        val p = ArrayList<Int>(68)
        p.add(headerMark); p.add(headerSpace)
        for (i in 31 downTo 0) {
            p.add(560)
            p.add(if ((code shr i) and 1L == 1L) 1690 else 560)
        }
        p.add(560)
        return p.toIntArray()
    }

    fun patternOf(code: IrCode): IntArray = when (code) {
        is IrCode.Raw -> code.us
        is IrCode.Nec -> necPattern(code.hex, code.headerMark, code.headerMark / 2)
    }

    fun transmit(mgr: ConsumerIrManager, freq: Int, code: IrCode) {
        mgr.transmit(freq, patternOf(code))
    }
}
