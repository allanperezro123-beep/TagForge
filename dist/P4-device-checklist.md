# Tag Forge - Fase 4 (Tareas): lista de verificación en dispositivo

APK: `dist/tag-forge-p4-debug.apk` (versión 0.4.0-debug, versionCode 400, paquete `com.tagforge.app.debug`).

## Instalación

1. `adb install -r dist/tag-forge-p4-debug.apk` (actualiza sobre la Fase 3.1 sin perder historial ni plantillas; la base de datos no cambia de versión).
2. La barra inferior ahora tiene **Leer · Escribir · Tareas · Herramientas · Más**.
3. En **Más** hay una entrada nueva, **Permisos**, con el Centro de permisos.

## Material necesario

- Una etiqueta **NTAG215** en blanco (la principal) y una **NTAG213** (para el aviso de desborde).
- Un teléfono con Android 13 o superior; si puedes, otro con Android 10-12 y otro con Android 9 o inferior para la matriz de la sección 8.
- Un cable o la app de ajustes para revisar permisos especiales, y una URL de prueba (por ejemplo un servicio que muestre las peticiones recibidas).

## Reglas que NUNCA deben romperse (marca cada una)

- [ ] Ninguna tarea se ejecuta sin que aparezca la hoja de confirmación y pulses **Ejecutar**.
- [ ] **Cancelar**, Atrás o tocar fuera de la hoja: no se ejecuta nada y no se pide ningún permiso.
- [ ] Una acción que Android no deja hacer en silencio abre el panel o la pantalla de ajustes correspondiente y lo dice; nunca aparece "hecho" si no se hizo.
- [ ] La app no pide `QUERY_ALL_PACKAGES`, `SEND_SMS` ni `CALL_PHONE` (revisa en Ajustes > Apps > Permisos: no deben aparecer).
- [ ] Todo el texto está en español e inglés (cambia el idioma del teléfono y recorre las pantallas).

## 1. Pestaña Tareas (lista)

- [ ] Sin tareas: se ve "Aún no hay tareas" con el botón **Crear una tarea**.
- [ ] Con tareas: cada fila muestra el nombre, "N acciones" y, si corresponde, insignias de riesgo (con icono y texto).
- [ ] Menú de la fila: **Editar**, **Duplicar** (crea "Nombre (2)"), **Eliminar** (pide confirmación; Cancelar o Atrás no borra).
- [ ] El botón de reproducir abre la hoja de confirmación de la tarea; **no ejecuta nada por sí solo**.
- [ ] Al girar la pantalla o cerrar y reabrir la app, las tareas siguen ahí.

## 2. Constructor de tareas

- [ ] **Nueva tarea**: nombre, lista de acciones vacía con explicación, **Agregar una acción**.
- [ ] El selector muestra las 19 acciones agrupadas (Sonido y pantalla, Conexiones, Aplicaciones y enlaces, Mensajes y voz, Tiempo).
- [ ] Cada formulario valida: enlaces con `intent:`, `file:`, `content:` o `javascript:` quedan **bloqueados con explicación**; número de teléfono, paquete, hora (0-23, 0-59) y temporizador (1 s - 65535 s) muestran su error escrito debajo del campo.
- [ ] HTTP con `http://` muestra el aviso de que no está cifrado; no se pueden poner cabeceras ni cuerpo.
- [ ] Subir, bajar, editar y quitar acciones funciona; al quitar aparece **Deshacer**.
- [ ] A las 16 acciones el botón de agregar se desactiva y lo explica.
- [ ] El medidor de bytes cambia en vivo; con el selector 213/215/216 se ve cuánto cabe en cada una.
- [ ] **Guardar tarea** sin nombre: no guarda y el campo explica por qué. Con nombre: aparece "Se guardó ...".

## 3. Escribir la tarea en una NTAG215

- [ ] Arma una tarea de 3 acciones (por ejemplo: timbre en vibración, abrir un enlace, temporizador de 60 s). **Escribir en la etiqueta** -> acerca la NTAG215 en blanco -> "escritura verificada".
- [ ] Si la etiqueta ya tenía datos: pide confirmar el reemplazo antes de escribir; "Cancelar" no escribe.
- [ ] En **Leer** esa etiqueta se ve como "Tarea de Tag Forge" con el botón **Revisar y ejecutar** (ver sección 5).
- [ ] Con una **NTAG213** y una tarea grande: el medidor marca "Demasiado grande" y recomienda NTAG215/216; el botón Escribir queda desactivado con el motivo debajo.

## 4. Escaneo desde la pantalla de inicio (arranque en frío)

