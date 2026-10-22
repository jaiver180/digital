# Versión 9.0 GOLD KING: novedades respecto a la 6.7

Complemento de la especificación principal (`../README.md`, versión 6.7). Aquí está **solo lo que cambia o se agrega** en la versión **9.0 GOLD KING** (`com.domotronicas.vgheditor`, versionCode 23).

**Fuentes**: los 15 `classes*.dex` decompilados con jadx (293 clases de la app), `AndroidManifest.xml`, `resources.arsc`, `i18n.tsv`, `autoscan_modelos_samsung.txt`, `autoscan_firmas.txt` e `index.html` del módulo de facturas.

**No llegaron**: `assets/facturas/js/app.js` y `js/diagnosticos.js`, que tienen la lógica interna del formulario de facturas. Lo de esa parte que sale del HTML va marcado como **(deducido)**.

Mismas reglas que en la 6.7: escribe tu propio código, con tu propia marca. No se incluye nada sobre cómo saltar la licencia (la clase nueva `LicenseControl` no se documenta).

---

## 1. Qué cambió en el manifiesto y la compilación

| | 6.7 | 9.0 GOLD KING |
|---|---|---|
| versionCode / versionName | 13 / 6.7 | **23 / 9.0 GOLD KING** |
| Permisos | — | **sin cambios** |
| `debuggable` | true | **true** (sigue siendo una compilación debug) |

**Pantallas nuevas**: `autoscan.AutoScanSamsungActivity`, `facturas.FacturasActivity`, `hexeditor.HexEditorActivity`, `panelid.PanelIdIcSonyActivity`, `tvtester.TvTesterActivity` y `tvtester.TvTesterFullscreenActivity` (horizontal, `screenOrientation=6`).

**Versiones de compilación**

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| `androidx.activity` (+ `-ktx`, `-compose`) | 1.9.1 |
| `androidx.appcompat` (+ `-resources`) | 1.7.0 |
| `androidx.compose.animation` (+ `-core`) | 1.6.8 |
| `annotation-experimental` | 1.4.0 |
| `autofill`, `cardview` | 1.0.0 |

**SharedPreferences que usa**: `adb_keypair`, `dy_license_control`, `dy_locale_prefs`, `facturas_domotronicas_prefs`, `gemini_assistant_prefs` y `secure_license_prefs`.

> **Versión para PC.** `i18n.tsv` es compartido con una versión para Windows hecha en **Python**: usa `CH341DLL(A64).DLL` (driver CH341PAR) y su módulo de IA está en `core/gemini_assistant.py`. Hay textos que **solo existen en la versión de PC** y no tienen código en el APK:
> - ACTIVAR/DESACTIVAR AUTOSCAN (314/413);
> - "Lectura SPI/I2C inestable";
> - "CS probado en …";
> - "ESCANEO I2C: responden …";
> - el manejo de teclas de las señales de prueba con clic derecho, AvPág, etc.

---

## 2. Autoscan Samsung

Clases: `autoscan/ModelDatabase`, `BinScanner`, `FingerprintStore` y `AutoScanSamsungActivity`.

**Para qué sirve**: abres un `.bin` de un T-CON Samsung y te dice **con qué modelo de la lista** se modifica VGH/VGL. No modifica el archivo.

### 2.1 Lista de modelos y grupos

- Se carga desde `assets/autoscan_modelos_samsung.txt`: **159 líneas**, convertidas en `data/autoscan_modelos_y_grupos.json`.
- El formato es el de una línea C, leída con esta expresión regular:

```
{"MODELO", base, vghPos, vglPos, hasSecond, base2, vgh2Pos, vgl2Pos},
```

Cada número puede ir en hex (`0x…`) o en decimal.

> **Diferencia importante con el editor.** En el editor Samsung, los últimos tres campos son el **checksum**. En el Autoscan se interpretan como **una segunda copia** del bloque: `hasSecond`, `base2`, y las posiciones de VGH y VGL dentro de esa copia.

**configKey** (identifica el grupo):
- con segunda copia: `%08X|%02X|%02X|%08X|%02X|%02X` (base, vgh, vgl, base2, vgh2, vgl2);
- sin segunda copia: `%08X|%02X|%02X|-`.

Los modelos con la misma `configKey` forman un **grupo**, numerado en el orden en que aparece por primera vez. Salen **19 grupos**.

