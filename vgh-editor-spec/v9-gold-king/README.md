# Versión 9.0 GOLD KING: novedades respecto a la 6.7

Complemento de la especificación principal (`../README.md`, versión 6.7). Aquí está **solo lo que cambia o se agrega** en la versión **9.0 GOLD KING** (`com.domotronicas.vgheditor`, versionCode 23).

> **Qué archivos llegaron.** Para esta versión no recibí los `classes*.dex`, así que no hubo código para decompilar. El documento se basa en:
> - `AndroidManifest.xml`, decodificado en `data/AndroidManifest.v9.decodificado.xml`;
> - `i18n.tsv`: los 1885 textos de la interfaz en español, inglés y portugués;
> - `autoscan_modelos_samsung.txt` y `autoscan_firmas.txt` (este último vacío, solo trae el encabezado);
> - `index.html` del módulo de facturas (sin `js/app.js` ni `js/diagnosticos.js`);
> - los archivos de versión de librerías y Gradle.
>
> Lo que se deduce de los textos de la interfaz va marcado como **(deducido)**. Para conocer los algoritmos exactos de los módulos nuevos hacen falta los `.dex`.

Mismas reglas que en la 6.7: escribe tu propio código, con tu propia marca. No se incluye nada sobre cómo saltar la licencia.

---

## 1. Qué cambió en el manifiesto

| | 6.7 | 9.0 GOLD KING |
|---|---|---|
| versionCode / versionName | 13 / 6.7 | **23 / 9.0 GOLD KING** |
| Permisos | — | **sin cambios** |
| `debuggable` | true | **true** (sigue siendo una compilación debug) |

**Pantallas nuevas**

| Activity | Función |
|---|---|
| `autoscan.AutoScanSamsungActivity` | Autoscan: identifica el modelo Samsung a partir de un .bin |
| `facturas.FacturasActivity` | Órdenes de servicio y facturación (WebView con `index.html`) |
| `hexeditor.HexEditorActivity` | Editor hexadecimal completo, que lee del archivo o del chip |
| `panelid.PanelIdIcSonyActivity` | Copia del Panel ID (EEPROM 24Cxx) del panel original al de reemplazo (Sony) |
| `tvtester.TvTesterActivity` | Generador de señales de prueba |
| `tvtester.TvTesterFullscreenActivity` | La señal a pantalla completa, en horizontal |

**Versiones de compilación detectadas**

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| `androidx.activity` (+ `-ktx`, `-compose`) | 1.9.1 |
| `androidx.appcompat` (+ `-resources`) | 1.7.0 |
| `androidx.compose.animation` (+ `-core`) | 1.6.8 (BOM 2024.06.00) |
| `annotation-experimental` | 1.4.0 |
| `autofill`, `cardview` | 1.0.0 |

Los textos mencionan también **una versión para PC**:
- usa `CH341DLL(A64).DLL` (el driver CH341PAR de WCH) en una carpeta `driver_dll`;
- su módulo de IA está en `core/gemini_assistant.py`, así que esa versión está hecha en **Python**;
- su licencia es "una por PC".

---

## 2. Autoscan Samsung

**Para qué sirve**: abres un `.bin` de un T-CON Samsung y te dice **con qué modelo de la lista** se modifica VGH/VGL. **No modifica el archivo.** Después el usuario va al Editor Parche Samsung o al Editor VGH/VGL con ese modelo.

### 2.1 Lista de modelos

`data/autoscan_modelos_y_grupos.json` tiene **159 entradas**, en el mismo formato que la 6.7:

```
{"MODELO", base_main, vgh_pos, vgl_pos, checksum, base_check, vgh_chk_pos, vgl_chk_pos}
```

Diferencias con la base de la 6.7:
- **Modelos nuevos**: `QN55Q60BAG`, `QN50Q65BAKXZL`, `UN55TU7020G`.
- **Modelo con dos opciones**: `UN50DU7700` aparece dos veces, una con base `0x06F000` (checksum en `0x06F160`) y otra con `0x04F000`. Es un caso de **ESCOGER**.
- **Modelo que ya no está**: `CS601C TCOM ST4251B05-2C-1`, que es I2C; el autoscan solo cubre SPI.

**Grupos.** Los modelos se agrupan por la combinación (dirección base + posiciones + checksum), y modelos distintos que comparten combinación **se modifican igual**. Salen **19 grupos** (todos están en el JSON):

