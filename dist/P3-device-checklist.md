# Tag Forge - Fase 3 (Herramientas seguras): lista de verificación en dispositivo

APK: `dist/tag-forge-p3-debug.apk` (versión 0.3.0-debug, paquete `com.tagforge.app.debug`).

## Instalación

1. Instala con `adb install -r dist/tag-forge-p3-debug.apk` (se actualiza sobre la Fase 2 sin perder historial ni plantillas).
2. La barra inferior ahora tiene **Leer · Escribir · Herramientas · Más**.

## Material necesario

- Etiquetas NTAG213, NTAG215 y NTAG216, con y sin datos. Idealmente al menos una de cada una **con un mensaje escrito** y otra **en blanco de fábrica**.
- Una etiqueta ya bloqueada (solo lectura), si tienes. Opcional pero muy útil.
- Una tarjeta ISO-DEP (tarjeta bancaria sin contacto, DNI electrónico, etc.) para ver la información de tecnologías. **No se envía ningún comando de escritura a estas tarjetas.**
- Una tarjeta MIFARE Classic (tarjeta de transporte antigua), si tienes. Opcional.
- Un teléfono con `adb` o una app de correo/Drive para comprobar el archivo compartido.

> Importante: Borrar y Formatear **destruyen datos de la etiqueta**. Usa etiquetas de prueba. Ninguna de las dos herramientas toca las páginas 0-3, los bytes de bloqueo, el contenedor de capacidades (CC), la contraseña ni la configuración.

## 1. Pestaña Herramientas (navegación)

- [ ] La pestaña **Herramientas** (icono de llave) aparece entre Escribir y Más.
- [ ] Se ven dos secciones: "Modificar una etiqueta" (Borrar, Formatear; con el icono en rojo) e "Inspeccionar y copiar" (Volcado de memoria, Clonar, Tecnologías).
- [ ] Con NFC apagado: aparece el aviso "NFC está desactivado" con el botón para abrir los ajustes; **Borrar y Formatear quedan desactivados**; Volcado/Clonar/Tecnologías siguen abriéndose. Al volver con NFC activo, los botones se activan sin reiniciar.
- [ ] En un teléfono sin NFC: el aviso dice que no hay NFC y no hay botón de ajustes.

## 2. Borrar etiqueta (confirmación -> espera -> resultado)

- [ ] Tocar **Borrar etiqueta** abre un diálogo "¿Borrar esta etiqueta?" con el botón **Borrar** en rojo. **Aún no se activa NFC**: acercar una etiqueta en este momento no hace nada (compruébalo leyéndola después).
- [ ] **Cancelar**, tocar fuera del diálogo y el botón Atrás lo cierran y **no tocan la etiqueta**.
- [ ] **Borrar** abre la hoja "Acerca la etiqueta que quieres borrar" con Cancelar. Cancelar (o deslizar hacia abajo) la cierra y acercar una etiqueta después **no la modifica**.
- [ ] Acerca una **NTAG213 con un mensaje**: "Borrando..." -> "Verificando..." -> "Etiqueta borrada", con vibración de detección y de éxito. En Leer ahora aparece vacía.
- [ ] Con la NTAG213 borrada, en **Herramientas > Volcado de memoria** las páginas 04 y 05 son `01 03 A0 0C` y `34 03 00 FE` (el TLV de control de bloqueo de fábrica sigue ahí) y las páginas 06 a 27 son `00 00 00 00`.
- [ ] En NTAG215 y NTAG216 la página 04 queda `03 00 FE 00` y el resto de la memoria de usuario en ceros.
- [ ] Las páginas 0-3 (UID), la página 03 (CC `E1 10 xx 00`) y las páginas de bloqueo/configuración son idénticas antes y después (compara dos volcados).
- [ ] Borrar una etiqueta que **ya estaba vacía** dice "Ya estaba vacía" y no escribe nada.
- [ ] Una etiqueta **bloqueada**: "Esta etiqueta está bloqueada" y una nota de que el bloqueo no se puede deshacer; **sin botón de reintento**.
- [ ] El historial (Más > Historial) registra una entrada **Borrado** por intento (también las fallidas), **sin** el contenido borrado.

### Etiqueta retirada a mitad (prueba de pulso)

- [ ] Inicia un borrado y **retira la etiqueta** en cuanto detecte vibración. Resultado: fallo con "Vuelve a acercar la etiqueta... nunca queda a medio escribir" y botón **Intentar de nuevo**. En el historial: fallo por etiqueta perdida.
- [ ] Lee esa etiqueta: o conserva el mensaje original o está vacía; **nunca** aparece un mensaje cortado ni un error de formato raro.
- [ ] Pulsa **Intentar de nuevo** y acerca la etiqueta: termina el borrado sin que te pida confirmar otra vez.
- [ ] La hoja **no se puede cerrar** mientras "Borrando..." está en marcha (deslizar y Atrás no hacen nada); sí se puede cerrar al terminar o en la espera.

