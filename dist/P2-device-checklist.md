# Tag Forge - Fase 2 (Escritura + historial + plantillas): lista de verificación en dispositivo

APK: `dist/tag-forge-p2-debug.apk` (versión 0.2.0-debug, paquete `com.tagforge.app.debug`).

## Instalación

1. Instala con `adb install -r dist/tag-forge-p2-debug.apk` (o copia el APK al teléfono). Se actualiza sobre la Fase 1 sin perder los ajustes.
2. Abre "Tag Forge". La barra inferior ahora tiene **Leer · Escribir · Más**.

## Material necesario

- Etiquetas NTAG213, NTAG215 y NTAG216, de preferencia varias de cada tipo (vacías y con datos).
- Una etiqueta ya bloqueada (solo lectura), si tienes. Opcional.
- Una etiqueta NTAG que puedas retirar a mitad de escritura (todas sirven; es una prueba de pulso).

## 1. Asistente de escritura (guiado)

- [ ] Escribir muestra "¿Qué quieres escribir?" con los tipos agrupados: Básico, Comunicación, Lugares y personas, Conectividad, Avanzado.
- [ ] Al elegir un tipo se abre su formulario y la **vista previa en vivo** aparece en cuanto los datos son válidos (tarjeta decodificada igual que en Leer). "Mostrar bytes" despliega el hex NDEF.
- [ ] El medidor de espacio cambia mientras escribes (por ejemplo `16 / 137 bytes` para `https://example.com` en NTAG213) e indica "Cabe".
- [ ] "Añadir al mensaje" lleva a la **Revisión**; ahí se puede editar, subir, bajar y quitar cada registro. Al quitar aparece "Deshacer".
- [ ] El botón Atrás recorre los pasos del asistente en lugar de salir de la pantalla.

## 2. Validación en línea (español e inglés)