| Grupo | base_main | VGH | VGL | Checksum | Modelos |
|---|---|---|---|---|---|
| 1 | 0x04F000 | 0x0D | 0x0E | 0x04F160 (+3, +4) | 32 (AU7000, CU7000, DU7000…) |
| 2 | 0x041550 | 0x04 | 0x0B | — | 24 (Q60A/B/C, AU8000…) |
| 3 | 0x0B1750 | 0x08 | 0x0F | — | 14 (TU8000…) |
| 4 | 0x0CF000 | 0x0D | 0x0E | 0x0CF020 (+4, +5) | 14 (TU7000/7020…) |
| 5 | 0x0B1590 | 0x0A | 0x08 | — | 12 (NU7100…) |
| 6 | 0x0C1590 | 0x0A | 0x08 | — | 12 (MU6100…) |
| 7 | 0x04F000 | 0x0D | 0x0E | 0x04F160 (+3, **+0x0B**) | 10 (variantes "30V") |
| 8 a 19 | … | | | | 1 a 7 cada uno |

Los grupos 1 y 7 tienen las mismas direcciones y solo se diferencian en la posición del checksum de VGL. Con los bytes del archivo no siempre se pueden distinguir, por eso existe "ESCOGER" (25V/30V).

### 2.2 Cómo decide (deducido de los textos de ayuda)

> "CÓMO DECIDE (sin adivinar): firma de un BIN real guardado como referencia, modelo o serie en el nombre del archivo, o modelo escrito dentro del BIN."

Algoritmo propuesto para tu app:

1. **Filtrar los grupos válidos.** Un grupo es válido para este archivo si:
   - todas sus direcciones (`base_main + vgh_pos`, `base_main + vgl_pos` y, si tiene, las del checksum) caen dentro del tamaño del archivo; si no, aparece el aviso *"Las direcciones de este modelo caen fuera del tamaño real del archivo"*;
   - el byte de VGH está en la tabla VGH y el de VGL en la tabla VGL (tablas globales de la 6.7). La interfaz muestra `VÁLIDO VGH=… VGL=…`;
   - opcionalmente, el bloque no es todo `0xFF` ni todo `0x00`.
2. **Desempatar**, en este orden de prioridad:
   1. **Firma guardada**: los 16 bytes desde `base_main` (y, si existe, una segunda copia de 16 bytes) coinciden con una firma de referencia → **EXACTO**.
   2. **Modelo en el nombre del archivo** (por ejemplo `UN55RU7300.bin`), comparando sin distinguir mayúsculas → EXACTO.
   3. **Serie en el nombre** (`TU7000`, `AU8000`, `RU7100`…) → limita los candidatos a los grupos de esa serie.
   4. **Modelo escrito dentro del BIN**: buscar en el archivo, como texto ASCII, los nombres de modelo de la lista.
   - Aviso: si el nombre del archivo indica un modelo pero sus direcciones no tienen datos válidos, mostrar *"OJO: el nombre del archivo indica X, pero en ESAS direcciones el BIN no tiene VGH/VGL válidos"*.
3. **Resultado**:
   - **✅ MODELO EXACTO**: un solo grupo. Se muestra el modelo a usar, los demás modelos que se modifican igual, las direcciones y los voltajes originales.
   - **⚠ ESCOGER / VARIOS CANDIDATOS**: varios grupos con datos válidos (OPC1/OPC2, 25V/30V). El usuario elige con "ES ESTE (ver detalle / guardar como referencia)".
   - **❌ NINGUNO**: *"Este BIN no corresponde a ninguna dirección + posición de la lista"*. Se ofrece ⋮ → Importar lista de modelos.

### 2.3 Firmas de referencia

Formato del archivo de texto (sacado del encabezado de `autoscan_firmas.txt`):

```
# CLAVE_GRUPO;MODELO;HEX_BLOQUE_16_BYTES;HEX_2DA_COPIA_16_BYTES o -
```

Funciones de la interfaz:
- "GUARDAR ESTE BIN COMO REFERENCIA": crea la firma con los 16 bytes del bloque.
- Avisos: "Esa firma ya estaba guardada" y "No se pudo crear la firma".
- Menú ⋮: Exportar firmas (.txt), Importar firmas (.txt) ("No había firmas nuevas en el archivo"), Borrar mis firmas, Importar lista de modelos (.txt).

El archivo de firmas de fábrica que llegó está **vacío**, así que la app viene sin firmas y el usuario las crea con BINs reales.

**ACTIVAR AUTOSCAN (314) / DESACTIVAR AUTOSCAN (413)**: son dos opciones de menú con un número entre paréntesis. Parecen un interruptor oculto con código, pero sin los `.dex` no se puede saber para qué sirve el número. En tu app basta con una opción normal en Ajustes.

---

## 3. Facturación / Órdenes de servicio

