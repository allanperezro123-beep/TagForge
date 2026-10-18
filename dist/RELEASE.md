# Cómo firmar y publicar la versión 1.0.0 de Tag Forge

La versión final es `1.0.0` con `versionCode` 600. La compilación `release` activa R8 (minificación y recorte de recursos) y se firma
con TU llave. El repositorio no contiene ninguna llave ni contraseña: `keystore.properties`, `*.jks` y `*.keystore` están en `.gitignore`.

## 1. Crear la llave (una sola vez)

Hazlo con JDK 17. Elige tú las contraseñas y guárdalas en un gestor de contraseñas: si pierdes la llave no podrás actualizar la app
instalada, y quien la tenga puede firmar versiones falsas.

```
keytool -genkeypair -v -keystore keystore/tagforge-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias tagforge
```

Copia de seguridad: guarda `keystore/tagforge-release.jks` en un lugar cifrado FUERA del repositorio.

## 2. Configurar `keystore.properties`

```
copy keystore.properties.example keystore.properties
```

Edita `keystore.properties` y escribe tus valores reales:

```
storeFile=keystore/tagforge-release.jks
storePassword=<la contraseña del almacén>
keyAlias=tagforge
keyPassword=<la contraseña de la llave>
```

`storeFile` es relativa a la raíz del repositorio. Sin este archivo, `assembleRelease` FALLA con un mensaje que nombra
`keystore.properties`; nunca firma con la llave de depuración.

## 3. Compilar y archivar

```
gradlew.bat :domain:test :app:testDebugUnitTest :app:assembleDebug
gradlew.bat :app:assembleRelease
gradlew.bat :app:archiveRelease
```

`archiveRelease` copia el APK firmado y su `mapping.txt` de R8 a `apks/tagforge-p6-1.0.0.apk` y `apks/tagforge-p6-1.0.0-mapping.txt`
(la carpeta `apks/` no se versiona). Guarda el `mapping.txt`: sin él los errores del APK ofuscado no se pueden leer.

## 4. Verificar la firma

```
apksigner verify --verbose --print-certs apks/tagforge-p6-1.0.0.apk
```

Debe decir `Verified using v2 scheme: true` (o v3) y el certificado NO puede ser el de depuración (`CN=Android Debug`).
Comprueba también que `versionCode` es 600: `aapt dump badging apks/tagforge-p6-1.0.0.apk | findstr versionCode`.

## 5. Prueba en el teléfono con el APK firmado

Instala el APK firmado y repite los checklists de `dist/P1-device-checklist.md` a `dist/P6-device-checklist.md`.
Humo de la versión ofuscada (RB-01): guardar una entrada del historial, leer una etiqueta de tarea, abrir TODAS las pantallas
(Leer, Escribir, Herramientas, Tareas, Más, Seguridad, Firma), exportar e importar JSON, generar un QR de Wi-Fi.
Si algo falla con `ClassNotFoundException` o un error de serialización, añade la regla de conservación que R8 indique en
`app/proguard-rules.pro`.

## 6. Etiquetar la versión

La etiqueta `phase-6` ya existe: la puso el cierre de la fase 6 sobre el código con las pruebas en verde.
Cuando TODOS los puntos de los checklists pasen en el APK firmado, marca la versión final:

```
git tag v1.0.0
```

Cualquier punto fallido BLOQUEA la publicación (RB-05): corrige, vuelve a compilar, a firmar y a repetir los checklists.
