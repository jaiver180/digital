package com.electrokichel.app.core.data

data class VgEntry(val volts: Double, val hex: Int)

enum class PmicTrigger { RT43_EEPROM, SW_DIRECT_0x80, UNLOCK0_THEN_0x80, UNLOCK1_THEN_0x80, NONE_CS603 }

data class PmicChip(
    val id: String, val nombre: String, val i2cAddr: Int, val blockSize: Int,
    val unlock: Int?, val delayMs: Long, val trigger: PmicTrigger,
    val vghOff: Int, val vglOff: Int, val vgl2Off: Int, val vcomOff: Int,
) { val hasVgl2 get() = vgl2Off >= 0; val hasVcom get() = vcomOff >= 0 }

data class FlashModel(
    val name: String, val baseMain: Long, val vghPos: Int, val vglPos: Int,
    val checksum: Boolean, val baseCheck: Long, val vghChk: Int, val vglChk: Int,
    val i2c: Boolean, val i2cAddr: Int,
    val customVgh: List<VgEntry>?, val customVgl: List<VgEntry>?,
)

data class AutoSignature(val modelName: String, val len: Int, val pattern: IntArray)

sealed class PatchData {
    data class Bytes(val v: IntArray) : PatchData()
    data class Text(val v: String) : PatchData()
    data class Byte(val v: Int) : PatchData()
}
data class PatchPart(val offset: Int, val data: PatchData)
data class DivisionProfile(
    val nombre: String, val size: Int, val pattern: IntArray?,
    val vghOffset: Int, val vghValue: Int, val startPos: Int?, val parches: List<PatchPart>?,
)

data class ServiceCode(val placaModelo: String, val procedimiento: String)
data class PmicIsp(val pmic: String, val fabricante: String, val modelo: String,
    val i2cAddr: String, val regVgh: String, val regVgl: String, val rangoVgh: String, val rangoVgl: String)
data class PmicPinout(val referencia: String, val scl: String, val sda: String)

sealed class IrCode {
    data class Nec(val hex: String, val headerMark: Int = 9000) : IrCode()
    data class Raw(val us: IntArray) : IrCode()
}
data class IrBrand(val titulo: String, val frecuenciaHz: Int, val botones: Map<String, IrCode>)

data class SamsungLive(
    val addr: Int, val regVgh: Int, val regVgl: Int,
    val vghBase: Float, val vglBase: Float, val step: Float,
    val vghTable: IntArray, val vglTable: IntArray,
)
