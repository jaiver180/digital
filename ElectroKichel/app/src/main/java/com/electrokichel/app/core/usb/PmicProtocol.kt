package com.electrokichel.app.core.usb

import com.electrokichel.app.core.data.PmicChip
import com.electrokichel.app.core.data.PmicTrigger

/** Protocolo I2C de los PMIC (lectura y grabación del bloque, con el trigger de cada familia). */
class PmicProtocol(private val ch: Ch341) {
    private val REG_CTRL = 0xFF

    fun readBlock(chip: PmicChip): ByteArray? {
        return try {
            if (chip.trigger == PmicTrigger.NONE_CS603) {
                ch.i2cReadReg(chip.i2cAddr, 0, chip.blockSize)
            } else {
                ch.i2cWriteReg(chip.i2cAddr, REG_CTRL, 0)
                Thread.sleep(chip.delayMs)
                ch.i2cReadReg(chip.i2cAddr, 0, chip.blockSize)
            }
        } catch (e: Exception) { null }
    }

    /** Graba el bloque completo y ejecuta el trigger (2 ciclos). */
    fun writeBlock(chip: PmicChip, block: ByteArray): Boolean {
        repeat(2) {
            if (!writeBlockOnce(chip, block)) return false
            Thread.sleep(20)
            runTrigger(chip)
            Thread.sleep(20)
        }
        return true
    }

    private fun writeBlockOnce(chip: PmicChip, block: ByteArray): Boolean {
        return try {
            if (chip.trigger == PmicTrigger.NONE_CS603) {
                // CS603: escribe el bloque en el registro 0 y en el 0x30
                ch.i2cWrite(chip.i2cAddr, 0, block, 1, block.size, 0); Thread.sleep(5)
                ch.i2cWrite(chip.i2cAddr, 0x30, block, 1, block.size, 0); Thread.sleep(5)
                return true
            }
            chip.unlock?.let { ch.i2cWriteReg(chip.i2cAddr, REG_CTRL, it) }
            Thread.sleep(chip.delayMs)
            ch.i2cWrite(chip.i2cAddr, 0, block, 1, block.size, 0)
            true
        } catch (e: Exception) { false }
    }

    private fun runTrigger(chip: PmicChip) {
        val a = chip.i2cAddr
        when (chip.trigger) {
            PmicTrigger.RT43_EEPROM -> {
                ch.i2cWriteReg(a, 0, 0x08); Thread.sleep(200)
                ch.i2cWriteReg(a, 0, 0x02); Thread.sleep(20)
            }
            PmicTrigger.SW_DIRECT_0x80 -> { ch.i2cWriteReg(a, REG_CTRL, 0x80); Thread.sleep(150) }
            PmicTrigger.UNLOCK0_THEN_0x80 -> {
                ch.i2cWriteReg(a, REG_CTRL, 0x00); Thread.sleep(5)
                ch.i2cWriteReg(a, REG_CTRL, 0x80); Thread.sleep(150)
            }
            PmicTrigger.UNLOCK1_THEN_0x80 -> {
                ch.i2cWriteReg(a, REG_CTRL, 0x01); Thread.sleep(5)
                ch.i2cWriteReg(a, REG_CTRL, 0x80); Thread.sleep(150)
            }
            PmicTrigger.NONE_CS603 -> { /* sin trigger */ }
        }
    }

    /** Escanea el bus I2C: direcciones que responden ACK. */
    fun scanBus(): List<Int> = (0x08..0x77).filter { ch.i2cProbe(it) == true }
}
