// Fallas comunes por tipo de equipo. Cada tarjeta rellena el campo de diagnóstico.
window.DIAGNOSTICOS = {
  Celular: [
    { t: "No enciende", d: "Equipo no enciende: se revisa pin de carga, batería, línea de alimentación y corto en PMIC." },
    { t: "No carga", d: "No carga: se revisa pin/conector de carga, IC de carga (Tristar/U2) y riel VBUS." },
    { t: "Pantalla rota", d: "Reemplazo de pantalla/display por rotura. Se prueba táctil e imagen al instalar." },
    { t: "No da señal", d: "Sin señal/red: se revisa IC de señal, antena y configuración de banda." },
    { t: "Mojado", d: "Equipo mojado: limpieza ultrasónica de placa, revisión de corrosión y líneas en corto." },
    { t: "Software", d: "Falla de software: se realiza flasheo/restauración del firmware y pruebas." },
  ],
  Tablet: [
    { t: "No enciende", d: "No enciende: se revisa batería, carga y línea de alimentación." },
    { t: "Táctil falla", d: "Táctil intermitente: reemplazo de digitalizador y prueba de respuesta." },
    { t: "No carga", d: "No carga: pin de carga, flex e IC de carga." },
    { t: "Pantalla", d: "Reemplazo de display por imagen defectuosa o rotura." },
  ],
  PC: [
    { t: "No enciende", d: "No enciende: fuente de poder, botón de encendido y riel de alimentación en board." },
    { t: "No da video", d: "Sin video: GPU/chip de video, memoria RAM y conexión de panel/salida." },
    { t: "Lenta", d: "Rendimiento lento: limpieza, pasta térmica, revisión de disco y RAM." },
    { t: "No carga (laptop)", d: "Laptop no carga: cargador, pin DC-IN e IC de carga de la board." },
    { t: "Sobrecalienta", d: "Sobrecalentamiento: limpieza de disipador, cambio de pasta térmica y ventilador." },
  ],
  TV: [
    { t: "No enciende", d: "No enciende: fuente (power), riel de standby 5V y revisión de mainboard." },
    { t: "Pantalla negra con sonido", d: "Imagen negra con audio: backlight (tiras LED), driver de LED y VGH/VGL del panel." },
    { t: "Franjas/líneas", d: "Franjas o líneas verticales/horizontales: T-CON, COF/Source y voltaje VGH/VGL del panel." },
    { t: "Imagen partida", d: "Pantalla partida en dos: corrección de VGH en la memoria del panel (ver módulo de reparación)." },
    { t: "No da imagen", d: "Sin imagen: T-CON, cable LVDS/V-by-One y alimentación del panel." },
    { t: "Backlight", d: "Falla de retroiluminación: medición de tiras LED y driver de backlight." },
  ],
  Ventilador: [
    { t: "No gira", d: "No gira: capacitor de arranque, bobinado del motor y switch de velocidad." },
    { t: "Ruido", d: "Ruido/vibración: balineras/bujes del motor y balance de aspas." },
    { t: "Velocidad", d: "Velocidad fija o sin control: switch, capacitor y módulo de control." },
  ],
  Lavadora: [
    { t: "No enciende", d: "No enciende: toma, fusible, tarjeta de control y alimentación." },
    { t: "No centrifuga", d: "No centrifuga: motor, capacitor, correa y sensor de tapa." },
    { t: "No carga agua", d: "No carga agua: electroválvula, sensor de nivel y filtro de entrada." },
    { t: "No desagua", d: "No desagua: bomba de drenaje, filtro y manguera." },
  ],
  Otro: [
    { t: "Diagnóstico", d: "Equipo en diagnóstico: se revisa alimentación, componentes principales y síntoma reportado." },
    { t: "No enciende", d: "No enciende: se revisa fuente de alimentación y protecciones." },
  ],
};
