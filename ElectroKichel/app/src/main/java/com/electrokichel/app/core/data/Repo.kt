package com.electrokichel.app.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Carga los datos empaquetados en assets/ (una sola vez). */
object Repo {
    lateinit var pmicChips: List<PmicChip>; private set
    lateinit var samsungModels: List<FlashModel>; private set
    lateinit var samsungVgh: List<VgEntry>; private set
    lateinit var samsungVgl: List<VgEntry>; private set
    lateinit var signatures: List<AutoSignature>; private set
    lateinit var divisionProfiles: List<DivisionProfile>; private set
    lateinit var serviceCodes: List<ServiceCode>; private set
    lateinit var pmicIsp: List<PmicIsp>; private set
    lateinit var pmicPinouts: List<PmicPinout>; private set
    lateinit var irBrands: Map<String, IrBrand>; private set
    lateinit var samsungLive: SamsungLive; private set
    private var loaded = false

    fun ensure(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val a = ctx.applicationContext.assets
            fun read(name: String) = a.open(name).bufferedReader().use { it.readText() }

            // PMIC chips
            pmicChips = JSONArray(read("pmic_chips.json")).map { o ->
                PmicChip(
                    id = o.getString("id"), nombre = o.getString("nombre"),
                    i2cAddr = o.getInt("i2c_addr"), blockSize = o.getInt("tam_bloque"),
                    unlock = if (o.isNull("codigo_unlock")) null else o.getInt("codigo_unlock"),
                    delayMs = o.getLong("retardo_ms"),
                    trigger = PmicTrigger.valueOf(o.getString("trigger")),
                    vghOff = o.getInt("off_vgh"), vglOff = o.getInt("off_vgl"),
                    vgl2Off = o.getInt("off_vgl2"), vcomOff = o.getInt("off_vcom"),
                )
            }

            // Samsung T-CON models
            val sm = JSONObject(read("samsung_tcon_models.json"))
            samsungVgh = sm.getJSONArray("tabla_vgh_samsung").map { VgEntry(it.getDouble("voltios"), it.getInt("hex")) }
            samsungVgl = sm.getJSONArray("tabla_vgl_samsung").map { VgEntry(it.getDouble("voltios"), it.getInt("hex")) }
            samsungModels = sm.getJSONArray("modelos").map { o ->
                FlashModel(
                    name = o.getString("modelo"), baseMain = o.getLong("base_main"),
                    vghPos = o.getInt("vgh_pos"), vglPos = o.getInt("vgl_pos"),
                    checksum = o.getBoolean("checksum"),
                    baseCheck = o.optLong("base_check"), vghChk = o.optInt("vgh_chk_pos"), vglChk = o.optInt("vgl_chk_pos"),
                    i2c = o.optBoolean("i2c"), i2cAddr = o.optInt("i2c_addr", 0x50),
                    customVgh = o.optJSONArray("tabla_vgh")?.map { e -> VgEntry(e.getDouble("voltios"), e.getInt("hex")) },
                    customVgl = o.optJSONArray("tabla_vgl")?.map { e -> VgEntry(e.getDouble("voltios"), e.getInt("hex")) },
                )
            }
            signatures = sm.getJSONArray("firmas_autodeteccion").map { o ->
                AutoSignature(o.getString("modelo"), o.getInt("len"), o.getJSONArray("patron").let { p -> IntArray(p.length()) { p.getInt(it) } })
            }

            divisionProfiles = JSONArray(read("division_repair_profiles.json")).map { o ->
                DivisionProfile(
                    nombre = o.getString("nombre"), size = o.getInt("tamano_bytes"),
                    pattern = o.optJSONArray("patron")?.let { p -> IntArray(p.length()) { p.getInt(it) } },
                    vghOffset = o.getInt("vgh_offset"), vghValue = o.getInt("vgh_valor"),
                    startPos = if (o.has("pos_inicio")) o.getInt("pos_inicio") else null,
                    parches = o.optJSONArray("parches")?.map { pp ->
                        val datos = pp.get("datos")
                        PatchPart(pp.getInt("offset"), when (datos) {
                            is JSONArray -> PatchData.Bytes(IntArray(datos.length()) { datos.getInt(it) })
                            is Int -> PatchData.Byte(datos)
                            else -> PatchData.Text(datos.toString())
                        })
                    },
                )
            }

            serviceCodes = JSONArray(read("service_codes.json")).map { o ->
                ServiceCode(o.getString("placa_modelo"), o.getString("procedimiento"))
            }

            val pd = JSONObject(read("pmic_database.json"))
            pmicIsp = pd.getJSONArray("pmic_isp").map { o ->
                PmicIsp(o.getString("pmic"), o.getString("fabricante"), o.getString("modelo"),
                    o.getString("i2c_addr"), o.getString("reg_vgh"), o.getString("reg_vgl"),
                    o.getString("rango_vgh"), o.getString("rango_vgl"))
            }
            pmicPinouts = pd.getJSONArray("pinout_i2c").map { o ->
                PmicPinout(o.getString("referencia"), o.getString("scl"), o.getString("sda"))
            }

            // IR
            val ir = JSONObject(read("ir_codes.json"))
            val map = linkedMapOf<String, IrBrand>()
            ir.getJSONObject("marcas_servicio").let { ms ->
                ms.keys().forEach { k ->
                    val b = ms.getJSONObject(k)
                    val botones = linkedMapOf<String, IrCode>()
                    b.getJSONObject("botones").let { bs ->
                        bs.keys().forEach { bk ->
                            val c = bs.getJSONObject(bk)
                            botones[bk] = if (c.has("nec32")) IrCode.Nec(c.getString("nec32"))
                            else IrCode.Raw(c.getJSONArray("raw_us").let { r -> IntArray(r.length()) { r.getInt(it) } })
                        }
                    }
                    map["IR $k"] = IrBrand("$k (servicio)", b.getInt("frecuencia_hz"), botones)
                }
            }
            ir.getJSONObject("controles_dedicados").let { cd ->
                cd.keys().forEach { k ->
                    val b = cd.getJSONObject(k)
                    val botones = linkedMapOf<String, IrCode>()
                    b.getJSONObject("botones_raw_us").let { bs ->
                        bs.keys().forEach { bk ->
                            val r = bs.getJSONArray(bk); botones[bk] = IrCode.Raw(IntArray(r.length()) { r.getInt(it) })
                        }
                    }
                    map[k] = IrBrand(k, b.optInt("frecuencia_hz", 38000), botones)
                }
            }
            ir.optJSONObject("controles_servicio_hex")?.let { ch ->
                ch.keys().forEach { k ->
                    val b = ch.getJSONObject(k)
                    val freq = b.optInt("frecuencia_hz", 38000)
                    val botones = linkedMapOf<String, IrCode>()
                    b.getJSONObject("botones").let { bs ->
                        bs.keys().forEach { bk -> botones[bk] = IrCode.Nec(bs.getString(bk), if (k == "SamsungService") 4500 else 9000) }
                    }
                    map[k] = IrBrand(k, freq, botones)
                }
            }
            irBrands = map

            val sl = JSONObject(read("samsung_i2c_live.json"))
            samsungLive = SamsungLive(
                addr = sl.getInt("addr"), regVgh = sl.getInt("reg_vgh"), regVgl = sl.getInt("reg_vgl"),
                vghBase = sl.getDouble("vgh_base").toFloat(), vglBase = sl.getDouble("vgl_base").toFloat(),
                step = sl.getDouble("paso").toFloat(),
                vghTable = sl.getJSONArray("vgh_tabla").let { t -> IntArray(t.length()) { t.getInt(it) } },
                vglTable = sl.getJSONArray("vgl_tabla").let { t -> IntArray(t.length()) { t.getInt(it) } },
            )
            loaded = true
        }
    }
}

private inline fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }
