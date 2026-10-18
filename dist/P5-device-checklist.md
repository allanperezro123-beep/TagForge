# Tag Forge - Fase 5 (Productividad): lista de verificación en dispositivo

APK: `dist/tag-forge-p5-debug.apk` (versión 0.5.0-debug, versionCode 500, paquete `com.tagforge.app.debug`).

## Instalación

1. `adb install -r dist/tag-forge-p5-debug.apk` (actualiza sobre la Fase 4 sin perder historial, plantillas ni tareas; la base de datos no cambia de versión).
2. En **Más** hay tres entradas nuevas: **Escritura en lote**, **Códigos QR** e **Importar y exportar**.
3. Dependencia nueva: `com.google.zxing:core` (códigos QR, solo cálculo local). Permisos nuevos: **ninguno** (sin CÁMARA y sin almacenamiento).

## Material necesario

- Tres o más etiquetas **NTAG213/215** en blanco o regrabables (para el lote) y una etiqueta con datos (para el aviso de reemplazo).
- Una plantilla guardada con `{n}` en un enlace, por ejemplo `https://x.co/t/{n}` (créala en **Escribir**, añade el registro y pulsa **Guardar como plantilla**).
- Otro teléfono o un PC para mostrar/escanear un código QR de Wi-Fi.
- Si puedes: un teléfono con TalkBack y un launcher que permita fijar accesos directos.

## Reglas que NUNCA deben romperse (marca cada una)

- [ ] La app no pide **CÁMARA** ni permisos de almacenamiento (Ajustes > Apps > Permisos: no deben aparecer).
- [ ] La exportación JSON **no contiene contraseñas de Wi-Fi** (ábrela en un editor de texto y busca la contraseña).
- [ ] Una importación defectuosa no importa **nada** (todo o nada).
- [ ] Nada se escribe en una etiqueta sin que sostengas una etiqueta contra el teléfono; los accesos directos y el widget solo abren la pantalla.
- [ ] Todo el texto está en español e inglés (cambia el idioma del teléfono y recorre las pantallas nuevas).

## 1. Historial: búsqueda y filtros (HT-03)

- [ ] Escribe en el campo de búsqueda: la lista se actualiza sola, **sin pulsar Enter** (con una pequeña pausa).
- [ ] Busca por parte de un resumen, por UID y por tipo de etiqueta.
- [ ] Los filtros de tipo (Lectura, Escritura, Lote...), resultado (Correcto / Fallido) y fechas (Hoy, Semana, Mes) se combinan: con 10 entradas y 3 escrituras fallidas, filtrar "Escritura + Fallido" muestra exactamente 3.
- [ ] Sin coincidencias aparece el estado vacío con **Borrar filtros**, que restablece la lista completa.

## 2. Importar y exportar (HT-04)

- [ ] **Más > Importar y exportar > Exportar copia (JSON)** abre el selector del sistema; guarda el archivo.
- [ ] Borra el historial y las plantillas (o usa una instalación limpia), pulsa **Importar** y elige el archivo: se restauran todos los elementos (historial, plantillas y tareas) y el mensaje dice cuántos.
- [ ] Importar dos veces el mismo archivo no duplica nada (los ids repetidos se omiten).
- [ ] Edita el JSON a mano: rompe la entrada nº 7 (por ejemplo un valor de `kind` inexistente) e impórtalo: **no se importa nada** y el error nombra la entrada 7.
- [ ] Cambia `"schema": 1` por `"schema": 99`: se rechaza con un mensaje claro.
- [ ] **Exportar historial (CSV)**: ábrelo en una hoja de cálculo; comas, comillas y saltos de línea en los resúmenes salen bien (UTF-8) y ninguna celda empieza con una fórmula activa.
- [ ] Mientras trabaja se ve el progreso y los botones quedan desactivados.

## 3. Escritura en lote (PR-01)

- [ ] **Más > Escritura en lote**: elige la plantilla con `{n}`, primer número 7, salto 1, dígitos 3, 3 etiquetas, tipo de etiqueta correcto.
- [ ] La vista previa dice "Números 007 al 009" y muestra "Etiqueta más larga: X de Y bytes". Elige NTAG213 con una plantilla demasiado grande: el lote **no puede empezar** y lo explica.
- [ ] Inicia el lote: "La siguiente etiqueta recibe 007". Acerca la primera etiqueta: háptica, "Etiqueta 007 escrita y verificada" y ahora "008".
- [ ] Acerca la **misma** etiqueta otra vez: pide confirmación antes de reemplazar.
- [ ] En la etiqueta 009, **retírala a mitad de la escritura**: el contador **no avanza**, aparece el fallo y la siguiente etiqueta vuelve a recibir **009**.
- [ ] **Saltar número** pasa al siguiente sin escribir; **Terminar** muestra el resumen "N correctas / N fallidas / N pendientes".
- [ ] Mata la app a mitad del lote y vuelve a abrirla: el primer número propuesto es el siguiente por escribir (el contador sobrevive).
- [ ] Cada intento (bueno o malo) aparece en el Historial con el tipo Lote.
- [ ] Al salir de la pantalla deja de esperar etiquetas; Atrás durante el lote lo termina y muestra el resumen.

