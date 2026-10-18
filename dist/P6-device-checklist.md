# Fase 6 (1.0.0, versionCode 600): checklist en dispositivo

APK de depuración: `dist/tag-forge-p6-debug.apk` (paquete `com.tagforge.app.debug`).
APK final: lo firma el responsable con su llave (ver `dist/RELEASE.md`) y esta lista se repite sobre el APK firmado.

## REGLAS DE SEGURIDAD PARA ESTAS PRUEBAS (léelas primero)

- Usa SOLO etiquetas de sacrificio. Todo lo de las secciones 2 a 5 puede dejar una etiqueta inservible para siempre.
- Una etiqueta de sacrificio por prueba permanente (bloqueo, CFGLCK, límite de intentos). No las reutilices.
- Anota la contraseña de cada prueba ANTES de pulsar Confirmar. El chip nunca devuelve la contraseña.
- Mantén la etiqueta quieta sobre la antena (suele estar cerca de la cámara) hasta que la hoja diga "Listo".
- Si algo sale raro, no sigas: vuelve a escanear la etiqueta en Leer y anota el estado real.

## 1. Prerrequisitos

- [ ] Una NTAG215 nueva (sacrificio A), una NTAG213 nueva (sacrificio B), una NTAG216 nueva si la tienes (sacrificio C).
- [ ] Ajustes: modo Avanzado APAGADO al empezar (debe estar apagado por defecto).
- [ ] Herramientas muestra la sección "Proteger y bloquear" con la entrada "Seguridad". NO hay entrada de consola.

## 2. Contraseña en la NTAG215 de sacrificio A (NS-02, NS-03, NS-04)

- [ ] Seguridad, "Poner o cambiar contraseña": "Generar" rellena contraseña y PACK. Copiar y Compartir funcionan.
- [ ] El botón "Leer la etiqueta" falla con el mensaje de "guarda la contraseña" mientras no marques "Guardé esta contraseña".
- [ ] Primer toque: solo lee. Aparece el cuadro "Confirma: esto no se puede deshacer" con los 4 comandos exactos
      (`A2 85 ...`, `A2 86 ...`, `A2 84 80 00 00 00`, `A2 83 04 00 00 04`), el UID y el aviso de la contraseña.
- [ ] "Confirmar" está deshabilitado hasta escribir los últimos 4 caracteres del UID Y marcar "Entiendo". Un texto distinto no lo habilita.
- [ ] Cancelar o Atrás en ese cuadro: acercar la etiqueta después NO escribe nada (compruébalo en Leer).
- [ ] Confirmar y acercar OTRA etiqueta: mensaje "no es la etiqueta que confirmaste", sin escribir nada en la otra.
- [ ] Confirmar y acercar la misma etiqueta: éxito "La protección con contraseña está activa".
- [ ] Leer: la etiqueta aparece como protegida (estado "Protegida", botón Desbloquear).
- [ ] Desbloquear: con la contraseña MALA aparece "contraseña incorrecta" y NO se reintenta solo; con la buena, "Contraseña aceptada".
- [ ] Seguridad, "Quitar contraseña" con la contraseña correcta: el flujo autentica y deja la etiqueta como de fábrica; Leer ya no la muestra protegida.
- [ ] Variante solo escritura (desactiva "Pedir la contraseña también para leer"): Leer funciona sin contraseña, escribir NO.

## 3. Interrupción (G5): etiqueta perdida a mitad

- [ ] Repite la sección 2 en una etiqueta nueva y retira la etiqueta del teléfono justo después de pulsar Confirmar.
- [ ] Resultado esperado: fallo con el texto "La contraseña se escribió pero la protección NO está activa ... Reintenta con la misma contraseña".
      NUNCA un éxito. Reintentar con "Intentar de nuevo" y la misma etiqueta termina el trabajo.
- [ ] El historial tiene una entrada de tipo Seguridad con el resultado fallido y SIN la contraseña.

## 4. Límite de intentos y CFGLCK (NTAG213 de sacrificio B; permanente)

- [ ] Seguridad, "Bloquear configuración o limitar intentos": elige límite 3 y NO marques CFGLCK. Pide su propio cuadro de confirmación.
- [ ] Desbloquear: antes de poder escribir la contraseña aparece "Tras 3 intentos fallidos esta etiqueta se bloquea para siempre"
      y hay que pulsar "Entendido".
- [ ] Tres contraseñas malas: la tercera deja la zona bloqueada; después ni la contraseña correcta funciona y la app lo explica.
- [ ] En otra NTAG de sacrificio: CFGLCK. Después de sacar la etiqueta del campo, ya no se puede cambiar la configuración;
      la app lo explica. PWD y PACK siguen siendo escribibles.

## 5. Bloqueo permanente (NTAG216 o NTAG215 de sacrificio; permanente)

- [ ] Con Avanzado APAGADO, "Bloquear la etiqueta para siempre" ofrece solo "Dejar de solo lectura con Android". Las demás opciones están deshabilitadas.
- [ ] "Dejar de solo lectura con Android": tras confirmar, Leer muestra la etiqueta como no escribible y Escribir la rechaza.
- [ ] Con Avanzado ENCENDIDO, las opciones directas aparecen y el cuadro de confirmación muestra los comandos exactos
      (CC `00 00 00 0F`, bloqueo dinámico, bloqueo estático al final). Verifica en el volcado anotado que las páginas quedaron bloqueadas.
- [ ] Un segundo intento de bloqueo dice "no hay nada que cambiar".

## 6. Firma de originalidad (NS-07)

- [ ] Seguridad, "Firma de originalidad" con una NTAG: muestra UID y 32 bytes y el texto "Firma leída, sin verificar".
      NO dice en ningún sitio que la etiqueta sea genuina.
- [ ] Con una etiqueta que no es NTAG21x: mensaje claro de que no es compatible.

## 7. Consola de comandos (NS-08)

- [ ] Ajustes: enciende Avanzado. Seguridad muestra la entrada "Consola de comandos". Apágalo y la entrada desaparece.
- [ ] La primera vez en la sesión aparece la advertencia; hay que pulsar "Entiendo el riesgo". Al volver a entrar en la misma sesión NO se repite.
- [ ] `60` sobre una NTAG215 devuelve `00 04 04 02 01 00 11 03` sin pedir confirmación.
- [ ] Texto que no son bytes completos (`30 0`) muestra el error junto al campo y no envía nada.
- [ ] `A2 10 01 02 03 04` (página de usuario, etiqueta de sacrificio): pide confirmación sencilla y escribe solo al confirmar.
- [ ] `A2 83 04 00 00 04` (CFG0): muestra la vista previa de comandos y exige escribir los 4 últimos caracteres del UID; cancelar no envía nada.
- [ ] Retirar la etiqueta durante un comando muestra "Etiqueta perdida" y la consola queda lista para otra trama.

## 8. Versión y R8 (con el APK firmado)

- [ ] `versionName` 1.0.0 y `versionCode` 600; se instala encima de la 0.5.0 sin desinstalar (misma llave que las anteriores si ya las firmaste con ella).
- [ ] Humo de R8: guardar una entrada del historial, leer una etiqueta de tarea, navegar TODAS las pantallas, exportar/importar JSON, QR de Wi-Fi: sin cierres.
- [ ] `apksigner verify --verbose` indica firma v2 o superior y un certificado que no es el de depuración.

Cualquier punto fallido BLOQUEA la publicación (RB-05).