**Diferencias con la base de la 6.7**:
- Modelos nuevos: `QN55Q60BAG`, `QN50Q65BAKXZL` y `UN55TU7020G`.
- `UN50DU7700` aparece **dos veces** con direcciones distintas: es un caso de "ELEGIR".
- Ya no está `CS601C TCOM` (I2C).

"Importar lista de modelos (.txt)" agrega más líneas con el mismo formato, pero **solo para la sesión actual** (no se guardan en disco).

**Funciones de normalización** (para comparar nombres):

```kotlin
fun normalize(s) = s.uppercase().replace(Regex("[^A-Z0-9]"), "")
fun coreOf(model) = model.uppercase().trim().split(Regex("[-_ ]"))[0]   // "UN55AU7000-Colombia" → "UN55AU7000"
// serie: ^[A-Z]{2}\d{2}([A-Z]{1,2})(\d{2,4})([A-Z]?) sobre coreOf
//   UN55AU7000 → "AU7000";  QN55Q60BAG → "Q60B" (si el número tiene 2 cifras se agrega la letra siguiente)
//   solo vale si mide 4 caracteres o más
fun seriesOf(model): String?
```

### 2.2 Algoritmo `BinScanner.scan(bin, fileName, db, fps)` (confirmado en el código)

`normFile = normalize(nombre del archivo sin extensión)`

**Paso 1, candidatos válidos.** Para cada grupo `g` (`recipe` = el primer modelo del grupo):
1. Si `maxAddrNeeded ≥ bin.size` o `base + 16 > bin.size` → se descarta con "fuera del tamaño del archivo".
2. Si los 16 bytes desde `base` están **en blanco** (todos `0xFF` o todos `0x00`) → "bloque vacío".
3. Se lee `vgh = bin[base + vghPos]` y `vgl = bin[base + vglPos]`. Si alguno vale `0x00` o `0xFF` → "celda vacía, no es aquí".
4. Si tiene segunda copia y no está en blanco, se leen `vgh2` y `vgl2` (solo para informar; no descarta el grupo).
5. `fingerprint = fps.match(bin, g)` (sección 2.3).
6. `nameHits` = los modelos del grupo que pasan `nameMatchesModel(normFile, modelo)`.
7. El grupo se agrega a `valid` y al log como `VÁLIDO VGH=0x.. VGL=0x..`.

> Ojo: **no** se comprueba que el byte esté en la tabla de voltajes. Basta con que no sea `00` ni `FF`.

Si no queda ningún grupo válido → **NINGUNO**: "Ninguna dirección conocida tiene VGH/VGL con datos válidos."

**Paso 2, filtros en cascada.** Se empieza con `set = valid` y cada filtro se aplica solo si reduce el conjunto:

| # | Filtro | Cuándo se aplica |
|---|---|---|
| 1 | Candidatos con **firma** | si hay alguno y (son menos que `set` o es exactamente 1) |
| 2 | **Nombre completo** en el archivo: un modelo cuyo nombre normalizado es distinto de su `coreOf` (por ejemplo `UN55AU7000COLOMBIA`) aparece como token en `normFile` | si `set > 1` y reduce |
| 3 | Candidatos con `nameHits` | si `set > 1` y reduce |
| 4 | **Serie** (`seriesOf`) como token en `normFile` | si `set > 1` y reduce |
| 5 | **Texto dentro del BIN**: el `coreOf` de 9 caracteres o más aparece en el BIN (leído en ISO-8859-1 y en mayúsculas) | si `set > 1` y queda **exactamente 1** |

Cada filtro que se aplica agrega su motivo, por ejemplo "coincide con la firma de un BIN real de referencia (X)" o "el nombre del archivo tiene la serie AU7000".

**Paso 3, resultado.**
- `set.size == 1` → **EXACTO**. Motivo: "Detectado porque … y …". Si no se aplicó ningún filtro: "Es la única dirección + posición de la lista con VGH/VGL válidos" (o "Única coincidencia").
- `set.size > 1` → **ELEGIR**:
  - si todos los candidatos tienen `nameHits` con el mismo `coreOf`: "El modelo X tiene N opciones de dirección/posición (OPC1/OPC2, 25V/30V)… escoge";
  - si no: "quedan N grupos con direcciones DISTINTAS… escoge el de tu TV. Si lo guardas como referencia, la próxima vez será automático".