## 4. Códigos QR (PR-02)

- [ ] En **Escribir**, con un solo registro válido (por ejemplo Wi-Fi), el paso de revisión muestra **Mostrar como código QR**; con dos registros o uno inválido el botón no aparece.
- [ ] El QR se ve nítido (negro sobre blanco también en tema oscuro) y otro dispositivo lo escanea correctamente (Wi-Fi: se une a la red).
- [ ] El aviso de privacidad del QR es visible. TalkBack lee la descripción del código.
- [ ] **Más > Códigos QR > Elegir una imagen**: abre el selector de fotos del sistema (sin permiso) y elige una captura con un QR de Wi-Fi (`WIFI:T:WPA;S:Casa;P:clave1234;;`): aparece "Red Wi-Fi: Casa" (sin mostrar la clave) y **Usar en Escribir** abre el asistente en el paso de revisión con la red cargada y editable.
- [ ] Una imagen sin QR dice que no se encontró; un archivo que no es imagen dice que no se pudo abrir.
- [ ] Pega el texto `https://example.com`, `tel:+34600111222`, `mailto:a@b.com?subject=Hola`, una vCard y texto libre: cada uno se reconoce como URL, Teléfono, Correo, Contacto y Texto.
- [ ] Una red WEP o un QR de Wi-Fi sin nombre se conservan como **Texto** (no se pierde nada).
- [ ] Ida y vuelta: crea un Wi-Fi con `;` `:` o `\` en el nombre o la clave, muéstralo como QR, léelo de una captura y compruébalo.

## 5. Accesos directos (PR-03)

- [ ] Mantén pulsado el icono de la app en el launcher: aparecen **Leer** y **Escribir**; abren la pestaña correspondiente.
- [ ] **Más > Plantillas > menú de una plantilla > Fijar en la pantalla de inicio**: el launcher pide confirmar y crea el acceso "Escribir: nombre".
- [ ] Al tocar ese acceso se abre Escribir con la plantilla cargada y la hoja **"acerca una etiqueta para escribir"** ya esperando; acercar una etiqueta escribe y verifica.
- [ ] Si el NFC está apagado o la plantilla ya no es válida, el acceso abre la pantalla **sin** esperar etiqueta.
- [ ] Con un launcher que no admite fijar, la app lo dice en lugar de fallar.

## 6. Widget (PR-03)

- [ ] Añade el widget "Tag Forge" a la pantalla de inicio: muestra solo dos botones, **Leer** y **Escribir** (nunca contenido de etiquetas).
- [ ] **Leer** abre la pestaña Leer; **Escribir** abre la pestaña Escribir. Ninguno escribe nada por sí solo.
- [ ] Texto legible en tema claro y oscuro; cada botón mide al menos 48 dp de alto.

## 7. Accesibilidad (PR-04)

- [ ] TalkBack activo: al leer una etiqueta anuncia "Etiqueta leída: NTAG215, 2 registros" (con 1 registro, "1 registro").
- [ ] TalkBack recorre Historial (filtros), Importar/Exportar, Lote, QR, Plantillas y Más: todos los botones, iconos y campos tienen nombre; los iconos decorativos se omiten.
- [ ] Las hojas de escritura anuncian los cambios de estado (esperando, escribiendo, éxito o fallo) sin tener que tocarlas.
- [ ] Tamaño de fuente al **200 %** (Ajustes > Accesibilidad > Tamaño de fuente): el asistente de Escribir, el lote, el QR y Importar/Exportar se pueden desplazar y ningún botón queda recortado.
- [ ] El estado nunca se indica solo con color: el medidor de bytes, las insignias de riesgo, el resultado de importar/exportar y los estados del lote siempre llevan texto o icono.
- [ ] Filas del volcado de memoria y valores ocultos (contraseña Wi-Fi) se pueden tocar cómodamente (objetivo táctil de 48 dp).
- [ ] Prueba instrumentada opcional: `gradlew.bat :app:connectedDebugAndroidTest --tests '*FontScaleTest'` (y `DaoTest`/`ExchangeStoreTest`, que siguen sin ejecutarse en dispositivo).

## 8. Regresión rápida de fases anteriores

- [ ] Leer una NTAG, escribir un texto, ejecutar una tarea con confirmación y abrir el Centro de permisos funcionan igual que en la Fase 4.
- [ ] La barra inferior sigue siendo Leer · Escribir · Tareas · Herramientas · Más.

## Notas conocidas

- El filtro de fechas usa ventanas móviles (Hoy / Semana / Mes), sin selector de fechas personalizado.
- La lectura de QR es desde imagen o texto pegado; no hay lector con cámara a propósito (sin permiso de CÁMARA).
- Las pruebas instrumentadas (Room, FontScale) están compiladas pero se ejecutan solo en dispositivo.
