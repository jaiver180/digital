# Especificación técnica para recrear una app tipo "VGH/VGL Editor" (Android)

Documento obtenido del análisis del APK **VGH/VGL Editor — DOMOTRONICAS YONARHE v6.7** (`com.domotronicas.vgheditor`, versionCode 13).
Describe **qué hace cada función y cómo funciona por dentro** (protocolos USB/I2C/SPI/IR/ADB, tiempos y algoritmos), con los datos técnicos exportados a `data/`, para que puedas escribir **tu propia app** con las mismas capacidades.

> **Alcance y límites.** Esto es una especificación funcional, no una copia del código. Escribe tu implementación desde cero, con tu propio nombre, marca e íconos. Este documento **no** incluye:
> - la forma de saltar ni de generar la licencia de la app original;
> - las tablas de voltaje de los PMIC que el autor cifró a propósito (clase `DataShield`). En la sección 6.3 se explica cómo armar las tuyas a partir de los datasheets.

---

## Índice

1. [Ficha técnica del APK original](#1-ficha-técnica-del-apk-original)
2. [Arquitectura recomendada](#2-arquitectura-recomendada)
3. [Hardware que usa la app](#3-hardware-que-usa-la-app)
4. [Driver CH341A — modo SPI](#4-driver-ch341a--modo-spi)
5. [Driver CH341A — modo I2C](#5-driver-ch341a--modo-i2c)
6. [Módulos VGH/VGL](#6-módulos-vghvgl)
7. [Programador EEPROM 24Cxx / Flash SPI 25xx](#7-programador-eeprom-24cxx--flash-spi-25xx)
8. [Recuperación por UART y TV Box](#8-recuperación-por-uart-y-tv-box)
9. [eMMC ISP (USB Mass Storage)](#9-emmc-isp-usb-mass-storage)
10. [Clínica HDMI (EDID)](#10-clínica-hdmi-edid)
11. [Controles remotos infrarrojos](#11-controles-remotos-infrarrojos)
12. [Control remoto por red (ADB por WiFi)](#12-control-remoto-por-red-adb-por-wifi)
13. [Ingreso a modo de servicio (base de consulta)](#13-ingreso-a-modo-de-servicio-base-de-consulta)
14. [Centro de datos (eMMC, PMIC, pinouts)](#14-centro-de-datos-emmc-pmic-pinouts)
15. [Osciloscopio por entrada de audio](#15-osciloscopio-por-entrada-de-audio)
16. [Comparador de archivos BIN](#16-comparador-de-archivos-bin)
17. [Calculadora electrónica](#17-calculadora-electrónica)
18. [Asistente de IA](#18-asistente-de-ia)
19. [Puente Bluetooth SPP](#19-puente-bluetooth-spp)
20. [Licencia, idiomas y menú principal](#20-licencia-idiomas-y-menú-principal)
21. [Fallos del original que conviene no repetir](#21-fallos-del-original-que-conviene-no-repetir)
22. [Plan de implementación por fases](#22-plan-de-implementación-por-fases)
23. [Archivos de datos incluidos](#23-archivos-de-datos-incluidos)

---

## 1. Ficha técnica del APK original

| Dato | Valor |
|---|---|
| Paquete | `com.domotronicas.vgheditor` |
| Versión | 6.7 (versionCode 13) |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| Lenguaje | Kotlin (coroutines, StateFlow) |
| UI | Mezcla: **Jetpack Compose** (pantalla principal `RecoveryActivity`, comparador BIN) + **Views/ViewBinding** (el resto) |
| Compilación | **debug** (`android:debuggable="true"`): no la imites, publica una release firmada |
| Arranque | `SplashActivity` (LAUNCHER) → `RecoveryActivity` (menú Compose) |

**Librerías detectadas**
- `com.github.mik3y:usb-serial-for-android` (drivers CH34x, CP21xx, FTDI, PL2303, CDC-ACM) para UART
- `androidx.compose.*`, `androidx.appcompat`, `constraintlayout`, `viewbinding`, `recyclerview`, `viewpager2`
- `androidx.security:security-crypto` (Tink)
- `com.google.code.gson:gson`
- `kotlinx-coroutines-android`

**Permisos** (manifiesto completo en `data/AndroidManifest.decodificado.xml`)

| Permiso / feature | Para qué |
|---|---|
| `android.hardware.usb.host` (required) | CH341A, conversores USB-serie, dongle eMMC |
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (+ `BLUETOOTH`/`BLUETOOTH_ADMIN` hasta API 30) | Puente UART por Bluetooth SPP |
| `TRANSMIT_IR` + `android.hardware.consumerir` (opcional) | Controles IR |
| `RECORD_AUDIO` + micrófono (opcional) | Osciloscopio por audio |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | ADB por WiFi, Gemini, base de datos en GitHub |
| `WRITE_EXTERNAL_STORAGE` (hasta API 28) | Guardar .bin en Descargas en Android viejos |

`RecoveryActivity` tiene un `intent-filter` `USB_DEVICE_ATTACHED` con un `device_filter.xml`, así la app se abre sola al conectar el CH341A.

---

## 2. Arquitectura recomendada

```
app/
 ├─ core/usb/        Ch341Spi.kt, Ch341I2c.kt, UsbPermission.kt, ScsiBot.kt
 ├─ core/serial/     SerialLink.kt (USB-serie y Bluetooth SPP detrás de una sola interfaz)
 ├─ core/ir/         IrEncoder.kt (NEC/Samsung32), IrTransmitter.kt
 ├─ core/adb/        AdbConnection.kt, AdbCrypto.kt
 ├─ feature/vgh/     Editor Samsung (SPI), Precisión BIN, Pantalla dividida
 ├─ feature/pmic/    PMIC I2C (lectura/grabación de bloque, VGH/VGL/VCOM)
 ├─ feature/eeprom/  24Cxx y 25xx (leer, respaldar, restaurar)
 ├─ feature/uart/    Terminal, autorrecuperación, TV Box
 ├─ feature/emmc/    eMMC ISP
 ├─ feature/hdmi/    EDID
 ├─ feature/remote/  Controles IR, ADB WiFi
 ├─ feature/db/      Modos de servicio, PMIC, pinouts, galería eMMC
 ├─ feature/scope/   Osciloscopio
 ├─ feature/tools/   Comparador BIN, calculadora, IA
 └─ assets/data/     *.json de la carpeta data/ de este documento
```

Recomendaciones:
- **Una sola UI en Compose** (el original mezcla Compose y Views).
- **Un solo dueño del USB.** El original tiene `disconnectAllOtherDrivers()` porque SPI, I2C y PMIC compiten por el mismo CH341A. Usa un `UsbSessionManager` singleton con `Mutex`.
- **Toda la E/S USB en `Dispatchers.IO`**, con reintentos y tiempos de espera como se describe abajo.
- **Los datos van en JSON dentro de `assets/`**, no escritos en el código, para poder actualizarlos sin recompilar.

`build.gradle.kts` (mínimo):

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0") // repo jitpack
    implementation("com.google.code.gson:gson:2.11.0")
}
```

`res/xml/device_filter.xml`:

```xml
<resources>
    <usb-device vendor-id="6790" product-id="21778"/> <!-- 1A86:5512 CH341A modo I2C/SPI -->
    <usb-device vendor-id="6790" product-id="21795"/> <!-- 1A86:5523 -->
    <usb-device vendor-id="6790" product-id="29987"/> <!-- 1A86:7523 CH340 -->
</resources>
```

---

## 3. Hardware que usa la app

| Herramienta | Uso | Conexión |
|---|---|---|
| **CH341A** (VID `0x1A86`, PID `0x5512`/`0x5523`/`0x7523`) | SPI (flash del T-CON), I2C (PMIC, EEPROM, EDID) | OTG, pinza SOP8 |
| **Conversor USB-serie** (CH340, CP2102, FTDI, PL2303) | UART de placas de TV / TV Box | OTG; TX↔RX cruzados, GND común, **sin** VCC |
| **Módulo Bluetooth SPP** (HC-05/HC-06) | UART inalámbrico | UUID `00001101-0000-1000-8000-00805F9B34FB` |
| **Dongle eMMC ISP** (puente USB Mass Storage) | Leer/grabar eMMC | OTG; el dongle se presenta como disco USB (SCSI) |
| **Emisor IR** del teléfono, o dongle IR por USB-C | Controles remotos | `ConsumerIrManager` |
| **Entrada de audio** (micrófono/jack con divisor resistivo) | Osciloscopio | `AudioRecord` |

---

## 4. Driver CH341A — modo SPI

Implementación de referencia: clase `Ch341SpiDriver` del original. Todo va por **transferencias bulk**: OUT al endpoint `0x02` y IN desde el `0x82`. Cada paquete mide como máximo **32 bytes**.

### 4.1 Comandos del CH341

| Constante | Valor | Significado |
|---|---|---|
| `CMD_SPI_STREAM` | `0xA8` | Inicia una ráfaga SPI (le siguen hasta 31 bytes) |
| `CMD_I2C_STREAM` | `0xAA` | Ráfaga I2C |
| `CMD_UIO_STREAM` | `0xAB` | Control directo de pines (CS, dirección) |
| `UIO_STM_OUT` | `0x80` | OR con el valor de salida de los pines D0..D5 |
| `UIO_STM_DIR` | `0x40` | OR con la máscara de dirección |
| `UIO_STM_END` | `0x20` | Fin del stream UIO |
| `I2C_STM_SET` | `0x60` | OR con la velocidad (`1` = 100 kHz; en SPI fija el modo) |

### 4.2 Abrir el dispositivo

```
controlTransfer(0x40, 0xA1, 0, 0, null, 0, 100)   // init vendor
controlTransfer(0xC0, 0x5F, 0, 0, buf[8], 2, 1000) // leer versión/tamaño de paquete
bulkOut [0xAA, 0x61, 0x00]                         // I2C_STREAM, SET|1 (100 kHz), END
bulkOut [0xAB, 0xB7, 0x7F, 0x20]                   // UIO: OUT=0x37 (CS en alto), DIR=0x3F, END
```

Para cerrar: `bulkOut [0xAB, 0xB7, 0x40, 0x20]` (todos los pines como entrada), luego `releaseInterface` y `close`.

### 4.3 Chip Select

- **Bajar CS** (empieza la transacción): `[0xAB, 0xB7, 0xB7, 0xB6, 0x20]`, es decir OUT=0x37, OUT=0x37, OUT=0x36 (D0 en bajo), END.
- **Subir CS** (termina la transacción): `[0xAB, 0xB7, 0x20]`.

### 4.4 Transacción SPI full-duplex

El CH341 transmite **LSB primero**, así que cada byte se **invierte bit a bit** al enviar y al recibir:

```kotlin
fun swap(x: Int): Int {               // invierte el orden de los 8 bits
    var v = x and 0xFF
    v = ((v shr 1) and 0x55) or ((v shl 1) and 0xAA)
    v = ((v shr 2) and 0x33) or ((v shl 2) and 0xCC)
    return ((v shr 4) and 0x0F) or ((v shl 4) and 0xF0)
}

fun spiTransaction(write: ByteArray, readCount: Int): ByteArray {
    csLow()
    // Se envían write.size + readCount bytes en paquetes de [0xA8 + hasta 31 bytes].
    // Los bytes de lectura son 0xFF de relleno. Tras cada paquete se lee por bulk IN
    // exactamente la misma cantidad de bytes que se mandó (hasta 8 intentos).
    // Si llegan menos bytes de los esperados: subir CS y lanzar error.
    csHigh()
    // Los últimos readCount bytes recibidos, invertidos con swap(), son la respuesta.
}
```

### 4.5 Operaciones sobre flash NOR 25xx

| Operación | Comando | Notas |
|---|---|---|
| Release power-down | `0xAB` | Espera 1 ms antes de leer el JEDEC |
| JEDEC ID | `0x9F` → 3 bytes | fabricante, tipo, capacidad. Tamaño = `1 shl capacidad` si está entre 16 y 32. Fabricante `0x00`/`0xFF` = no hay chip |
| Leer estado | `0x05` → 1 byte | bit 0 = BUSY |
| Esperar listo | sondear `0x05` cada 1 ms, hasta 20 000 intentos | |
| Write Enable | `0x06` | antes de cada borrado o programación |
| Desproteger | `0x06`, luego `[0x01, 0x00]` (WRSR = 0) y esperar | quita los bits BP |
| Leer | `[0x03, A23..16, A15..8, A7..0]` + N | en bloques de **64 bytes**, 3 reintentos (25 ms, 50 ms) |
| Borrar sector 4 KB | `[0x20, addr24]` y esperar | |
| Programar página | `[0x02, addr24] + 256 bytes` y esperar | |

**Escritura segura de un sector** (`writeSector`), hasta 2 intentos:
1. desproteger → WREN → borrar 4 KB → esperar;
2. por cada página de 256 B: WREN → programar → esperar;
3. esperar 3 ms, releer los 4096 bytes y comparar;
4. si no coincide, reintentar. Si sigue fallando, mostrar el error con el primer offset distinto (las causas típicas son WP en alto o mal contacto).

---

## 5. Driver CH341A — modo I2C

Clase de referencia: `Ch341I2cDriver`. Abre la interfaz 0, busca los endpoints bulk IN y OUT, hace los mismos dos `controlTransfer` del modo SPI (si el `0x5F` devuelve un tamaño de paquete ≥ 32, lo usa) y fija la velocidad con `[0xAA, 0x60|speed, 0x00]` (`1` = 100 kHz, `2` = 400 kHz).

### 5.1 Sub-comandos del stream I2C (después de `0xAA`)

| Nombre | Valor |
|---|---|
| `STA` (start) | `0x74` |
| `STO` (stop) | `0x75` |
| `OUT` | `0x80 \| n` (envía los n bytes siguientes) |
| `IN` | `0xC0 \| n` (lee n bytes; `0xC0` solo = último byte con NACK) |
| `END` | `0x00` |

### 5.2 Lectura `readBytes(dev, reg, len, addrBytes)`

En bloques de hasta **27 bytes**. Con `addrBytes = 1`, un bloque nunca cruza un límite de 256 bytes, y los bits A8..A10 de la dirección de memoria se suman a la dirección del dispositivo (`dev | ((addr shr 8) and 7)`), como piden las 24C04/08/16.

```
Fase de dirección: [0xAA, 0x74, 0x80|(1+addrBytes), dev<<1, (addrH), addrL, 0x00]   // sin STOP: repeated start
Fase de lectura:   [0xAA, 0x74, 0x81, (dev<<1)|1, (0xC1 × (n-1)), 0xC0, 0x75, 0x00]
Luego bulk IN de n bytes (hasta 8 intentos).
```

### 5.3 Escritura `writeBytes(dev, reg, data, addrBytes, pageSize, pageDelayMs)`

Por cada página: `[0xAA, 0x74, 0x80|(1+addrBytes+n), dev<<1, addr…, datos…, 0x75, 0x00]`, y luego espera `pageDelayMs` (el ciclo de escritura de la EEPROM, típicamente 5 ms).

Helpers del original:
- `readRegisterVerified`: hasta 3 intentos, con 2 ms entre ellos.
- `writeRegisterRetrying`: hasta 3 intentos, y verifica releyendo.

---

## 6. Módulos VGH/VGL

### 6.1 Editor VGH/VGL de paneles Samsung (SPI)

Lee y modifica **dos bytes** (VGH y VGL), y opcionalmente sus checksums, dentro de la flash SPI del T-CON, **sin tocar el resto**.

**Modelo de datos** (`data/samsung_tcon_models.json`, 156 modelos):

```
FlashModel {
  modelo        // p. ej. "UN43TU7000F"
  base_main     // dirección absoluta del bloque de parámetros
  vgh_pos       // VGH está en base_main + vgh_pos
  vgl_pos       // VGL está en base_main + vgl_pos
  checksum      // si el bloque tiene bytes de verificación
  base_check, vgh_chk_pos, vgl_chk_pos
  tabla_vgh / tabla_vgl   // tablas propias (si no tiene, usa las globales)
  i2c / i2c_addr          // algunos modelos van por I2C (dirección 0x50) en vez de SPI
}
```

**Tablas globales voltaje↔byte**: `tabla_vgh_samsung` (114 valores) y `tabla_vgl_samsung` (79 valores) en el mismo JSON. Ejemplos: VGH 17.1 V = `0x54`; VGL −5.1 V = `0x18`.

**Autodetección** (`firmas_autodeteccion`, 32 firmas):
1. Leer los primeros 16 bytes del bloque.
2. Si son todos `0xFF` o todos `0x00`, no se puede detectar.
3. Si no, compararlos con el patrón de cada firma (sus primeros `len` bytes) y devolver el modelo correspondiente.

**Flujo de lectura**: conectar el CH341A, leer el JEDEC para conocer el tamaño, y leer el byte en `base_main + vgh_pos`, el de `+ vgl_pos` y, si hay checksum, los de `base_check + vgh_chk_pos` y `+ vgl_chk_pos`. El índice actual se obtiene buscando el byte leído en la tabla.

**Cambiar el valor** (algoritmo `setIndex` del original):

```kotlin
val delta = nuevoIdx - idxActual
nuevoHex = tabla[nuevoIdx].hex
if (modelo.checksum) chk = (chk - delta) and 0xFF   // compensa el checksum
```

> Esto supone que los valores hex de la tabla son consecutivos. Para hacerlo más robusto: `chk = (chk - (nuevoHex - hexActual)) and 0xFF`.

**Grabar**: leer el sector de 4 KB que contiene `base_main`, poner los bytes en `vgh_pos + (base_main & 0xFFF)` y `vgl_pos + (…)`, y los checksums en `(base_check & 0xFFF) + chk_pos` si caen en el mismo sector. Después `writeSector()` (borrado, programación y verificación) como en la sección 4.5.

### 6.2 Precisión VGH/VGL en archivos BIN (búsqueda heurística)

Sirve para .bin de modelos que no están en la base:
1. Recorrer el archivo en sectores de 4096 bytes.
2. En cada posición `p` donde el byte esté en la tabla VGH global, revisar `p+1 … p+16` (sin salir del sector) buscando un byte que esté en la tabla VGL.
3. Puntaje de cada par = `|V_vgh − objetivoVGH| + |V_vgl − objetivoVGL|`, con los objetivos que ingresa el usuario. En "fuerza bruta" el puntaje es 0.
4. Ordenar por puntaje y mostrar los **3 mejores** (offset, VGH, VGL).

### 6.3 PMIC por I2C (VGH, VGL, VGL2, VCOM)

Configuración por chip en `data/pmic_chips.json` (**111 chips**): dirección I2C, tamaño de bloque, código de desbloqueo, retardo, tipo de "trigger" y offsets de VGH, VGL, VGL2 y VCOM dentro del bloque.

Ejemplos:

| Chip | Addr | Bloque | Unlock | Trigger | VGH | VGL | VCOM |
|---|---|---|---|---|---|---|---|
| SW5084 / RT6919 / SM4053… | 0x20 | 13 | — | `SW_DIRECT_0x80` | 3 | 4 | — |
| TPS65177 / KD5567S / IML8973… | 0x20 | 13 | 1 | `UNLOCK1_THEN_0x80` | 8 | 11 | — |
| RT6943 / G2510 / P303-xx / SY7634… | 0x70 | 40 | 1 | `RT43_EEPROM` | 19 | 21 | — |
| CS603 | 0x36 | 42 | — | `NONE_CS603` | 13 | 12 | — |
| LP6281 / SM4186 | 0x20/0x21 | 9 | — | `SW_DIRECT_0x80` | 3 | 5 | 8 |

**Registro de control**: `0xFF`.

**Leer el bloque**:
1. CS603: leer `tam_bloque` bytes desde el registro 0 y listo.
2. Los demás: escribir `0x00` en el registro `0xFF`, esperar `retardo_ms` y leer `tam_bloque` bytes desde el registro 0.

**Grabar el bloque** (`writeBlock`, 2 ciclos con 20 ms entre ellos):
1. Si `codigo_unlock` no es null, escribir ese código en `0xFF`.
2. Esperar `retardo_ms`.
3. Escribir el bloque completo desde el registro 0.
4. Ejecutar el **trigger**, que pasa los valores a la EEPROM interna del PMIC:

| Trigger | Secuencia (tal como se ve en el código decompilado; verifícala con el datasheet) |
|---|---|
| `SW_DIRECT_0x80` | `0xFF ← 0x80`, esperar 150 ms |
| `UNLOCK0_THEN_0x80` | `0xFF ← 0x00`, 5 ms, `0xFF ← 0x80`, 150 ms |
| `UNLOCK1_THEN_0x80` | `0xFF ← 0x01`, 5 ms, `0xFF ← 0x80`, 150 ms |
| `RT43_EEPROM` | escribir `0x08`, esperar 200 ms, escribir `0x02`, esperar 20 ms (el decompilador muestra el registro 0x00) |
| `NONE_CS603` | sin trigger; además escribe el bloque en el registro `0x30`, con 5 ms entre escrituras |

**Voltajes.** Cada chip tiene una tabla "código ↔ voltaje" (familias SW, RT43, GEN25, NT50330_G2 y unas 60 familias `PE_*`). En el original esas tablas están **cifradas** (`DataShield`), así que no se incluyen aquí. Para tu app:
- Sácalas del **datasheet** de cada PMIC (sección "VGH/VGL Output Voltage Setting"). Casi siempre son lineales: `V = Vmin + código × paso`. En `data/pmic_database.json` están los rangos típicos (por ejemplo VGH 17.0–40.0 V y VGL −15.5 a −5.5 V para RT6937/P303-31).
- También puedes medir en un panel sano: leer el bloque, medir con el multímetro y anotar la pareja código→voltaje.
- Algunos chips tienen un índice base distinto de 0: SW50224_V2 usa VGH base 5 y VGL base 8; RT6950, SW50212 y SM4085 usan VGH base 144; LEMP20/21 y SW50222 usan VGL base 15. Además RT6950, SW50212 y SM4085 tienen **VGL2**.

### 6.4 Samsung por I2C "en vivo"

PMIC Samsung en la dirección `0x20`: registro `0x0A` = VGH, registro `0x0B` = VGL.
- VGH: `V = 17.0 + idx × 0.1`, hex en la tabla interna (empieza en 84, 84, 85, 85, 86…). Por defecto 25.0 V.
- VGL: `V = −5.5 − idx × 0.1`, hex en la tabla (empieza en 27, 27, 28, 28…). Por defecto −8.0 V.
- Al leer, buscar el índice cuyo hex esté más cerca del leído; si la diferencia es mayor que 15, el valor no es plausible.

### 6.5 Reparación de "pantalla partida" (DISP SEGM / JEIDA-VESA / MIRROR)

`data/division_repair_profiles.json`, con **67 perfiles**:

```
{ nombre, tamano_bytes, patron?, vgh_offset, vgh_valor, pos_inicio?, parches? }
```

Algoritmo:
1. Para cada perfil cuyo `tamano_bytes` sea igual al tamaño del volcado:
   - si tiene `pos_inicio` y no tiene `patron`, la posición encontrada es `pos_inicio`;
   - si tiene `patron`, buscarlo en el archivo; la posición es la primera coincidencia.
2. Aplicar:
   - si tiene `parches`, escribir cada `{offset, datos}`, donde `datos` puede ser una lista de bytes, un texto o un solo byte;
   - si no, escribir `vgh_valor` en `posición + vgh_offset`.
3. "Reparar en un clic": leer el chip completo, detectar, guardar una copia del original en Descargas, parchear, grabar y verificar releyendo.

---

## 7. Programador EEPROM 24Cxx / Flash SPI 25xx

**I2C** (`EepromChipPreset`: nombre, tamaño, bytes de dirección, página, dirección por defecto 0x50):

| Chip | Tamaño | Bytes de dirección | Página |
|---|---|---|---|
| 24C01 / 24C02 | 128 / 256 | 1 | 8 |
| 24C04 / 08 / 16 | 512 / 1K / 2K | 1 | 16 |
| 24C32 / 64 | 4K / 8K | 2 | 32 |
| 24C128 / 256 | 16K / 32K | 2 | 64 |
| 24C512 | 64K | 2 | 128 |
| Genérico | manual | manual | manual |

**SPI**: 25X40 (512 KB), 25X80 (1 MB), 25X16 (2 MB), 25X32 (4 MB), 25X64 (8 MB), 25X128 (16 MB), o genérico manual. El tamaño se autodetecta por JEDEC.

Funciones: detectar, leer y **respaldar** a archivo, **restaurar** desde archivo o URI (escritura más verificación), y log en consola.

---

## 8. Recuperación por UART y TV Box

### 8.1 Conexión

- **USB**: con `usb-serial-for-android`. El original agrega un `ProbeTable` propio para reconocer los CH340 de Aliexpress. Parámetros: `setParameters(baud, 8, 1, NONE)`.
- **Velocidades**: 9600, 19200, 38400, 57600, **115200** (la habitual), 230400, 460800, 921600.
- **Bluetooth SPP** (sección 19), como alternativa.
- Recepción con `SerialInputOutputManager`; el texto se agrega a la terminal con autoscroll.

### 8.2 Teclas de interrupción (frenar el arranque y entrar al bootloader)

| Tecla | Byte | Familias |
|---|---|---|
| ENTER | `0x0D` | MStar, Realtek, MediaTek, Amlogic, Sony, Novatek, Linux, Nugget, Panasonic |
| ESC | `0x1B` | Realtek, MediaTek, Amlogic, Sony, Linux |
| CTRL+C | `0x03` | MediaTek MT58xx, Sony |
| SPACE | `0x20` | Realtek, Amlogic |
| TAB | `0x09` | Realtek |
| SUPR (DEL) | `0x7F` | Realtek |
| CTRL+Q | `0x11` | Realtek, Linux |
| CTRL+P | `0x10` | Realtek, Linux |

Cada botón **repite su byte cada 100 ms durante 15 s** (`sendRepeatedCommand(byte, 15000, 100)`). Mientras tanto, el usuario enciende la placa.

### 8.3 Comandos de recuperación por familia

Cada comando se envía seguido de `\r`.

| Familia | Comandos |
|---|---|
| HISILICON | `rec`, `recovery yes`, `recovery` |
| MSTAR | `reboot recovery`, `cnstar`, `recovery`, `recovery_wipe_partition`, `boot_fastboot`, `reset` |
| MEDIATEK | `reboot recovery`, `eboot recovery`, `boot recovery`, `recovery`, `boot_recovery`, `reset`, `b.reboot` |
| REALTEK | `recovery`, `go r`, `reboot recovery`, `reboot factory_reset`, `boot_fastboot`, `boot-fastboot`, `boot_recovery`, `boot-recovery`, `cus recovery`, `recover`, `a recover`, `reset` |
| AMLOGIC | `run update`, `reboot recovery`, `reboot factory_reset`, `recovery`, `reset` |
| SAMSUNG | `reboot recovery`, `recovery`, `boot recovery`, `factory_reset`, `erase_all`, `download`, `reset` |
| PANASONIC | `securebootcmd` |
| ROCKCHIP (TV Box) | `boot_recovery`, `reboot recovery`, `run recoverykey`, `run boot_recovery`, `reset` |
| ALLWINNER (TV Box) | `boot-recovery`, `reboot recovery`, `run setargs_mmc_recovery`, `run recovery`, `reset` |

**"Sistema automático"** (pantallas UART y TV Box): envía los comandos de la familia uno tras otro, **cada 500 ms**, y al terminar la lista vuelve a empezar, hasta que se pulse DETENER.

### 8.4 Autorrecuperación inteligente (pantalla principal en Compose)

Presets (`TVChipsetPreset`: nombre, unidad de interrupción, repeticiones, comandos y teclas manuales):

| Preset | Unidad × repeticiones | Comandos |
|---|---|---|
| MStar | ENTER × 200 | REBOOT RECOVERY, CNSTAR, RECOVERY, RECOVERY_WIPE_PARTITION, BOOT_FASTBOOT, RESET |
| Realtek | ESC × 200 | RECOVERY GO R, REBOOT RECOVERY, REBOOT FACTORY_RESET, BOOT_FASTBOOT, BOOT-FASTBOOT, BOOT_RECOVERY, BOOT-RECOVERY, CUS BOOT, RECOVER, E RECOVER, RECOVERY, RESET |
| MediaTek DTV | ESC × 200 | REBOOT RECOVERY, EBOOT RECOVERY, BOOT RECOVERY, RECOVERY, BOOT_RECOVERY, RESET, B.REBOOT |
| MediaTek MT58xx | ENTER × 200 | igual que MediaTek DTV |
| Amlogic | ENTER × 200 | RUN UPDATE, REBOOT RECOVERY, REBOOT FACTORY_RESET, RECOVERY, RESET |
| Nugget / Sony / Sony MT58xx / Novatek / Linux | ENTER × 200 | RECOVERY, REBOOT RECOVERY, RESET (Nugget empieza por REBOOT RECOVERY) |
| Panasonic | ENTER × 200 | SECUREBOOTCMD |
| Samsung (Tizen) | `20089999\r` × 1 | RECOVERY, RESET |
| Hisilicon | `EXT\r\n` × 20 | REC, RECOVERY YES, RECOVERY |
| ECO | `RX/TX OK\r\n` × 24 | RECOVERY, RESET |
| Custom | configurable | configurable |
| Todas (diagnóstico) | ENTER × 200, en ciclo | combinación de todos |

Algoritmo:
1. **Paso 1 (interrupción)**: enviar la unidad N veces con **15 ms** entre envíos. Revisar las **últimas 10 líneas** del log (en minúsculas) buscando un prompt: `"> "`, `boot>`, `uart>`, `console`, `shell`, `mstar`, `realtek`, `amlogic`, `hisilicon`, `mtk`, `#`. Repetir hasta encontrarlo o hasta **60 s**, revisando cada 500 ms y mostrando la cuenta regresiva.
2. **Paso 2**: con prompt detectado, o pasados los 60 s de todas formas, enviar los comandos con **1000 ms** entre cada uno y **1500 ms** entre ciclos.
3. **Éxito**: el log contiene `rebooting`, `resetting`, `loading kernel` o `booting...`.
4. Si el dispositivo se desconecta, se aborta con error.

Para la terminal: modo HEX, contadores RX/TX, "mantener pantalla encendida", máximo de líneas configurable, y botón "Preguntar a la IA" (sección 18).

---

## 9. eMMC ISP (USB Mass Storage)

> El texto de ayuda del original dice "con el CH341A", pero el código **no usa el CH341A**: habla **USB Mass Storage Bulk-Only (BOT) con comandos SCSI** con un dongle ISP que se presenta como disco USB. Implementa esto último.

**Abrir**: buscar una interfaz con endpoints bulk IN y OUT y reclamarla. Opcionalmente fijar la velocidad del bus con el vendor request `controlTransfer(0x40, 0x04, code, 0, null, 0, 1000)`, donde code = 15 → 400 kHz, 8 → 1 MHz, 2 → 5 MHz.

**CBW (31 bytes, little endian)**:

```
dCBWSignature = 0x43425355 ("USBC")
dCBWTag       = 0x55AA55AA (fijo en el original)
dDataLength   = n
bmFlags       = 0x80 si es lectura, 0x00 si es escritura
bLUN          = 0
bCBLength     = largo del CDB
CBWCB         = CDB (6/10 bytes)
```

Luego, la fase de datos con un timeout de `n + 5000` ms. Después se lee el **CSW** (13 bytes); éxito si `byte[12] == 0`.

**Comandos SCSI**: `TEST UNIT READY (0x00)` (hasta 5 intentos, 200 ms entre ellos), `INQUIRY (0x12)`, `READ CAPACITY(10) (0x25)`, `READ(10) (0x28)`, `WRITE(10) (0x2A)`.

**Datos**: sectores de 512 B, bloques de **64 sectores** (32 KB) por comando.
- **Volcado SMART**: solo los primeros **2 097 152 sectores (1 GB)**, que es donde están el bootloader y las particiones de sistema.
- **Volcado FULL**: todo el chip.
- **Escritura**: desde un archivo (SAF `ACTION_OPEN_DOCUMENT`), mostrando el progreso.

**Tabla de fabricantes** (por MID del CID): 0x11 Toshiba/Kioxia, 0x13 Micron, 0x15 Samsung, 0x16 Renesas/Hynix, 0x1D AMD, 0x1F Kingston/Atmel, 0x2C Micron, 0x37 AppoTech/Kingmax, 0x44 SanDisk, 0x45 SanDisk, 0x70 Kingston, 0x90 SK Hynix, 0xFE Micron/Numonyx.

La pantalla tiene las pestañas LEER / ESCRIBIR / PINES DE CONEXIÓN / CMD (log).

---

## 10. Clínica HDMI (EDID)

- El EDID está en la EEPROM I2C **0x50** del puerto HDMI (pines DDC SDA/SCL). Bloque de **128 bytes**.
- **Leer**: `readBytes(0x50, 0, 128, addrBytes = 1)`.
- **Validar**: cabecera `00 FF FF FF FF FF FF 00`, y que la suma de los 128 bytes módulo 256 sea 0.
- **Reparar checksum**: `edid[127] = (256 − (Σ edid[0..126] mod 256)) mod 256`.
- **Grabar**: `writeBytes(0x50, 0, data, addrBytes = 1, pageSize = 8, delay = 5 ms)`, con confirmación del usuario antes.
- Botones: restaurar el original y guardar el .bin en Descargas.

---

## 11. Controles remotos infrarrojos

Todos los datos están en `data/ir_codes.json`.

### 11.1 Transmisión

`ConsumerIrManager.transmit(frecuencia, patrón)`, donde el patrón alterna **marca/espacio en microsegundos**. Antes de transmitir:
- comprobar `hasIrEmitter()`;
- si no hay emisor, mostrar el aviso "conecta un dongle IR USB-C";
- vibrar 35 ms al pulsar.

**Codificador NEC 32 bits** (lo usan los controles "SERVICIO" de 18 marcas, LG y Kalley):

```kotlin
fun necPattern(hex: String, headerMark: Int = 9000, headerSpace: Int = 4500): IntArray {
    val code = hex.padStart(8, '0').toLong(16)
    val p = mutableListOf(headerMark, headerSpace)
    for (i in 31 downTo 0) {
        p += 560
        p += if ((code shr i) and 1L == 1L) 1690 else 560
    }
    p += 560
    return p.toIntArray()
}
```

- **Samsung32**: igual, pero con cabecera **4500/4500**. La clase genérica `ServiceRemoteActivity` del original usa 4500/4500 para todas las marcas con hex. LG y Kalley usan 9000/4500.
- **Raw**: los controles dedicados (Sony, TCL RC902V "NIKAI", Hyundai, Sankey, Sharp, Finlandek, etc.) traen patrones crudos ya capturados (`botones_raw_us`) a 38 kHz. Algunos se envían en varias ráfagas con pausas (`sendIRMultiBurst`).

### 11.2 Contenido del JSON

- `marcas_servicio`: Acer, Hisense, HisenseV2, Hyundai, Kodak, LgV2, Lloyd, Nokia, OnePlus, Panasonic, Realme, Sansui, Sharp, SonyAts (40 kHz), Tcl, Thomson, Vcon (Videocon) y Xiaomi (raw). En total **567 botones**: `{nec32: "E01F08F7"}` o `{raw_us: [...]}`.
- `controles_dedicados`: 11 controles completos, **410 botones** en formato raw.
- `controles_servicio_hex`: SamsungService (28), LgService (61) y KalleyOlimpo RC-311S (42).

### 11.3 Secuencias de servicio Samsung

Botones del control de servicio Samsung, con la pausa entre teclas:

| Botón en la app | Secuencia | Pausa |
|---|---|---|
| Factory | `info` → `factory` | 300 ms |
| Service 1 | `info, menu, 3, 1, 4, exit` (sin pausa explícita) | 400 ms |
| Service 2 | `info, menu, 4, 1, 3, exit` (sin pausa explícita) | 400 ms |
| Hotel / otros | `mute, 1, 4, 7, mute` · `mute, 3, 6, 9, mute` · `mute, 2, 5, 8, exit` · `mute, 3, 6, 9, exit` · `mute, 4, 9, exit` | 200 ms |
| Hotel (raw) | patrones crudos `hotelPatterns` | 200 ms |

El control LG incluye `IN-START` e `IN-STOP` (modo de servicio LG), `P-CHECK`, `ADJUST`, `EYE`, `HDMI 1-4`, etc. (ver `LgService` en el JSON).

---

## 12. Control remoto por red (ADB por WiFi)

Es un cliente del **protocolo ADB** sobre TCP (puerto por defecto **5555**). El usuario activa "Depuración ADB por red" en el TV.

**Mensaje ADB**: cabecera de 24 bytes LE `[command, arg0, arg1, data_length, data_crc32 (suma de bytes), magic = command ^ 0xFFFFFFFF]` + payload.

| Comando | Valor (LE) |
|---|---|
| CNXN | `0x4E584E43` |
| AUTH | `0x48545541` |
| OPEN | `0x4E45504F` |
| OKAY | `0x59414B4F` |
| WRTE | `0x45545257` |
| CLSE | `0x45534C43` |

Secuencia:
1. `CNXN(version = 0x01000000, maxdata = 1 048 576, "host::features=cmd,shell_v2\0")`.
2. Si el TV responde `AUTH(TOKEN=1, token)`, firmar el token con la clave **RSA-2048** (PKCS#1 v1.5 sobre el token, como hace adb) y enviar `AUTH(SIGNATURE=2, firma)`.
3. Si responde otro `AUTH`, enviar `AUTH(RSAPUBLICKEY=3, pubkey_adb + " nombre\0")`. La clave pública va en el formato de adb: estructura `RSAPublicKey` de 64 palabras (n0inv, módulo y R² en little endian) codificada en Base64. El TV muestra "¿Permitir depuración?"; esperar hasta **20 s**.
4. Al recibir `CNXN`, la conexión está lista.
5. Para un comando de shell: `OPEN(localId, 0, "shell:<cmd>\0")` → leer `WRTE` (responder `OKAY` a cada uno) hasta `CLSE`.

Generar el par de claves una sola vez (`KeyPairGenerator RSA 2048`) y guardarlo (en `EncryptedSharedPreferences` o en el Keystore).

**Funciones de la pantalla**

| Botón | Comando |
|---|---|
| Flechas / OK | `input keyevent 19/20/21/22/23` |
| Power | `input keyevent 26` |
| Atrás / Inicio / Menú | `input keyevent 4 / 3 / 82` |
| Vol+ / Vol− / Silencio | `input keyevent 24 / 25 / 164` |
| Rotación | `settings put system accelerometer_rotation 1`, `settings put system user_rotation N` |
| Listar apps | `pm list packages` con `-s` (sistema), `-3` (usuario), `-e` (habilitadas), `-d` (deshabilitadas) |
| Habilitar / deshabilitar | `pm enable <pkg>` / `pm disable-user <pkg>` |
| Desinstalar (usuario 0) | `pm uninstall --user 0 <pkg>` |
| Comando libre | campo de texto → `shell:` |

---

## 13. Ingreso a modo de servicio (base de consulta)

`data/service_codes.json` tiene **366 entradas** `{placa_modelo, procedimiento}`, por ejemplo `"T.R85.031" → "Menú 1147"` o `"HYLED4324TM" → "MUTE + 182 + POWER o INFO + FACTORY"`.

La pantalla es solo un buscador por texto: filtra por coincidencia parcial sin distinguir mayúsculas y no se comunica con ningún equipo.

---

## 14. Centro de datos (eMMC, PMIC, pinouts)

- **Galería eMMC** (en línea): lista carpetas e imágenes de un repositorio público de GitHub con la API `GET https://api.github.com/repos/<owner>/<repo>/contents/<path>` (cabecera `Accept: application/vnd.github+json`) y descarga las imágenes de `raw.githubusercontent.com`. Guarda las imágenes en caché, permite ampliarlas y buscar. **Monta tu propio repositorio** con tus fotos de puntos ISP.
- **PMIC ISP** (`data/pmic_database.json → pmic_isp`, 55 chips): fabricante, dirección I2C, registro VGH, registro VGL y rangos de voltaje.
- **Conexiones PMIC** (`pinout_i2c`, 85 chips): pin SCL y pin SDA de cada referencia.

---

## 15. Osciloscopio por entrada de audio

- `AudioRecord(source = UNPROCESSED (9), con respaldo a MIC (1), 48000 Hz, CHANNEL_IN_MONO (16), PCM_16BIT (2))`.
- Normalizar cada muestra: `s / 32768f`.
- Calcular Vmax, Vmin y **Vpp**, multiplicados por `calibrationVoltsPerUnit`, que el usuario calibra con una tensión conocida.
- **Frecuencia** = (cruces por cero / 2) / duración del bloque.
- Dibujar la onda con 512 puntos en un `Canvas` de Compose.
- Para medir tensiones de la placa, hace falta un divisor resistivo o una punta atenuadora hacia la entrada de micrófono. Avisa al usuario: la entrada de audio solo admite unos cientos de mV.

---

## 16. Comparador de archivos BIN

- Cargar el archivo A y el B y compararlos byte a byte. Mostrar `0xOFFSET: 0xAA -> 0xBB`, hasta **500 diferencias**, más el total y los tamaños de cada archivo.
- Vista "BLOQUE COMPLETO A y B" en hex lado a lado (`FullBlockViewActivity`), resaltando lo que cambió.

---

## 17. Calculadora electrónica

Solo hace cálculos, no necesita hardware:
- **Colores**: código de colores de 4 y 5 bandas con tolerancia, y modo inductor (µH).
- **SMD**: códigos de 3 y 4 dígitos, notación con R (`4R7`) y EIA-96 si quieres agregarlo.
- **Serie/paralelo**: `Rs = ΣR`, `1/Rp = Σ(1/R)`.
- **Ohm**: despeja V, I o R; divisor de tensión `Vout = Vin · R2 / (R1 + R2)`.

---

## 18. Asistente de IA

- Gemini `generateContent` con la clave que pone el propio usuario (gratis en aistudio.google.com).
- Tipos de prompt: UART (diagnóstico del log), PMIC (¿VGH/VGL normales?), detección de EEPROM, DIV/MAP/MIRROR, copia de EEPROM de panel, y genérico. Todos piden respuestas cortas en español.
- Reintenta con espera si recibe HTTP 503, o 429 cuyo cuerpo diga "overloaded". Traduce los errores 400 y 403 a mensajes amigables.
- Modo "IA en tiempo real" en la terminal UART: envía las últimas líneas del log.

Mejor que el original:
- manda la clave en la cabecera `x-goog-api-key`, no en la URL;
- guárdala en `EncryptedSharedPreferences`;
- considera ofrecer también la API de Claude.

---

## 19. Puente Bluetooth SPP

- Mostrar los dispositivos emparejados (`bondedDevices`) para elegir.
- `createRfcommSocketToServiceRecord(UUID SPP)`. Si falla, usar el respaldo por reflexión `createRfcommSocket(1)`. Antes, `cancelDiscovery()`.
- Un hilo de lectura (daemon) entrega los bytes a `onDataReceived`; `onDisconnected` y `onError` avisan a la UI.
- En Android 12 o superior, pedir `BLUETOOTH_CONNECT` en tiempo de ejecución.

---

## 20. Licencia, idiomas y menú principal

**Licencia del original** (solo como referencia de diseño; crea tu propio esquema y tus propias claves):
- Huella del equipo = SHA-256(`ANDROID_ID|MANUFACTURER|MODEL|BOARD`), primeros 24 caracteres hex.
- Código = `huella.firmaBase64`, firmado con **ECDSA P-256 / SHA256withECDSA**. La app solo trae la clave pública.
- Para pedir la licencia abre WhatsApp con la huella. El código se guarda en SharedPreferences y se valida en cada arranque.
- Para tu app: genera **tu propio** par de claves EC y firma las huellas desde tu servidor o tu PC. Si la vendes por Google Play, considera Play Billing en vez de este esquema.

**Idiomas**: español (por defecto), inglés y portugués, con una clase `S.t("clave")` y `LocaleManager`. En Android usa `strings.xml` con `values-en` y `values-pt`.

**Menú principal (tarjetas)**:
- Editor VGH/VGL
- PMIC
- EEPROM/SPI
- Recuperación UART
- Recovery TV Box
- Firmware eMMC ISP
- Conexiones PMIC
- Osciloscopio
- Parche Samsung
- Multimarcas, con: control de servicio Samsung, LG, Kalley/Olimpo RC-311S, TCL RC902V, Hyundai/Challenger AD-1603, Kalley Smart TV, Kalley Android TV, Finlandek, Kalley/Hisense, Sankey RD1545, Hyundai Android TV, Sony, Sharp, Hisense servicio, y los 18 controles "IR (SERVICIO)"
- Pantallas aparte: Centro de datos, Calculadora, Clínica HDMI, Comparador BIN, Control remoto por red, Pantalla dividida, Modos de servicio, Precisión BIN, YouTube del autor

---

## 21. Fallos del original que conviene no repetir

1. **Compilación debug publicada** (`debuggable=true`). Publica una release firmada y con R8.
2. **Clave de API en la URL** y en SharedPreferences sin cifrar.
3. **Base de datos en un GitHub de terceros sin verificar.** Usa tu propio repositorio, o firma o pon un hash al índice.
4. **Checksum del editor Samsung** calculado con el índice y no con el valor hex (sección 6.1).
5. **`searchPattern`** del módulo de pantalla partida: el bucle decompilado no avanza bien cuando hay un desajuste. Implementa la búsqueda bien (o con KMP) y trata `-1` como comodín.
6. **Texto de ayuda de eMMC** que dice CH341A, cuando en realidad usa un dongle USB Mass Storage.
7. **Varios drivers peleando por el mismo USB.** Centraliza la sesión USB.
8. **`WRITE_EXTERNAL_STORAGE` / `getExternalStoragePublicDirectory`**: en Android 10 o superior usa `MediaStore.Downloads` o SAF.

---

## 22. Plan de implementación por fases

1. **Base**: proyecto Compose, permisos USB, `UsbSessionManager`, driver CH341 SPI con lectura del JEDEC.
2. **Editor Samsung**: cargar `samsung_tcon_models.json`, leer y escribir los bytes VGH/VGL más los checksums, autodetección y respaldo.
3. **I2C**: driver CH341 I2C, EEPROM 24Cxx y EDID HDMI.
4. **PMIC**: `pmic_chips.json` más tus tablas de voltaje desde los datasheets; lectura, grabación y trigger.
5. **UART**: terminal con usb-serial-for-android, teclas, comandos, sistema automático y autorrecuperación; luego Bluetooth.
6. **IR**: codificador NEC, los JSON de códigos y las secuencias Samsung y LG.
7. **ADB WiFi**: protocolo, claves RSA, teclas y gestor de apps.
8. **eMMC ISP**: BOT/SCSI, volcado SMART o completo, y escritura.
9. **Extras**: pantalla partida, búsqueda en BIN, comparador, osciloscopio, calculadora, base de datos e IA.
10. **Licencia propia**, idiomas y publicación.

Prueba cada fase con hardware real y con un **respaldo previo**. Grabar mal un T-CON o un PMIC puede dejar el panel sin imagen.

---

## 23. Archivos de datos incluidos

| Archivo | Contenido |
|---|---|
| `data/AndroidManifest.decodificado.xml` | Manifiesto original en texto |
| `data/samsung_tcon_models.json` | 156 modelos de T-CON Samsung (offsets VGH/VGL y checksum), tablas VGH (114) y VGL (79), 32 firmas de autodetección |
| `data/pmic_chips.json` | 111 PMIC: dirección I2C, bloque, desbloqueo, trigger y offsets (sin tablas de voltaje) |
| `data/pmic_database.json` | 55 PMIC con registros VGH/VGL y rangos; 85 pinouts SDA/SCL |
| `data/division_repair_profiles.json` | 67 perfiles de reparación de pantalla partida |
| `data/ir_codes.json` | Códigos IR: 18 marcas de servicio, 11 controles raw, y los controles de servicio Samsung, LG y Kalley |
| `data/service_codes.json` | 366 procedimientos para entrar al modo de servicio por placa o modelo |
