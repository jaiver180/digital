package com.electrokichel.app.core.engine

data class EepromPreset(val name: String, val sizeBytes: Int, val addrBytes: Int, val pageSize: Int, val defaultAddr: Int = 0x50)
data class SpiPreset(val name: String, val sizeBytes: Int)

object Presets {
    val i2c = listOf(
        EepromPreset("24C01", 128, 1, 8), EepromPreset("24C02", 256, 1, 8),
        EepromPreset("24C04", 512, 1, 16), EepromPreset("24C08", 1024, 1, 16),
        EepromPreset("24C16", 2048, 1, 16), EepromPreset("24C32", 4096, 2, 32),
        EepromPreset("24C64", 8192, 2, 32), EepromPreset("24C128", 16384, 2, 64),
        EepromPreset("24C256", 32768, 2, 64), EepromPreset("24C512", 65536, 2, 128),
        EepromPreset("Genérico / manual", 256, 1, 8),
    )
    val spi = listOf(
        SpiPreset("25X40 (512KB)", 512 * 1024), SpiPreset("25X80 (1MB)", 1024 * 1024),
        SpiPreset("25X16 (2MB)", 2 * 1024 * 1024), SpiPreset("25X32 (4MB)", 4 * 1024 * 1024),
        SpiPreset("25X64 (8MB)", 8 * 1024 * 1024), SpiPreset("25X128 (16MB)", 16 * 1024 * 1024),
        SpiPreset("SPI / manual", 0),
    )
}
