# Mixi — App Android

APK que envuelve el motor `yt-dlp` y carga la interfaz web en un WebView.
Ver el documento de diseño en `../docs/specs/`.

## Piezas

| Archivo | Responsabilidad |
|---|---|
| `MainActivity.kt` | Pantalla única: barra nativa + WebView + avisos |
| `DownloadBridge.kt` | Puente `window.Mixi.*` (JavaScript → Kotlin) |
| `EngineManager.kt` | Inicializa yt-dlp/ffmpeg, arma las descargas, mantiene el motor al día |
| `MediaSaver.kt` | Deja el archivo en Galería / app de Música vía `MediaStore` |
| `UpdaterManager.kt` | Consulta `version.json`, baja el APK nuevo y abre el instalador |

## Icono y estilo

- Icono adaptativo: flecha de descarga blanca sobre índigo `#4F46E5`
- `minSdk 26` (Android 8) · `targetSdk 36` (Android 16)
- Solo `arm64-v8a` y `armeabi-v7a` (los procesadores de celulares modernos)

## Firma

La APK **debe firmarse siempre con la misma keystore**, o Android rechazará
la actualización. La keystore vive en los Secrets del repositorio:

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | la keystore codificada |
| `KEYSTORE_PASSWORD` | su contraseña |
| `KEY_ALIAS` | `mixi` |
| `KEY_PASSWORD` | la contraseña de la clave |

Hay una **copia de respaldo** en `C:\Users\Fire\mixi-keystore-respaldo\`.
Si se pierde esa keystore, ya no se podrán instalar actualizaciones encima:
habría que desinstalar y volver a instalar la app.

## Compilar

No hace falta Android Studio. El APK se compila en GitHub Actions:

```
Actions → "Compilar APK" → Run workflow
```

Cada compilación publica el APK en Releases y actualiza `version.json`,
que es lo que hace que el celular detecte que hay versión nueva.

## Detalles del motor que es fácil olvidar

- `execute(...)` recibe un callback de progreso con **tres** parámetros
  (`progreso`, `eta`, `línea`).
- `extractNativeLibs="true"` en el manifest es **obligatorio** para esta librería.
- Para que el historial funcione, el WebView necesita `domStorageEnabled = true`.
- En Android 10+ la escritura directa fuera de `Download/` y `Documents/` está
  bloqueada: por eso el guardado pasa por `MediaStore`.