- **Aviso final**: si el nombre del archivo corresponde a modelos de la lista pero ninguno está en los grupos que quedaron: "OJO: el nombre del archivo indica X, pero en ESAS direcciones el BIN no tiene VGH/VGL válidos…".

**Coincidencia por nombre:**

```kotlin
// token: antes no puede haber una letra; después no puede haber un dígito
fun containsToken(hay, token) = cualquier aparición i donde
    (i == 0 || !hay[i-1].isLetter()) && (fin >= hay.length || !hay[fin].isDigit())

fun nameMatchesModel(normFile, model): Boolean {
    val core = normalize(coreOf(model))
    if (core.length >= 7 && containsToken(normFile, core)) return true
    // sin el prefijo de 2 letras ("UN"/"QN"/"UE"…), si mide 9 o más:
    if (core.length >= 9 && core[0].isLetter() && core[1].isLetter()) {
        val sinPrefijo = core.substring(2)        // "55AU7000"
        return aparece en normFile sin dígitos pegados antes ni después
    }
    return false
}
```

### 2.3 Firmas (`FingerprintStore`)

- **Formato de una línea**: `configKey;MODELO;HEX16;HEX16_2da|-`, con 32 caracteres hex por bloque.
- **Al guardar** ("GUARDAR ESTE BIN COMO REFERENCIA"): se copian los 16 bytes desde `base` (y desde `base2` si hay segunda copia).
- **Al comparar**: debe coincidir la `configKey`, y los 16 bytes deben ser iguales **ignorando las posiciones de VGH y VGL**, que son justamente las que cambian entre equipos. Si hay segunda copia, también debe coincidir, ignorando `vgh2Pos` y `vgl2Pos`.
- **Al agregar**: si la línea ya existe, no se duplica ("Esa firma ya estaba guardada").
- **Carga**: primero `assets/autoscan_firmas.txt` (viene vacío, solo con el encabezado) y después `filesDir/firmas_usuario.txt`, que es donde se guardan las del usuario.
- **Exportar**: crea `firmas_vgh_vgl.txt` con el encabezado `# Firmas VGH/VGL Scanner Samsung - DOMOTRONICASYONARHE`.
- **Importar**: solo acepta líneas con 4 campos y bloques de 16 bytes.
- **Borrar mis firmas**: borra `firmas_usuario.txt` y vuelve a cargar las de fábrica.

---

## 3. Facturación / Órdenes de servicio

### 3.1 Contenedor nativo (confirmado en el código)

`FacturasActivity` es un `WebView` con JavaScript y DOM storage activados que carga `file:///android_asset/facturas/index.html`.
- **Enlaces externos** (`wa.me`, etc.) se abren con `ACTION_VIEW`; solo `file:///android_asset/` se carga dentro del WebView.
- **Atrás** navega dentro del WebView mientras pueda.

**Puente `AndroidBridge`** (`@JavascriptInterface`):

| Método | Qué hace |
|---|---|
| `tr(text)` | Traduce un texto con el mismo sistema i18n de la app (sección 7) |
| `getItem(key)` / `setItem(key, value)` / `deleteItem(key)` | **Almacenamiento** en `SharedPreferences("facturas_domotronicas_prefs")`, como `commit()` síncrono. Las órdenes **no** van en `localStorage` |
| `openWhatsApp(phone, text)` | Quita lo que no sea dígito del teléfono y abre `https://wa.me/<num>?text=<Uri.encode(text)>`. Si falla: "No se pudo abrir WhatsApp" |
| `savePdfBase64(base64, fileName)` | En API 29 o superior lo guarda en `MediaStore.Downloads` (`IS_PENDING` 1 y luego 0); en versiones anteriores, en `Environment.DIRECTORY_DOWNLOADS`. Muestra un toast con el resultado |
| `printPage()` | `PrintManager.print("Facturas - Ticket", webView.createPrintDocumentAdapter(…))` |
| `toast(msg)` | Muestra un toast traducido |

**Traducción del HTML**: en `onPageFinished` se inyecta un script (`TR_JS`) que recorre el DOM:
- traduce nodos de texto y los atributos `placeholder` y `title`, y el `value` de los botones;
- ignora `SCRIPT`, `STYLE`, `TEXTAREA` y las `OPTION` que tienen `value`;
- normaliza espacios y respeta los espacios de los extremos;
- con un `MutationObserver` traduce también lo que se agrega después.