- [ ] Correo `a@b` -> error junto al campo al salir de él: "Escribe un correo como nombre@example.com".
- [ ] Teléfono `12` -> error con el rango de dígitos. `+57 (300) 123-4567` se acepta.
- [ ] URL `example.com` (sin https://) -> error. `https://example.com` se acepta.
- [ ] WiFi: SSID de más de 32 caracteres/bytes -> error; contraseña `123` con WPA2 -> error "8 a 63 caracteres"; red **Abierta** oculta el campo de contraseña.
- [ ] Ubicación: latitud `91` o longitud `-181` -> error de rango; `abc` -> "Escribe un número".
- [ ] Bluetooth con dirección incompleta, paquete `camera`, tipo MIME `plain` y hex impar -> errores específicos.
- [ ] Con un campo vacío obligatorio, "Añadir al mensaje" no avanza y marca todos los errores.
- [ ] Cambia el idioma del teléfono a inglés y repite dos o tres errores: los mensajes salen en inglés.

## 3. Escritura y verificación (cada tipo)

Para cada tipo escribe una etiqueta y léela después en la pestaña **Leer**:

- [ ] Texto, Enlace, Correo (con asunto), Teléfono, SMS, Ubicación, Contacto (vCard), WiFi, Bluetooth, App (AAR), MIME personalizado, Tipo externo.
- [ ] La hoja pasa por "Acerca la etiqueta" -> "Escribiendo..." -> "Verificando..." -> "Escrita y verificada", con vibración de detección y de éxito.
- [ ] Repite con NTAG213, NTAG215 y NTAG216: el contenido leído es idéntico al escrito.
- [ ] Un mensaje de varios registros (Texto + Enlace + app) se lee en el mismo orden.
- [ ] WiFi: al acercar la etiqueta escrita a otro teléfono Android, ofrece conectarse a la red (si tienes otro teléfono).
- [ ] Contacto: otro teléfono ofrece guardar el contacto.
- [ ] Interruptor "Abrir Tag Forge al acercar": añade 34 bytes al medidor y el registro AAR queda al final.

## 4. Tamaño y destino

- [ ] Selector de etiqueta de destino (NTAG213/215/216): al cambiar, el medidor usa 137 / 492 / 868 bytes.
- [ ] Un mensaje de 138 bytes en NTAG213: el medidor se pone en rojo con "Sobran 1 bytes para NTAG213. Usa NTAG215...", el botón **Escribir** queda desactivado y debajo dice por qué.
- [ ] Un mensaje de exactamente 137 bytes SÍ se escribe y verifica en una NTAG213 (la etiqueta queda sin terminador, es válido).
- [ ] Acerca una NTAG213 teniendo seleccionado NTAG215 con un mensaje de 200 bytes: error "El mensaje necesita 207 bytes pero la etiqueta tiene 137", sin escribir nada, **sin botón de reintento**, y aparece la opción "Última etiqueta (137 B)" seleccionada.
- [ ] Una etiqueta bloqueada: "Esta etiqueta está bloqueada", **sin reintento**, no se escribe nada.

## 5. Sobrescritura (confirmación)

- [ ] Etiqueta con contenido: aparece "¿Reemplazar el contenido existente? Esta etiqueta ya contiene: ...". **Cancelar** (o tocar fuera / Atrás) deja la etiqueta exactamente igual (léela para comprobarlo).
- [ ] Confirmar "Reemplazar" escribe y verifica. Puedes tardarte en decidir: la espera aguanta hasta 60 s con la etiqueta pegada al teléfono.
- [ ] Etiqueta vacía: no pregunta nada.

## 6. Etiqueta retirada (pulso)

- [ ] Retira la etiqueta a mitad de la escritura: "La etiqueta se alejó", con **Intentar de nuevo**; nunca dice "Escrita y verificada". La app no se cierra.
- [ ] Lee esa etiqueta en Leer: o está vacía, o con el contenido anterior, o completa; **nunca** un mensaje truncado o corrupto.
- [ ] Más > Historial muestra esa escritura como "La etiqueta se alejó".
- [ ] "Intentar de nuevo" + tocar la etiqueta otra vez termina bien.
- [ ] No se puede cerrar la hoja deslizando mientras "Escribiendo..." / "Verificando..." está en curso.
- [ ] Informe importante: algunos teléfonos devuelven vacío el acuse de escritura; la app lo tolera porque verifica leyendo. Si alguna escritura falla con "Los datos de la etiqueta no son válidos", anota el modelo.

## 7. "Escribir otra" y plantillas

- [ ] Tras un éxito: **Listo**, **Escribir otra etiqueta** (repite el mismo mensaje en la siguiente etiqueta) y **Guardar como plantilla**.
- [ ] Guardar plantilla "WiFi casa": aparece el aviso "Guardada como plantilla". Guardar otra con el mismo nombre en mayúsculas la nombra "... (2)".
- [ ] Más > Plantillas: lista alfabética con número y tipos de registros. Tocar una abre el asistente en la Revisión con los registros idénticos.
- [ ] Menú de la plantilla: Renombrar (con las mismas reglas de nombres) y Eliminar (con **Deshacer**).
- [ ] Sin plantillas: estado vacío con botón "Ir a Escribir".

## 8. Historial

- [ ] Más > Historial: cada lectura y escritura aparece (más reciente primero) con icono y texto de resultado (no solo color), tipo de etiqueta y fecha.
- [ ] Mientras carga se ven filas esqueleto del mismo alto que las reales (sin saltos).
- [ ] Tocar una entrada abre "Etiqueta guardada" con los registros decodificados; las contraseñas WiFi **nunca** aparecen (ni en el resumen ni en el detalle).
- [ ] Eliminar una entrada la quita con **Deshacer**.
- [ ] Borrar historial pide confirmación. **Cancelar** deja todo igual; confirmar vacía la lista y las **plantillas siguen intactas**.
- [ ] Ajustes > "Guardar historial" desactivado: nuevas lecturas/escrituras ya no se registran. Activado: vuelven a registrarse.
- [ ] Sin historial: estado vacío con botón "Leer una etiqueta".

## 9. Sin NFC / NFC apagado

- [ ] NFC apagado en Escribir: banner "NFC está desactivado", el asistente se puede completar y guardar como plantilla, pero **Escribir** está desactivado y explica "Activa el NFC para escribir".
- [ ] Dispositivo sin NFC: historial y plantillas siguen siendo utilizables.

## 10. Idioma, accesibilidad y visual

- [ ] Español neutro sin textos en inglés mezclados en Escribir, Historial, Plantillas, errores y diálogos; repite en inglés.
- [ ] Modo claro y oscuro legibles, incluido el medidor (verde/ámbar/rojo + texto "Cabe / Casi llena / Demasiado grande").
- [ ] TalkBack: el medidor anuncia "X de Y bytes usados, Cabe"; el estado de la hoja de escritura se anuncia.
- [ ] Fuente grande (1.3 o más) y "Quitar animaciones": la hoja usa icono estático y no se corta contenido.

## Qué reportar

Para cada casilla que falle: modelo de teléfono, versión de Android, tipo de etiqueta y qué pasó. Lo más valioso: la sección 6 (etiqueta retirada) con NTAG213/215/216, el mensaje de exactamente 137 bytes y cualquier teléfono donde la escritura falle con "datos no válidos".