Es una **app web local** (`index.html` más `js/app.js`, `js/diagnosticos.js`, `js/jspdf.umd.min.js`, `js/logos.js` e `images/header_logo.png`) que se carga en un **WebView** dentro de `FacturasActivity`. Funciona sin internet: las órdenes se guardan solo en el dispositivo (deducido: `localStorage`). Para el PDF y la impresión usa un puente nativo `AndroidBridge`, según un comentario del propio HTML.

### 3.1 Vistas

Arriba hay 5 botones: **+ Nueva orden**, **📋 Órdenes guardadas**, **💰 Contabilidad**, **☀️ Modo claro/oscuro** y **⚙️ Configuración**.

**Nueva orden.** Acciones siempre visibles: 💾 Guardar · 📄 Factura PDF · 🧾 Ticket térmico · 💬 WhatsApp / Factura · ＋ Nueva. Tiene 4 pestañas:

| Pestaña | Campos |
|---|---|
| 👤 Cliente | N° de orden (automático, solo lectura), Fecha, Cliente, Teléfono/WhatsApp, DNI/Cédula, N° de documento de factura (automático), Dirección/correo, Forma de pago (Efectivo, Transferencia, Tarjeta, Nequi/Daviplata, Bancolombia) |
| 🔧 Equipo y diagnóstico | Tipo de equipo con íconos (Celular 📱, Tablet 📲, PC 🖥️, TV 📺, Ventilador 🌀, Lavadora 🧺, Otro 📦), Marca, Modelo, IMEI/Serie, Accesorios, **menú de diagnóstico** (tarjetas con las fallas comunes del tipo elegido, que rellenan el texto técnico; vienen de `diagnosticos.js`), Daños al recibir, Falla declarada por el cliente, Diagnóstico/Reparación |
| 🔒 Patrón / PIN | Patrón de desbloqueo en una cuadrícula de 3×3 (con las líneas en SVG) o PIN con teclado numérico. Es de uso interno y no se comparte con el cliente |
| 💵 Cobros | Fecha de recepción, Fecha de entrega, Presupuesto, Seña/Abono, Recargo %, Recargo $, Total, Saldo; Garantía (fecha límite, o días con el botón "Calcular"); Estado de pago (✅ PAGADO / ⏳ NO PAGADO) |

**Cálculos** (deducidos de los campos):

```
recargo$ = presupuesto × recargo% / 100
total    = presupuesto + recargo$
saldo    = total − abono
garantía_hasta = fecha_entrega_cliente + días_garantía
```

Si no hay fecha de entrega, se muestra el aviso *"Cargá la fecha de entrega y los días de garantía primero"*.

El estado de pago elegido es el que sale en el PDF, el ticket y WhatsApp, **sin importar el saldo**.

**Órdenes guardadas**: buscador (por cliente, orden o marca) y tarjetas con una etiqueta de estado: `recibido` (verde), `reparacion` (naranja) o `entregado` (azul). Cada tarjeta tiene abrir, PDF, WhatsApp y eliminar. Eliminar pide confirmación: *"¿Eliminar la orden #N de X? Esta acción no se puede deshacer."*

**Contabilidad**: tres indicadores (Órdenes totales, Facturado total, Saldo pendiente) y una tabla con Orden, Cliente, Equipo, Total y Saldo. Si se usaron varias monedas, avisa que *"los montos mezclan monedas"*.

**Configuración**:
- Nombre del negocio, lema, NIT/RUT, teléfono del taller (WhatsApp) y próximo N° de orden.
- **Moneda**: COP, USD, MXN, ARS, PEN, CLP, UYU, BOB, VES, GTQ, DOP, CRC, PAB, EUR y BRL, cada una con su símbolo.
- **Idioma de la factura**: es, en o pt.
- **Cláusula legal** editable (por defecto, una cláusula de abandono a los 2 meses).
- Logo del taller.

### 3.2 Salidas

**PDF**: se genera con **jsPDF** y se guarda en **Descargas** ("PDF guardado en Descargas: …").

**Ticket térmico de 78 mm**: hay un `#ticketPrint` con estilo `@media print` (monospace, 11 px, separadores punteados). En la app nativa imprime `AndroidBridge`; si no, usa la impresión del navegador.

**WhatsApp**: abre `https://wa.me/<teléfono>?text=<mensaje>` con este formato (sale de los textos de la interfaz):

```
*Cliente:* …
*Equipo:* …
*Falla declarada:* …
*Trabajo realizado:* …
*Presupuesto:* …
*Recargo:* … (N%)
*Abonado:* …
*Saldo a cobrar:* …
*Estado del pago:* PAGADO ✅ | PENDIENTE ⏳
*Fecha de entrega:* …
*Garantía (N días):* válida hasta …
<cláusula legal>
```