### 3.2 La interfaz web (de `index.html`; la lógica en sí está en `app.js`, que no llegó)

Arriba hay 5 botones: **+ Nueva orden**, **📋 Órdenes guardadas**, **💰 Contabilidad**, **☀️ Modo claro/oscuro** y **⚙️ Configuración**.

**Nueva orden.** Acciones: 💾 Guardar · 📄 Factura PDF · 🧾 Ticket térmico · 💬 WhatsApp / Factura · ＋ Nueva. Tiene 4 pestañas:

| Pestaña | Campos |
|---|---|
| 👤 Cliente | N° de orden (automático), Fecha, Cliente, Teléfono/WhatsApp, DNI/Cédula, N° de documento de factura (automático), Dirección/correo, Forma de pago (Efectivo, Transferencia, Tarjeta, Nequi/Daviplata, Bancolombia) |
| 🔧 Equipo y diagnóstico | Tipo de equipo (Celular, Tablet, PC, TV, Ventilador, Lavadora, Otro), Marca, Modelo, IMEI/Serie, Accesorios, menú de fallas comunes según el tipo (de `diagnosticos.js`), Daños al recibir, Falla declarada, Diagnóstico/Reparación |
| 🔒 Patrón / PIN | Patrón de 3×3 (SVG) o PIN con teclado numérico. Es de uso interno |
| 💵 Cobros | Recepción, Entrega, Presupuesto, Abono, Recargo %, Recargo $, Total, Saldo; Garantía (fecha, o días con "Calcular"); ✅ PAGADO / ⏳ NO PAGADO |

**Cálculos (deducidos):**

```
recargo$ = presupuesto × recargo% / 100
total    = presupuesto + recargo$
saldo    = total − abono
garantía_hasta = fecha_entrega_cliente + días_garantía
```

- **Listado**: buscador y estados `recibido` / `reparacion` / `entregado`.
- **Contabilidad**: órdenes totales, facturado y saldo pendiente, más la tabla de órdenes.
- **Configuración**: negocio, lema, NIT, teléfono, próximo N° de orden, **15 monedas** (COP, USD, MXN, ARS, PEN, CLP, UYU, BOB, VES, GTQ, DOP, CRC, PAB, EUR, BRL), idioma de la factura (es/en/pt) y cláusula legal editable.
- **PDF**: con jsPDF, y luego `AndroidBridge.savePdfBase64`.
- **Ticket térmico de 78 mm**: `@media print` más `AndroidBridge.printPage()`.

**Mensaje de WhatsApp** (de `i18n.tsv`):

```
*Cliente:* … / *Equipo:* … / *Falla declarada:* … / *Trabajo realizado:* …
*Presupuesto:* … / *Recargo:* … (N%) / *Abonado:* … / *Saldo a cobrar:* …
*Estado del pago:* … / *Fecha de entrega:* … / *Garantía (N días):* válida hasta …
<cláusula legal>
```

**Para tu app**: copia este mismo patrón (WebView más un puente). Además:
- **cifra** el patrón y el PIN del cliente (aquí se guardan en texto plano en SharedPreferences);
- valida el tamaño, porque SharedPreferences no está hecho para miles de órdenes. Para eso conviene Room/SQLite.

---

## 4. Editor hexadecimal completo (`HexEditorActivity`, Compose)

- **Desde archivo**: lo lee completo en memoria.
- **Desde el chip por SPI**: `detectCapacityBytes()` por JEDEC y después `readSector()` sector por sector (4 KB), con progreso.
- **Desde el chip por I2C**: se elige el preset 24Cxx y la dirección, y se lee en bloques con `readBytes(addr, offset, len, addrBytes)`.
- **Vista**: una lista virtualizada (`LazyColumn`) con 16 bytes por fila y columna ASCII. Al tocar un byte se edita en hex (00 a FF). Los bytes modificados se ven en rojo.
- **Guardar**: archivo completo en Descargas.
- **Grabar por SPI**: se recorre el volcado en sectores de 4096 bytes y se usa `writeSector()` (borrado, programación y verificación) en cada uno.
- **Grabar por I2C**: `writeBytes(addr, 0, data, addrBytes, pageSize, 5 ms)` con la página del preset.

