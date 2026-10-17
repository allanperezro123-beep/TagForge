# Tag Forge - Fase 1 (Fundación + Lectura): lista de verificación en dispositivo

APK: `dist/tag-forge-p1-debug.apk` (versión 0.1.0-debug, paquete `com.tagforge.app.debug`).

## Instalación

1. Copia el APK al teléfono (o usa `adb install -r dist/tag-forge-p1-debug.apk`).
2. Permite la instalación desde orígenes desconocidos si Android lo pide.
3. Abre "Tag Forge". Debe aparecer la pantalla "Leer etiqueta" (si tu teléfono está en inglés, "Read tag").

## Material necesario

- Etiquetas NTAG213, NTAG215 y NTAG216 (idealmente una con datos y otra vacía de cada tipo).
- Una etiqueta protegida con contraseña, si tienes (NTAG con AUTH0 y PROT activados). Opcional.
- Una tarjeta que no sea NTAG (por ejemplo MIFARE Classic o una tarjeta bancaria) para el caso "no soportada". Opcional.

## 1. Lectura básica

- [ ] Con NFC activado, la pantalla muestra el radar animado y el texto "Listo para escanear".
- [ ] Al acercar una NTAG213 con datos hay una vibración corta de detección y otra de éxito.
- [ ] Pestaña **Registros**: aparecen los registros (Texto, URI, MIME, Externo) con su contenido decodificado.
- [ ] "Mostrar hex sin procesar" en cada registro despliega TNF, tipo y carga útil en hexadecimal.
- [ ] Pestaña **Info**: UID con formato `04:xx:xx:...`, fabricante (NXP para UID que empieza en 04), tipo (NTAG213/215/216), tecnologías, ATQA y SAK, memoria, escribible, NDEF.
- [ ] Pestaña **Hex**: una fila por página. En una NTAG213 deben verse **45 filas** (páginas 0 a 44). En NTAG215 135 filas y en NTAG216 231 filas.
- [ ] Acercar otra etiqueta sin salir de la pantalla la lee de inmediato (modo repetición).
- [ ] Una etiqueta vacía muestra "Etiqueta vacía" y la pestaña Hex sigue funcionando.
- [ ] Repite con una NTAG215 y una NTAG216: el tipo y la capacidad cambian correctamente.

## 2. Errores y etiqueta perdida

- [ ] Retira la etiqueta a mitad de la lectura: aparece "La etiqueta se alejó. Mantenla firme contra el teléfono" con botón **Intentar de nuevo**. La app NO se cierra.
- [ ] Pulsa "Intentar de nuevo": vuelve a "Listo para escanear" y puede leer otra vez.
- [ ] Con una etiqueta protegida: se muestra "Protegida con contraseña" con el UID y el tipo, y un botón **Desbloquear** (en esta fase solo avisa que llegará en una versión posterior).
- [ ] Con una tarjeta MIFARE Classic o similar: mensaje explicativo de que no se puede leer, sin errores ni cierres.
- [ ] Nota para el informe: en una etiqueta protegida, anota si la pestaña Hex muestra el rango protegido o si aparece "etiqueta perdida" (riesgo conocido: Android puede reportar un NAK como pérdida de etiqueta).

## 3. Estado de NFC

- [ ] Desactiva NFC (ajustes rápidos): la pantalla pasa a "NFC está desactivado" con el botón **Abrir ajustes de NFC**.
- [ ] Pulsa el botón: se abre la configuración de NFC. Actívalo y vuelve a la app: pasa automáticamente a "Listo para escanear" sin reiniciar y se puede leer una etiqueta.
- [ ] Si tienes un dispositivo sin NFC: la app abre y muestra "NFC no está disponible" (solo explicación, sin botón de ajustes) y no se cierra.

## 4. Navegación y ajustes

- [ ] La barra inferior tiene **Leer** y **Más**. Cambiar de pestaña y volver conserva el estado y reanuda la escucha.
- [ ] Mientras estás en "Más" o "Ajustes", acercar una etiqueta NO la lee (la pantalla oculta no escucha).
- [ ] Más > Ajustes y Más > Acerca de se abren; la barra inferior se oculta y la flecha atrás funciona (también el gesto atrás).
- [ ] Ajustes > Tema: Claro, Oscuro y Predeterminado del sistema cambian el aspecto de inmediato.
- [ ] Cierra la app por completo y ábrela de nuevo: el tema elegido se conserva.
- [ ] "Color dinámico" aparece solo en Android 12 o superior y cambia la paleta.
- [ ] "Respuesta háptica" desactivada: no hay vibraciones al leer. Activada: vuelven.
- [ ] "Modo avanzado" está **desactivado** por defecto.
- [ ] Acerca de muestra "Versión 0.1.0-debug".

## 5. Idioma

- [ ] Con el teléfono en español, todos los textos aparecen en español neutro, sin textos en inglés mezclados.
- [ ] Cambia el idioma de la app a inglés (Ajustes de Android > Apps > Tag Forge > Idioma) y comprueba que todo se traduce.
- [ ] Revisa en ambos idiomas: pantalla de lectura, errores, pestañas, ajustes y Acerca de.

## 6. Accesibilidad y visual

- [ ] Modo claro y oscuro: texto legible, sin elementos ilegibles.
- [ ] Con "Quitar animaciones" activado en Android, el radar se muestra estático.
- [ ] TalkBack: los estados de escaneo ("Listo para escanear", "Leyendo etiqueta...", errores) se anuncian.
- [ ] Fuente grande (escala 1.3 o más): no se corta contenido.

## Qué reportar

Para cada casilla que falle indica: modelo de teléfono, versión de Android, tipo de etiqueta y qué pasó. Lo más valioso: la lectura de la NTAG protegida (sección 2) y el comportamiento de la pestaña Hex en NTAG215 y NTAG216.