Si la orden no tiene teléfono, muestra *"Esta orden no tiene teléfono cargado"*.

### 3.3 Cómo implementarlo

- **Rápido**: un WebView con tu propio HTML/JS y un `@JavascriptInterface AndroidBridge` que tenga `savePdf(base64, nombre)` (escribe en `MediaStore.Downloads`), `printTicket(html)` (`PrintManager`, o impresora térmica Bluetooth ESC/POS) y `openWhatsApp(url)`.
- **Nativo**: Compose más Room (tablas `Order` y `Settings`), `PdfDocument` de Android para el PDF, y comandos ESC/POS para la impresora térmica.
- **Datos**: guarda el patrón y el PIN cifrados (EncryptedSharedPreferences o SQLCipher). Son datos sensibles del cliente.

Esquema de una orden propuesto (deducido de los campos del formulario):

```json
{"orderNum":1,"date":"2026-10-22","client":"","phone":"","dni":"","docNum":"","address":"",
 "payMethod":"Efectivo","type":"TV","brand":"","model":"","imei":"","access":"",
 "faults":[],"damages":"","fault":"","repair":"","pattern":[0,1,2],"pin":"",
 "dateIn":"","dateOut":"","budget":0,"deposit":0,"surchargePct":0,
 "warrantyUntil":"","warrantyDays":30,"paid":true,"status":"recibido","currency":"COP"}
```

---

## 4. Editor hexadecimal completo

Funciones (sacadas de los textos de ayuda):
- Muestra un `.bin` de **cualquier tamaño** sin dividirlo en páginas: solo dibuja lo que se ve en pantalla (`LazyColumn`), con 16 columnas (00 a 0F) más la columna ASCII y desplazamiento horizontal.
- Origen de los datos: un archivo, o leer del chip por **SPI** (25Qxx, tamaño por JEDEC) o **I2C** (24Cxx, eligiendo chip y dirección).
- Al tocar un byte se edita a mano. Los bytes cambiados se marcan **en rojo** hasta que se guarda o se graba.
- GUARDAR ARCHIVO escribe el archivo completo en Descargas. GRABAR escribe en el chip, con verificación.
- Nuevos avisos de lectura inestable: *"Lectura SPI/I2C inestable en 0x… (N/M bytes distintos entre intentos consecutivos tras K reintentos)"*. Sugiere que ahora cada bloque **se lee dos veces y se comparan**. Conviene copiarlo.

---

## 5. Copia de Panel ID (Sony)

Copia la EEPROM de identificación (I2C 24Cxx) del panel **original** al panel **nuevo**, para que el TV reconozca el reemplazo:

1. **Paso 1, panel ORIGINAL**: elegir el chip (buscador, por ejemplo "24C04") y la dirección, leer y guardar el respaldo. Mensaje: "Lectura del panel ORIGINAL OK (N bytes)".
2. **Paso 2, panel NUEVO**: conectar su EEPROM, grabar y verificar. Mensaje: "Grabación del panel NUEVO OK y verificada (N bytes)". Si no se hizo el paso 1, avisa: "⚠ Primero leé el panel ORIGINAL".

Usa el mismo driver I2C del CH341A (sección 5 del documento principal) y los presets 24Cxx (sección 7).

---

## 6. Señales de prueba (TV Tester)

Es un generador de patrones para diagnosticar paneles LCD, LED y OLED. Se lleva la imagen al TV por **USB-C → HDMI** (DisplayPort Alt Mode) o por **duplicación inalámbrica** (Smart View / Miracast).

| Señal | Qué es | Para qué sirve |
|---|---|---|
| Carta de barras SMPTE | 8 barras al 100%: blanco, amarillo, cian, verde, magenta, rojo, azul, negro | Canales R/G/B, bits LVDS/V-by-One, T-CON |
| Canal Rojo / Verde / Azul 100% | Pantalla llena de un solo color | Subpíxeles muertos o atascados, COF |
| Campo blanco total | Todo blanco al 100% | Backlight (LED, difusores), fuente al máximo de carga |
| Campo negro total | Todo negro | Fugas de luz, píxeles atascados, VCOM |
| Rampa de grises · 16 | 16 escalones de 0 a 255 | Gamma, balance de blancos, VCOM |
| Cuadrícula de geometría | 16×10 casillas blancas sobre fondo negro | Overscan, escala, mitades desfasadas |
| Franjas horizontales | 20 franjas blanco/negro | Drivers de filas (Gate/GIP), CKV/CKVB, **VGH/VGL** |
| Franjas verticales | 30 franjas blanco/negro | Drivers de columna (Source/COF), tab bonding |