---

## 5. Copia de Panel ID (Sony) (`PanelIdIcSonyActivity`)

1. Se elige el chip de `EepromPresets.i2cChips` (24C01…24C512) y la dirección I2C.
2. **Leer el ORIGINAL**: `EepromManager.readBytesToMemory(preset, addr)`. El resultado queda en memoria y se puede respaldar.
3. **Grabar en el NUEVO**: `writeAndVerifyBytes` con el mismo preset. Mensaje: "Grabación del panel NUEVO OK y verificada (N bytes)".
4. Si se intenta grabar sin haber leído antes, avisa: "⚠ Primero leé el panel ORIGINAL".

---

## 6. Señales de prueba (`TvPatternView`, `TvPatterns`)

Es una `View` con `onDraw` de 10 patrones, sin antialias:

| ID | Nombre | Cómo se dibuja |
|---|---|---|
| 4 | Carta de Barras SMPTE | 8 barras verticales: `FFFFFF, FFFF00, 00FFFF, 00FF00, FF00FF, FF0000, 0000FF, 000000` |
| 0 / 1 / 5 | Canal Rojo / Verde / Azul 100% | `drawColor` del color puro |
| 2 / 3 | Campo Blanco / Negro Total | blanco o negro en toda la pantalla |
| 6 | Rampa de Grises · 16 | 16 barras con `gris = i × 17` (0 a 255) |
| 7 | Cuadrícula de Geometría | fondo negro con 16×10 rectángulos de contorno blanco, línea de `max(1, densidad × 0.5)` px |
| 8 | Franjas Horizontales | 20 franjas alternadas blanco/negro |
| 9 | Franjas Verticales | 30 franjas alternadas blanco/negro |

Los límites de cada barra se redondean (`Math.round(i·w/n)`) para que no queden huecos de un píxel.

**Orden de navegación**: `ORDER = [4, 0, 1, 5, 2, 3, 6, 7, 8, 9]`. Para avanzar o retroceder: `next(p, ±1)` circular.

**Contenido de la pantalla**:
- cada señal tiene nombre, etiqueta (por ejemplo "Gate · CKV · VGH/VGL") y 3 textos: ¿PARA QUÉ SIRVE?, ¿QUÉ DEBES OBSERVAR? y FALLAS QUE DETECTA;
- guía de conexión: USB-C a HDMI (DP Alt Mode), duplicación inalámbrica, banco de trabajo y ajustes del TV;
- todos esos textos están en `data/i18n_es_en_pt.json`.

**Pantalla completa** (`TvTesterFullscreenActivity`):
- en horizontal, con las barras del sistema ocultas y la pantalla siempre encendida;
- **1 toque**: siguiente señal; **toque largo**: anterior; **2 toques o Atrás**: salir;
- el nombre de la señal aparece un momento y se oculta.

---

## 7. Traducción en tiempo de ejecución (`common/Tr` y `TrCore`)

La app **no usa `strings.xml`**. Escribe todos los textos en español y los traduce al vuelo:

1. **Carga**: `assets/i18n.tsv` se lee la primera vez que se traduce algo. Cada línea es `TIPO\tes\ten\tpt` y admite los escapes `\n`, `\t` y `\r`.
2. **`E` (exacto, 1488 textos)**: un `HashMap<es, [en, pt]>`.
3. **`T` (plantilla, 397 textos)**: cada `⟦n⟧` se convierte en el grupo `(.*?)` de una expresión regular. Se guarda el literal más largo, que sirve de filtro rápido antes de probar la expresión, y el orden de los grupos. Al traducir, se rellenan los `⟦n⟧` de la versión en/pt con lo capturado.
4. **Si no hay coincidencia directa**: se separa un prefijo de símbolos o emoji (regex `^([^\p{L}\p{N}¿¡(<\[]+)(\S.*)$`) y se traduce el resto; también se parte por los separadores `" — "`, `" · "`, `"  |  "` y `" | "` y se traduce cada trozo.
5. **Caché** de resultados (`HashMap` de 2048 entradas).
6. **Idioma**: lo maneja `LocaleManager` (ES, EN o PT); con español devuelve el texto sin cambios. En Compose se usa mediante `TrTextKt`.