## 3. Formatear etiqueta

- [ ] El diálogo "¿Formatear esta etiqueta?" explica que se vacía **toda** la memoria de usuario y que no se puede deshacer. Cancelar/Atrás/tocar fuera no tocan la etiqueta.
- [ ] Formatear una etiqueta con datos: "Etiqueta formateada". En el volcado, solo `01 03 A0 0C | 34 03 00 FE` (NTAG213) o `03 00 FE 00` (215/216) y ceros hasta el final de la memoria de usuario.
- [ ] Formatear una etiqueta ya limpia: "Ya estaba limpia", sin escrituras.
- [ ] Una etiqueta cuyo contenido aparece como ilegible (por ejemplo, escrita con otra app de forma incorrecta): Formatear la deja leyéndose como NDEF vacío.
- [ ] Una etiqueta virgen sin formato NDEF que Android sabe formatear: se formatea mediante el sistema y termina en "Etiqueta formateada".
- [ ] Una etiqueta bloqueada se rechaza **antes** de escribir (sin reintento).
- [ ] Retirar la etiqueta durante el formateo de una NTAG216 (hasta 222 páginas): fallo con reintento; la etiqueta queda con el mensaje anterior o vacía, nunca a medias.

## 4. Volcado de memoria anotado

- [ ] **Volcado de memoria** abre una pantalla con "Acerca una etiqueta para leer su memoria". Al acercar una NTAG213 se ven **45 filas** (páginas 00 a 2C).
- [ ] La leyenda muestra los roles con colores y **nombre**: UID / BCC, Bloqueo estático, Contenedor de capacidades (CC), Memoria de usuario, Bloqueo dinámico, CFG0 (AUTH0=byte3), CFG1 (ACCESS), PWD, PACK. Cada fila indica su rol también con texto (no solo color).
- [ ] NTAG216: la página 226 (E2) es "Bloqueo dinámico", 227 "CFG0 (AUTH0=byte3)", 228 "CFG1 (ACCESS)", 229 "PWD", 230 "PACK". Revisa también NTAG213 (28h-2Ch) y NTAG215 (82h-86h).
- [ ] **PWD y PACK** se ven como `•• •• •• ••` con la nota "Oculta por el chip" y nunca muestran datos.
- [ ] Tocar una fila abre una hoja que explica ese rol (por ejemplo, CFG0 explica que el byte 3 es AUTH0 y que FF significa sin protección).
- [ ] El interruptor **4 bytes por fila / 16 bytes por fila** cambia el diseño: a 16 bytes cada fila agrupa 4 páginas en dos líneas legibles, sin texto cortado en un teléfono estrecho. La hoja de una fila agrupada explica cada página.
- [ ] Con TalkBack: cada fila se lee como "Página N, rol, bytes ..." (o "oculta por el chip").
- [ ] Una etiqueta que **no** es NTAG (Ultralight u otra): se muestran las páginas sin anotaciones y el aviso correspondiente.
- [ ] Una NTAG **protegida con contraseña** (AUTH0 activo), si tienes: el volcado muestra las páginas legibles y "Las páginas X a Y están protegidas y no se pueden leer", sin error.
- [ ] Una tarjeta **MIFARE Classic** en un teléfono sin controlador NXP: explicación clara "Parece una tarjeta MIFARE Classic... Tag Forge no lo intentará", sin cierre de la app ni error genérico.

### Copiar y compartir (TT-06)

- [ ] El icono **Copiar** pone el volcado en el portapapeles (en Android 12 o inferior aparece "Volcado copiado"). Pégalo en una nota: hay 3-4 líneas de comentario `#` (UID, chip) y una línea por página, `PP: HH HH HH HH |ascii|`.
- [ ] **Compartir > Compartir como texto** abre la hoja de compartir del sistema con un archivo `tagforge-dump-NTAG213-XXXX-AAAAMMDD-HHMMSS.txt`. Envíalo a ti mismo y ábrelo: el contenido coincide con la pantalla.
- [ ] **Compartir como archivo .bin** envía un archivo binario de 180 bytes para NTAG213 (45 páginas x 4), 540 para NTAG215, 924 para NTAG216.
- [ ] El nombre del archivo solo lleva los **últimos 2 bytes** del UID, no el UID completo.
- [ ] El archivo se comparte con permiso temporal: no aparece en la galería ni en el almacenamiento compartido (`adb shell ls /sdcard` no lo muestra).