Además hay grupos "PATRÓN MAIN" y "PATRÓN TCON". Cada tarjeta tiene un botón **i** con tres secciones: ¿PARA QUÉ SIRVE?, ¿QUÉ DEBES OBSERVAR? y FALLAS QUE DETECTA. Todos esos textos están en `data/i18n_es_en_pt.json`.

**Pantalla completa** (`TvTesterFullscreenActivity`):
- horizontal, en modo inmersivo y con `FLAG_KEEP_SCREEN_ON`;
- **1 toque**: siguiente señal; **toque largo**: anterior; **2 toques o Atrás**: salir;
- el nombre de la señal aparece un momento y se oculta.

La pantalla principal tiene un **monitor de vista previa**: tocarlo pasa a la siguiente señal.

**Guía "Conectar al TV"**:
1. Por cable (recomendado).
2. Inalámbrico, duplicando la pantalla.
3. En el banco de trabajo: usar las franjas H/V mientras se ajustan VGH, VGL y VCOM con el editor de PMIC.
4. Ajustes del TV para medir bien: Just Scan / Ajuste a pantalla, modo Estándar, nitidez en 0 y ahorro de energía desactivado.

Implementación: un `Canvas` de Compose que dibuja cada patrón según `size` (son patrones matemáticos simples), más `WindowInsetsControllerCompat` para ocultar las barras del sistema.

---

## 7. Otros cambios que se ven en los textos

- **Precisión VGH/VGL**: aparece el modo **"FUERZA BRUTA (ADN)"**, que corresponde al `bruteForce = true` de la 6.7 (puntaje 0, sin voltaje objetivo), y una sección "ANÁLISIS CON VOLTAJE".
- **Editor Parche Samsung**:
  - nuevo aviso cuando no se encuentra el voltaje: *"VGH: código (sin voltaje confirmado)"*;
  - "Cargar backup (.bin)" para modelos I2C como el CS601C TCOM;
  - "GRABAR DIRECTO EN LA MEMORIA" solo escribe los bytes de VGH y VGL.
- **PMIC**:
  - **escaneo del bus I2C** (*"responden 0x…, pero NO 0x…"*), que prueba todas las direcciones para ver cuáles contestan;
  - modo "RT43 FF01 (versión original)".
- **Prueba del CS en SPI**: *"CS probado en … pero JEDEC ID = … (sin respuesta real del chip)"*.
- **ADB por red**: botón **INSTALAR APK** (ADB `sync:` + `pm install`, o `exec:cmd package install -S <tamaño>` enviando el APK por el stream) y mensajes de error de autenticación más claros.
- **Control LG**: *"Los 60 botones de arriba fueron clonados directo de tu control de servicio LG original"*.
- **Entrada 🎤 VOZ**: probablemente dictado por voz (`RecognizerIntent`) para la terminal o los buscadores. Eso explicaría el permiso `RECORD_AUDIO` además del osciloscopio.
- **Licencia**: mismo esquema que la 6.7. Los textos nuevos dicen *"🔒 Requiere licencia activa (se pide al entrar)"* y *"🔒 Función bloqueada: activa la licencia primero"*, o sea que ahora el bloqueo es por función.
- **Idiomas**: los 1885 textos están completos en es/en/pt (no falta ninguna traducción). Las variables van como `⟦0⟧`, `⟦1⟧`, … y algunos textos traen HTML (`<b>`, `<span style>`), que se muestra con `Html.fromHtml` o `AnnotatedString`. La columna 1 del TSV dice `E` (texto exacto, 1488) o `T` (plantilla con variables, 397).

---

## 8. Archivos de esta carpeta

| Archivo | Contenido |
|---|---|
| `data/AndroidManifest.v9.decodificado.xml` | Manifiesto 9.0 en texto |
| `data/autoscan_modelos_y_grupos.json` | 159 modelos y los 19 grupos de dirección+posición |
| `data/i18n_es_en_pt.json` | 1885 textos `{tipo, es, en, pt}` (sirve para armar los `strings.xml`) |

## 9. Qué falta para completar la 9.0

Para describir los algoritmos internos de Autoscan, Facturas, Hex Editor, Panel ID y TV Tester con la misma precisión que la 6.7, necesito:
1. los `classes*.dex` de la versión 9.0 (o el `.apk` completo);
2. `assets/js/app.js` y `assets/js/diagnosticos.js` (la lógica de facturas y la lista de fallas por tipo de equipo).