**Para tu app**: puedes generar `strings.xml` (values, values-en, values-pt) a partir de `data/i18n_es_en_pt.json`, o copiar este esquema de traducción al vuelo, que es cómodo si el código ya está todo en español.

---

## 8. Mejoras en módulos que ya existían

### 8.1 PMIC: lectura robusta de la familia SW (`readSwFamily`)

El driver I2C ahora tiene:
- `SPEED_SLOW_20K = 0`;
- `probeAck(addr)`: envía `[AA 74 80 addr<<1 75 00]` y lee el estado; `bit7 = 0` significa ACK;
- `drainIn()`: vacía el endpoint IN con lecturas de 5 ms;
- `writePointer(addr, reg)`: escritura con STOP;
- `readCurrent(addr, len)`: lectura sin escribir antes el puntero, en bloques de 26 bytes.

La lectura **prueba varios métodos en orden** y se queda con el primero cuyos valores de VGH y VGL **están dentro de la tabla del chip**:

| Método | Secuencia |
|---|---|
| `O-FFxx` (metodoCtrl) | escribir `0xFF ← xx`, esperar `readDelayMs`, leer el bloque desde el registro 0 (repeated start) |
| Repeated | `0xFF ← 0x00`, esperar, `readBytes(addr, 0, len)` |
| Stop | `0xFF ← 0x00`, esperar, `writePointer(addr, 0)` (con STOP), 2 ms, `readCurrent` |
| `N-sinFF` | leer sin tocar `0xFF` |
| `A-20kHz` / `B-20kHz` | repetir los métodos a 20 kHz, para cables largos o pinzas malas |

Si ningún método da valores dentro de la tabla, `probeAck` diagnostica el problema:
- "el chip SÍ responde (ACK) en 0x.. → hay comunicación pero los datos no cuadran";
- "NADIE responde en 0x.. → revisar SDA/SCL, GND, alimentación del PMIC o pinza".

También son nuevos: `readRt43` / `saveRt43` (familia RT6943, incluido el modo "RT43 FF01 (versión original)"), `readCs60x`, `saveSwFamily`, `writeSingleRegister(chip, offset, valor)` (graba un solo registro) y `writeVghUnlockByte`. La base de datos agrega `PmicFamilyGroups`.

### 8.2 ADB por red: INSTALAR APK

1. `push(apk, "/data/local/tmp/dy_instalar.apk")` por el **protocolo sync**: `OPEN "sync:"`, luego `SEND` con `"ruta,33188"` (modo 0644), bloques `DATA` de hasta **64 KB**, `DONE` con fecha, esperar `OKAY` (o `FAIL` con el mensaje), `QUIT` y cerrar con `CLSE`.
2. `shell("pm install -r -g …")`. Si el TV responde "Unknown option", reintenta sin `-g`.
3. Si aparece `INSTALL_FAILED_UPDATE_INCOMPATIBLE` o un error de versión, explica que la app ya existe con otra firma o en una versión más nueva.

### 8.3 Otros

- **Precisión VGH/VGL**: el botón **"FUERZA BRUTA (ADN)"** corresponde a `bruteForce = true` (todos los candidatos con puntaje 0).
- **Menú principal**: nueva tarjeta "AUTOSCAN BIN VGH VGL SAMSUNG" (🔎), una `WelcomeScreen` y una tarjeta de FACTURAS.
- **Licencia**: ahora bloquea por función ("🔒 Función bloqueada: activa la licencia primero").

---

## 9. Archivos de esta carpeta

| Archivo | Contenido |
|---|---|
| `data/AndroidManifest.v9.decodificado.xml` | Manifiesto 9.0 en texto |
| `data/autoscan_modelos_y_grupos.json` | Los 159 modelos y los 19 grupos. Los campos `checksum`, `base_check`, `vgh_chk_pos` y `vgl_chk_pos` equivalen a `hasSecond`, `base2`, `vgh2Pos` y `vgl2Pos` del Autoscan |
| `data/i18n_es_en_pt.json` | Los 1885 textos `{tipo E/T, es, en, pt}` |

## 10. Qué falta

- `assets/facturas/js/app.js` y `js/diagnosticos.js`, para documentar exactamente el guardado de las órdenes, el diseño del PDF y la lista de fallas por tipo de equipo.