## 5. Clonar

- [ ] **Clonar a otra etiqueta** muestra "Paso 1 de 2 - Lee la etiqueta de origen" con el interruptor "Copiar también las páginas de usuario en bruto" (apagado) y su explicación.
- [ ] **Escanear etiqueta de origen** abre la hoja de espera. Acerca una NTAG213 con un mensaje: pasa a **Paso 2 de 2** con un resumen (chip, bytes, contenido, UID) y la nota "Las etiquetas no tendrán el mismo UID". El origen **no se escribe** (compruébalo con el volcado).
- [ ] **Escanear etiqueta de destino** con una **NTAG215 en blanco**: "Escribiendo la copia..." -> "Verificando..." -> "Copia verificada" con "las etiquetas tienen UID distintos". Lee el destino en Leer: el mensaje es idéntico.
- [ ] El botón **Escribir en otra etiqueta** reutiliza el mismo origen para una tercera etiqueta.
- [ ] Acerca la **misma etiqueta de origen** como destino: "Esta es la etiqueta de origen. Acerca otra etiqueta distinta" con reintento, sin escribir nada.
- [ ] Mensaje grande (unos 300 bytes, necesita NTAG215/216) hacia una **NTAG213**: "El mensaje necesita ... bytes pero la etiqueta admite 137", **antes** de escribir nada, con la sugerencia de usar una etiqueta más grande.
- [ ] Destino **con contenido**: aparece "¿Reemplazar el contenido de esta etiqueta? Esta etiqueta ya contiene: ...". **Cancelar** / tocar fuera / Atrás / cerrar la hoja = la etiqueta queda igual. **Reemplazar** copia y verifica. Puedes tardar hasta 60 s en decidir con la etiqueta pegada.
- [ ] Destino **bloqueado**: "Esta etiqueta está bloqueada", sin escribir.
- [ ] Retira el destino a mitad de la copia: fallo con reintento; el destino queda con su contenido anterior, vacío o con la copia completa (lo compruebas con Leer), y el historial registra "Clonado" fallido por etiqueta perdida.
- [ ] **Atrás** en el paso 2 vuelve al paso 1 y descarta el origen; acercar una etiqueta después no hace nada. **Empezar de nuevo** hace lo mismo.
- [ ] Con el interruptor de páginas en bruto: entre dos NTAG del **mismo modelo** (p. ej. 213 -> 213) las páginas de usuario del destino quedan iguales a las del origen (compara volcados: páginas 04..27 idénticas, página 0 distinta). Entre modelos distintos (213 -> 215) muestra "Copiar páginas en bruto necesita dos etiquetas del mismo tipo de chip" y no escribe nada.
- [ ] El historial registra una entrada **Clonado** por cada intento de destino.

## 6. Tecnologías de la etiqueta

- [ ] **Tecnologías de la etiqueta** muestra "Acerca una etiqueta al teléfono". Con una NTAG: sección NfcA con ATQA y SAK, y MifareUltralight con tipo y páginas si el sistema lo informa.
- [ ] Con una **tarjeta ISO-DEP**: sección IsoDep con **Bytes históricos en hexadecimal**, respuesta de capa superior, APDU de longitud extendida (Sí/No) y tiempo de espera. Ningún dato se escribe en la tarjeta.
- [ ] Con una etiqueta NfcV (ISO 15693) o FeliCa, si tienes: se ven DSFID/indicadores o código de sistema/bytes del fabricante.
- [ ] Con MIFARE Classic en un teléfono con controlador NXP: tipo, sectores, bloques y tamaño.
- [ ] En **Leer**, la ficha de detalle tiene una pestaña **Técnicas** (solo si el sistema reportó datos por tecnología). En un registro del historial no aparece.
- [ ] **Escanear otra etiqueta** reinicia la espera.

## 7. Coherencia general

- [ ] Cambia el idioma del teléfono a inglés: toda la pestaña Herramientas, los diálogos, las hojas y los nombres de regiones salen en inglés (sin textos en español mezclados), y viceversa.
- [ ] Modo oscuro/claro: las bandas de color del volcado, la leyenda y el botón rojo de los diálogos tienen contraste suficiente.
- [ ] Al pasar de Herramientas > Volcado a otra pestaña y volver, no queda ninguna operación armada por error (acercar una etiqueta en Leer solo la lee).
- [ ] Cambiar rápidamente de Herramientas a Volcado y a Leer no hace que una pantalla "robe" la etiqueta de otra.
- [ ] Giro de pantalla durante la espera: la hoja y el diálogo se conservan.
- [ ] Ninguna operación de esta fase muestra un cierre inesperado de la app.