- [ ] Con la app cerrada y la pantalla encendida y desbloqueada, acerca la etiqueta: aparece la **hoja de confirmación** sobre la pantalla anterior (sin abrir toda la app).
- [ ] La hoja lista **cada acción en lenguaje claro con todos sus parámetros** (URL, número, cuerpo del SMS, paquete, método + URL) e insignias en HTTP, SMS, llamada, enlace, app y Wi-Fi.
- [ ] El foco inicial está en **Cancelar**. Atrás o tocar fuera = cancelar. No hay cuenta atrás que confirme sola.
- [ ] **Cancelar**: la hoja se cierra y no ocurre nada. **Ejecutar**: cada acción corre en orden y muestra su resultado; al terminar se cierra sola en ~1,5 s si todo salió bien.
- [ ] Con la pantalla bloqueada la hoja NO aparece por encima del bloqueo (no hay "mostrar con pantalla bloqueada").
- [ ] Si la app no está instalada en otro teléfono, Android ofrece instalarla (registro AAR al final del mensaje).

## 5. Escaneo con la app abierta

- [ ] En la pestaña **Leer** con la app en primer plano, acerca la etiqueta de tareas: aparece **una sola** tarjeta "Tarea de Tag Forge" y la hoja se abre **una única vez** (nunca doble).
- [ ] Una etiqueta con una tarea dañada o de versión desconocida muestra un error claro y **no ejecuta nada parcial**.

## 6. Centro de permisos (Más > Permisos)

- [ ] Filas: modificar ajustes del sistema, No molestar, notificaciones (Android 13+), NFC y alarmas, cada una con estado (Concedido / Falta conceder / No disponible en esta versión) y una línea de motivo.
- [ ] **Conceder** abre la pantalla correcta (`Modificar ajustes del sistema` con el paquete, acceso a No molestar, ajustes NFC).
- [ ] Concede "Modificar ajustes del sistema" y vuelve a la app: la fila pasa a **Concedido sin reiniciar**.
- [ ] En la hoja de confirmación, el botón **Arreglar** de una acción que falta de permiso abre el Centro de permisos.

## 7. Estado de cada acción tras ejecutar (resultado esperado)

| Acción | Resultado esperado |
| --- | --- |
| Timbre normal / vibración | Hecho. |
| Timbre silencio sin acceso a No molestar | Pone **vibración**, lo informa y ofrece Arreglar. Con acceso: silencio real. |
| Volumen | Hecho (0 % en timbre/notificación pide No molestar). |
| Brillo sin "Modificar ajustes" | Abre la pantalla de permiso y dice "permiso necesario". Con permiso: hecho. |
| Linterna | Hecho; "No disponible" si el teléfono no tiene flash. |
| Botón multimedia | Hecho. |
| Abrir enlace / app / pantalla de ajustes | Abre la app o pantalla; si la app no está instalada: "no instalada". |
| SMS | Abre el SMS **redactado**; tú pulsas enviar. |
| Marcar | Abre el marcador con el número; tú llamas. |
| Alarma / Temporizador / Evento | Abre la app de reloj o calendario con los datos. |
| Decir en voz alta | Habla el texto (espera a que el motor de voz arranque). |
| Notificación | La muestra (en Android 13+ pide el permiso antes). |
| Petición HTTP | Código de respuesta en pantalla; solo con el ejecutor en primer plano; `http` pide una segunda pulsación. |

## 8. Matriz de viabilidad por versión de Android

| Acción | Android 8-9 (API 26-28) | Android 10 (API 29) | Android 11-12 (API 30-32) | Android 13+ (API 33+) |
| --- | --- | --- | --- | --- |
| Activar/desactivar Wi-Fi | **Directo** (se cambia solo) | Panel de conectividad: "necesita tu toque" | Panel de conectividad | Panel de conectividad |
| Unirse a una red Wi-Fi | **Directo** (configuración clásica) | Sugerencia de red: Android pide aprobar | Pantalla "agregar red" de Android | Pantalla "agregar red" de Android |
| Bluetooth | Abre ajustes de Bluetooth (nunca se activa en silencio) | Igual | Igual | Igual |
| Brillo | Necesita "Modificar ajustes del sistema" | Igual | Igual | Igual |
| Timbre en silencio | Necesita acceso a No molestar; si no, vibración | Igual | Igual | Igual |
| Notificación | Directo | Directo | Directo | Necesita permiso de notificaciones |
| Linterna | Directo si hay flash | Igual | Igual | Igual |
| Resto (enlace, app, SMS, marcar, alarma, temporizador, evento, voz, HTTP, multimedia, ajustes) | Directo (abre la app del sistema) | Igual | Igual | Igual |

Marca el resultado real en cada teléfono que pruebes y anota cualquier diferencia.

## 9. Permisos del manifiesto (revisar con `aapt dump permissions`)

- [ ] Declarados: `NFC`, `VIBRATE`, `INTERNET`, `POST_NOTIFICATIONS`, `SET_ALARM`, `ACCESS_NOTIFICATION_POLICY`, `WRITE_SETTINGS`, `CHANGE_WIFI_STATE` (solo hasta API 29).
- [ ] **No** declarados: `QUERY_ALL_PACKAGES`, `SEND_SMS`, `CALL_PHONE`, `READ_SMS`, `CAMERA`.

## 10. Historial

- [ ] Escribir una tarea queda en el historial como escritura; una tarea con Wi-Fi **no guarda la contraseña** en el resumen ni en los bytes del historial.
