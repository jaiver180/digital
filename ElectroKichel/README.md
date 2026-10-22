# ElectroKichel

App Android de herramientas de servicio técnico para TV y electrónica, con la marca **ElectroKichel**.
Reúne todas las funciones de la especificación (`../vgh-editor-spec/`): editor VGH/VGL Samsung por CH341A, PMIC por I2C, programador EEPROM/SPI, clínica HDMI, recuperación UART, controles de servicio IR, autoscan de BIN, señales de prueba, osciloscopio, facturación, y más.

> **Splash 3D**: al abrir, el logo gira 360° en 3D con perspectiva, rebote de escala y halo pulsante (`ui/Splash.kt`).

---

## Cómo compilar el APK (Android Studio)

1. Abre **Android Studio** (Koala o posterior) → *Open* → selecciona la carpeta `ElectroKichel/`.
2. Deja que sincronice Gradle (descarga el SDK 34 y las dependencias automáticamente la primera vez; necesita internet).
3. Conecta un teléfono con depuración USB o usa un emulador, y pulsa **Run ▶**.
4. Para generar el APK instalable: menú **Build → Build Bundle(s) / APK(s) → Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

Desde consola (con el SDK instalado y `local.properties` apuntando a él):

```bash
./gradlew assembleDebug      # APK de depuración
./gradlew assembleRelease    # APK de release (sin firmar; configura tu keystore para firmarlo)
```

**Requisitos**: JDK 17, Android SDK Platform 34, Gradle 8.7 (lo trae el wrapper).

> No se incluye el APK ya compilado: este proyecto se generó en un entorno sin acceso al repositorio de Google (SDK de Android), así que la compilación la haces tú con un clic en Android Studio. Todo el código y los datos están listos.

---

## Estructura

```
app/src/main/
 ├─ java/com/electrokichel/app/
 │   ├─ ElectroApp.kt, MainActivity.kt
 │   ├─ core/usb/      Ch341 (SPI+I2C), UsbHub, PmicProtocol
 │   ├─ core/adb/      AdbCrypto (RSA), AdbConnection (protocolo ADB)
 │   ├─ core/ir/       Ir (codificador NEC/Samsung32)
 │   ├─ core/engine/   SamsungEditor, AutoScan, BinTools, EepromPresets
 │   ├─ core/data/     Repo (carga de assets), Models
 │   ├─ ui/            Splash 3D, Home, Theme, UiKit
 │   └─ feature/       una pantalla por módulo
 └─ assets/            datos JSON + app web de facturas
```

## Módulos (22)

| Módulo | Qué hace |
|---|---|
| Editor VGH/VGL Samsung | Lee y graba VGH/VGL por CH341A/SPI, con autodetección y checksum |
| Editor Parche Samsung (BIN) | Corrige VGH/VGL en un .bin ya volcado |
| Autoscan BIN Samsung | Identifica el modelo exacto (firmas, nombre, serie, grupos) |
| Precisión VGH/VGL en BIN | Búsqueda heurística para modelos desconocidos |
| Reparación imagen dividida | 67 perfiles de "pantalla partida" 4K |
| PMIC LG / Chinos (I2C) | 114 chips: lee/graba el bloque, trigger por familia, escaneo I2C |
| I2C Samsung en vivo | VGH/VGL por I2C 0x20 |
| Programador SPI / I2C | EEPROM 24Cxx y Flash 25xx: leer, respaldar, grabar |
| Editor hexadecimal | Edita cualquier .bin (archivo o chip) byte a byte |
| Clínica HDMI (EDID) | Lee, valida, repara checksum y graba el EDID (I2C 0x50) |
| Copia Panel ID (Sony) | Copia la EEPROM del panel original al de reemplazo |
| Recuperación UART / TV Box | Consola serie, teclas y comandos recovery por tecnología |
| Controles de servicio IR | Samsung, LG, Kalley y decenas de marcas |
| Control remoto por red (ADB) | Maneja un TV Android por WiFi (protocolo ADB + firma RSA) |
| Señales de prueba (TV) | 10 patrones de diagnóstico + pantalla completa |
| Osciloscopio | Entrada de audio como osciloscopio |
| Comparador de BIN | Compara dos .bin byte a byte |
| Ingreso modo servicio | 366 códigos por placa/modelo |
| Centro de datos PMIC | 55 PMIC ISP + 85 pinouts |
| Calculadora electrónica | Ohm, serie/paralelo, SMD |
| Facturas / Órdenes | Órdenes de servicio, PDF, WhatsApp, ticket térmico |

## Datos (assets/)

Los datos reales van en `assets/` como JSON y se cargan en `Repo`: 156 modelos Samsung, 114 PMIC, 67 perfiles de reparación, ~1100 códigos IR, 366 códigos de servicio, 55 PMIC ISP, 85 pinouts, y el .txt de 159 modelos del autoscan.

## Hardware

- **CH341A** (VID 1A86, PID 5512/5523/7523) por OTG para SPI/I2C.
- **Conversor USB-serie** (CH340/CP2102/FTDI/PL2303) para UART.
- **Emisor IR** del teléfono o dongle IR USB-C.
- **Entrada de micrófono** (con divisor resistivo) para el osciloscopio.

## Avisos

- **Permisos**: la primera vez, Android pide permiso para el dispositivo USB y para el micrófono (osciloscopio).
- **Precaución**: grabar mal un T-CON o un PMIC puede dejar el panel sin imagen. Haz siempre un respaldo antes de grabar.
- Las tablas de voltaje de los PMIC chinos no venían en el APK original (estaban cifradas): el módulo PMIC ajusta por **byte crudo**. Puedes agregar tablas voltaje↔byte desde los datasheets.
- Sin licencia ni bloqueos: todas las funciones están abiertas.
