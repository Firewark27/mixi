# Mixi — Plan de Implementación

> **Para Hermes:** usar la skill `subagent-driven-development` para ejecutar tarea por tarea.

**Objetivo:** Construir "Mixi", un APK Android que permite a la mamá de Fire descargar video (720p)
y audio de YouTube pegando un link, con interfaz remota actualizable desde Vercel y auto-reparación
del motor yt-dlp.

**Arquitectura:** APK cáscara en Kotlin (WebView + barra nativa) con el motor yt-dlp embebido
(`youtubedl-android` 0.18.1). La interfaz vive en Vercel y se carga en el WebView. El APK se compila
en GitHub Actions y se distribuye por GitHub Releases. Actualizaciones en 3 niveles (motor / interfaz / APK).

**Stack:** Kotlin · Android SDK 35 · `io.github.junkfood02.youtubedl-android:library:0.18.1` +
`:ffmpeg:0.18.1` · WebView + JavaScriptInterface · MediaStore · HTML/CSS/JS vanilla · Vercel · GitHub Actions.

**Referencia:** `docs/specs/2026-10-14-mixi-design.md`

---

## Convenciones del proyecto

- **Raíz:** `C:\Users\Fire\mixi\`
- **Rama principal:** `main`
- **Versionado del APK:** `versionCode` entero creciente (1, 2, 3…), `versionName` semántico (`1.0.0`)
- **Idioma de la interfaz:** español, tuteo, sin tecnicismos
- **Regla de oro de la UI:** ningún mensaje técnico llega a la pantalla

---

# FASE 1 — Interfaz web (verificable localmente)

> Esta fase se puede probar **sin Android**: la web detecta si `window.Mixi` no existe
> y entra en **modo demo** (simula la descarga). Así validamos el diseño antes de compilar nada.

## Tarea 1: Estructura del proyecto + repositorio

**Objetivo:** Dejar la carpeta y el control de versiones listos.

**Archivos:**
- Crear: `C:\Users\Fire\mixi\README.md`
- Crear: `C:\Users\Fire\mixi\.gitignore`

**Paso 1:** Crear el `.gitignore`:

```gitignore
# Android
android/.gradle/
android/build/
android/app/build/
android/local.properties
*.iml
.idea/

# Keystore — NUNCA al repositorio
*.jks
*.keystore

# Sistema
.DS_Store
Thumbs.db
```

**Paso 2:** Crear el `README.md` con: qué es Mixi, arquitectura en una imagen, cómo se compila,
cómo se actualiza.

**Paso 3:** Inicializar git y hacer el primer commit:

```bash
cd C:/Users/Fire/mixi
git init
git add .
git commit -m "chore: estructura inicial del proyecto Mixi"
```

**Verificación:** `git log --oneline` muestra 1 commit.

---

## Tarea 2: Pantalla única de la interfaz

**Objetivo:** La pantalla que ve la usuaria: campo de link, botón VIDEO, botón MÚSICA, barra de progreso e historial.

**Archivos:**
- Crear: `web/index.html`
- Crear: `web/style.css`

**Especificaciones de la pantalla:**

| Elemento | Detalle |
|---|---|
| Barra superior | **La dibuja el APK (nativa)**, no la web. La web empieza debajo |
| Campo de link | Alto 64px, texto 18px, placeholder "Pega el link aquí" |
| Botón VIDEO | Alto 72px, texto 22px, color sólido |
| Botón MÚSICA | Alto 72px, texto 22px, color secundario |
| Barra de progreso | Aparece solo al descargar; porcentaje en texto grande |
| Historial | Lista de lo ya descargado (nombre + fecha), guardado en `localStorage` |

**Reglas de estilo (accesibilidad para usuaria no técnica):**
- Ningún texto menor a 16px
- Contraste alto sobre fondo claro
- Un solo camino: no hay menús ni pestañas

**Verificación:** abrir `web/index.html` en el navegador y ver la pantalla completa sin errores en consola.

---

## Tarea 3: Lógica de la interfaz + puente con el APK

**Objetivo:** Que los botones funcionen — tanto en el APK (llamando al motor) como en el navegador (modo demo).

**Archivos:**
- Crear: `web/app.js`

**Paso 1:** Detectar el entorno:

```js
const EN_APK = typeof window.Mixi !== 'undefined';
```

**Paso 2:** Exponer la API que el APK llamará para pintar el progreso:

```js
window.MixiUI = {
  progress(pct) { /* actualiza barra y porcentaje */ },
  done(nombre)  { /* 100%, notifica, agrega al historial */ },
  error(msg)    { /* muestra mensaje humano + botón ACTUALIZAR */ }
};
```

**Paso 3:** Los botones llaman al motor o simulan:

```js
async function descargar(tipo) {
  const url = campoLink.value.trim();
  if (!esLinkValido(url)) return mostrarError('Ese link no sirve. Copia otro 😕');

  if (EN_APK) {
    (tipo === 'video' ? window.Mixi.downloadVideo : window.Mixi.downloadAudio)(url);
  } else {
    simularDescarga(); // modo demo para probar la UI
  }
}
```

**Paso 4:** Guardar el historial en `localStorage` y pintarlo al abrir.

**Verificación:** en el navegador, pegar un link cualquiera → tocar VIDEO → la barra avanza hasta 100 %
y aparece en el historial (modo demo).

---

## Tarea 4: Manifiesto de versión

**Objetivo:** El archivo que el APK consulta para saber si hay actualización.

**Archivos:**
- Crear: `web/version.json`

**Contenido:**

```json
{
  "versionCode": 1,
  "versionName": "1.0.0",
  "url": "https://github.com/<USUARIO>/mixi/releases/latest/download/mixi.apk",
  "notes": "Primera versión de Mixi",
  "minSupportedVersionCode": 1
}
```

**Regla:** este archivo lo actualiza **automáticamente** el workflow de compilación (Tarea 14).

**Verificación:** `cat web/version.json` es JSON válido.

---

## Tarea 5: Publicar la web en Vercel

**Objetivo:** Que la interfaz tenga URL pública y se actualice con cada push.

**Paso 1:** Entrar a vercel.com, "Add New Project", importar el repositorio `mixi`.

**Paso 2:** Configurar **Root Directory = `web`**, Framework Preset = **Other**.

**Paso 3:** Deploy. Verificar que la URL responde:

```bash
curl -s -o /dev/null -w "%{http_code}" https://<proyecto>.vercel.app
```

**Esperado:** `200`

**Paso 4:** Probar que `version.json` es accesible:

```bash
curl -s https://<proyecto>.vercel.app/version.json
```

**Verificación:** ambos comandos responden correctamente.

---

# FASE 2 — Motor Android (el APK)

> **Nota:** esta fase no se puede probar en esta PC (no hay Android SDK). La verificación real es
> la compilación en GitHub Actions (Fase 3) y la instalación física por parte de Fire.

## Tarea 6: Esqueleto Gradle del APK

**Objetivo:** Proyecto Android compilable con el motor incluido.

**Archivos:**
- Crear: `android/settings.gradle.kts`
- Crear: `android/build.gradle.kts`
- Crear: `android/app/build.gradle.kts`
- Crear: `android/gradle/wrapper/gradle-wrapper.properties`
- Crear: `android/gradle.properties`

**Decisiones fijas:**

| Ajuste | Valor | Motivo |
|---|---|---|
| `applicationId` | `com.fire.mixi` | Identidad de la app |
| `minSdk` | 26 | Android 8.0 |
| `compileSdk` / `targetSdk` | 35 | Requisito de páginas de 16 KB |
| `ndk { abiFilters }` | `arm64-v8a`, `armeabi-v7a` | Cobertura de celulares modernos |
| `android:extractNativeLibs` | `true` | Exigido por `youtubedl-android` |
| `android:requestLegacyExternalStorage` | `true` | Compatibilidad con API 29 |

**Dependencias:**

```kotlin
implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
implementation("androidx.webkit:webkit:1.11.0")
```

**Verificación:** el workflow de la Tarea 13 llega a ejecutar `./gradlew assembleDebug` sin error de dependencias.

---

## Tarea 7: Manifest y recursos

**Objetivo:** Declarar permisos, FileProvider e ícono.

**Archivos:**
- Crear: `android/app/src/main/AndroidManifest.xml`
- Crear: `android/app/src/main/res/xml/file_paths.xml`
- Crear: `android/app/src/main/res/values/strings.xml`
- Crear: íconos en `android/app/src/main/res/mipmap-*`

**Permisos:**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

**FileProvider** (para que Android acepte instalar el APK nuevo):

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="com.fire.mixi.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**Verificación:** el APK compilado instala sin errores de manifest.

---

## Tarea 8: Motor de descarga (yt-dlp + ffmpeg)

**Objetivo:** El corazón de Mixi. Inicializa el motor, descarga a 720p y extrae audio.

**Archivos:**
- Crear: `android/app/src/main/java/com/fire/mixi/EngineManager.kt`

**Opciones exactas de descarga:**

```kotlin
// VIDEO 720p — mp4 garantizado, sin depender de ffmpeg
request.addOption("-f", "best[height<=720][ext=mp4]/best[height<=720]/best")
request.addOption("--no-mtime")
request.addOption("--no-playlist")
request.addOption("-o", "$tempDir/%(title)s.%(ext)s")
```

```kotlin
// AUDIO — m4a (compatible con todos los reproductores, sin recompresión larga)
request.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
request.addOption("--extract-audio")
request.addOption("--audio-format", "m4a")
request.addOption("--no-playlist")
```

**Auto-reparación (Nivel 1 de actualización):**

```kotlin
// Máximo 1 vez cada 24 h, en hilo de fondo, sin bloquear la interfaz
fun autoUpdateMotor() {
    val ultima = prefs.getLong("last_engine_update", 0L)
    if (System.currentTimeMillis() - ultima < 24 * 60 * 60 * 1000) return
    thread {
        runCatching {
            YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
            prefs.edit().putLong("last_engine_update", System.currentTimeMillis()).apply()
        }
    }
}
```

**Verificación:** en el celular de prueba, una descarga de video y una de audio terminan correctamente.

---

## Tarea 9: Guardado en Galería y Música

**Objetivo:** Que el archivo caiga donde la usuaria lo va a encontrar, no en una carpeta perdida.

**Archivos:**
- Crear: `android/app/src/main/java/com/fire/mixi/MediaSaver.kt`

**Enfoque:** descargar a un directorio temporal de la app y luego **insertar en `MediaStore`**:

```kotlin
// API 29+ → MediaStore (aparece en Galería / app de Música)
val coleccion = if (esVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
val valores = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
    put(MediaStore.MediaColumns.MIME_TYPE, mime)
    put(MediaStore.MediaColumns.IS_PENDING, 1)
}
```

**Respaldo:** si `MediaStore` falla, copiar a `Download/` y avisar con
"Guardado en Descargas 📂".

**Verificación:** tras una descarga, el video aparece en la Galería y el audio en el reproductor de música.

---

## Tarea 10: Puente web ↔ motor

**Objetivo:** Que la interfaz pueda pedir descargas y recibir el progreso.

**Archivos:**
- Crear: `android/app/src/main/java/com/fire/mixi/DownloadBridge.kt`

**API expuesta a JavaScript:**

```kotlin
@JavascriptInterface fun downloadVideo(url: String) { ... }
@JavascriptInterface fun downloadAudio(url: String) { ... }
@JavascriptInterface fun getVersionName(): String = BuildConfig.VERSION_NAME
@JavascriptInterface fun getVersionCode(): Int = BuildConfig.VERSION_CODE
@JavascriptInterface fun checkAppUpdate() { ... }
@JavascriptInterface fun getClipboardText(): String { ... }   // auto-detección del link
```

**Reporte de progreso hacia JavaScript:**

```kotlin
private fun reportar(pct: Int) {
    runOnUiThread {
        webView.evaluateJavascript("window.MixiUI.progress($pct)", null)
    }
}
```

**Traducción de errores (regla de oro: nada técnico):**

| Error real de yt-dlp | Mensaje a la pantalla |
|---|---|
| Contiene "Sign in to confirm" | "No se pudo. Dele a ACTUALIZAR 🔄" |
| Contiene "Unable to extract" | "No se pudo. Dele a ACTUALIZAR 🔄" |
| `UnknownHostException` / sin red | "Revisa tu internet 📶" |
| "No space left" | "Tu cel está lleno 📦" |
| Cualquier otro | "Algo salió mal. Dele a ACTUALIZAR" |

**Verificación:** descargar desde la interfaz real y ver la barra avanzar en pantalla.

---

## Tarea 11: Barra nativa + actualizador del APK

**Objetivo:** El botón ACTUALIZAR siempre presente y funcional.

**Archivos:**
- Crear: `android/app/src/main/java/com/fire/mixi/MainActivity.kt`
- Crear: `android/app/src/main/java/com/fire/mixi/UpdaterManager.kt`
- Crear: `android/app/src/main/res/layout/activity_main.xml`
- Crear: `android/app/src/main/assets/fallback.html`

**Layout:** barra superior nativa (título "Mixi" + botón `🔄 ACTUALIZAR`) y debajo el WebView.

**Lógica del actualizador:**

1. `GET https://<proyecto>.vercel.app/version.json`
2. Si `versionCode` remoto > `BuildConfig.VERSION_CODE` → botón en modo brillante + aviso
3. Al tocar: descargar el APK a caché con `DownloadManager`
4. Instalar con `FileProvider` + `ACTION_VIEW`:

```kotlin
val intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(uri, "application/vnd.android.package-archive")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
startActivity(intent)
```

5. Si no hay versión nueva → "✓ Tu app está al día"

**Respaldo sin internet:** si el WebView no carga, mostrar `assets/fallback.html`
(mensaje "Revisa tu internet 📶" + botón REINTENTAR). El botón ACTUALIZAR sigue visible porque es nativo.

**Verificación:** cambiar `version.json` a un `versionCode` mayor, tocar ACTUALIZAR y confirmar
que descarga e instala.

---

# FASE 3 — Compilación y distribución

## Tarea 12: Keystore de firma

**Objetivo:** Una firma estable para que las actualizaciones instalen encima.

**Paso 1:** El workflow (Tarea 13), en su primer run, genera la keystore si no existe el secret:

```bash
keytool -genkeypair -v -keystore mixi.jks -alias mixi \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Mixi, OU=Fire, O=Fire, L=Lima, C=PE"
```

**Paso 2:** Descargar la keystore desde los artifacts del run.

**Paso 3:** Guardarla en **Settings → Secrets and variables → Actions**:

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | `base64 mixi.jks` |
| `KEYSTORE_PASSWORD` | la contraseña |
| `KEY_ALIAS` | `mixi` |
| `KEY_PASSWORD` | la contraseña |

**Paso 4:** Guardar una **copia de respaldo** de la keystore fuera de GitHub (nunca al repositorio).

**Verificación:** el segundo run usa `KEYSTORE_BASE64` y firma correctamente.

---

## Tarea 13: Workflow de compilación

**Objetivo:** Compilar el APK en la nube, gratis, sin instalar nada en la PC.

**Archivos:**
- Crear: `.github/workflows/build-apk.yml`

**Pasos del workflow:**

1. `actions/checkout`
2. `actions/setup-java` (JDK 17)
3. `android-actions/setup-android`
4. Decodificar la keystore desde `KEYSTORE_BASE64`
5. `./gradlew assembleRelease`
6. Renombrar a `mixi.apk`
7. `softprops/action-gh-release` → publicar en GitHub Releases
8. Actualizar `web/version.json` en el repo con el nuevo `versionCode`

**Verificación:** el run termina en verde y aparece `mixi.apk` en Releases.

---

## Tarea 14: Verificación de las rutas de error

**Objetivo:** Probar que ningún fallo deja a la usuaria perdida.

**Pruebas en el celular de prueba:**

| # | Escenario | Comportamiento esperado |
|---|---|---|
| 1 | Sin wifi ni datos | "Revisa tu internet 📶" + botón REINTENTAR |
| 2 | Link de texto cualquiera | "Ese link no sirve. Copia otro 😕" |
| 3 | Link de YouTube válido, 720p | Descarga completa → aparece en Galería |
| 4 | Link de un tema musical | Descarga completa → aparece en Música |
| 5 | Tocar ACTUALIZAR sin versión nueva | "✓ Tu app está al día" |
| 6 | Tocar ACTUALIZAR con versión nueva | Descarga e instala |
| 7 | Rotar el celular durante la descarga | La descarga no se pierde |
| 8 | Cerrar y reabrir la app | El historial sigue ahí |

**Verificación:** los 8 escenarios se comportan como se describe.

---

# FASE 4 — Entrega

## Tarea 15: Instalación en el celular de su mamá

**Objetivo:** Dejar la app funcionando en su celular.

**En la visita de Fire:**

1. Copiar `mixi.apk` al celular (cable, Google Drive o Telegram)
2. Permitir "instalar apps de este origen" cuando Android lo pida
3. Instalar y abrir
4. Descargar un video de prueba y comprobar que aparece en la Galería
5. Descargar un audio de prueba y comprobar que aparece en Música
6. Explicarle los 3 pasos: **pegar link → tocar → esperar**

**Verificación:** su mamá logra descargar un video sola, siguiendo los 3 pasos.

---

## Tarea 16: Guía visual (respaldo)

**Objetivo:** Material para futuras dudas y para reinstalaciones.

**Entregable:** una página HTML con capturas grandes y 3 pasos, exportable a imagen para WhatsApp.

**Verificación:** Fire la lee y confirma que su mamá podría seguirla sola.

---

# Resumen de verificación final

| Criterio (del spec) | Cómo se comprueba |
|---|---|
| Pegar link y descargar funciona | Tarea 14, casos 3 y 4 |
| El video cae en la Galería | Tarea 14, caso 3 |
| El audio cae en Música | Tarea 14, caso 4 |
| Ningún mensaje técnico en pantalla | Tarea 14, casos 1 y 2 |
| El botón ACTUALIZAR siempre visible | Tarea 11 + caso 1 (sin internet) |
| Actualizar la interfaz sin tocar el cel | Push a `web/` → recargar la app |
| Actualizar el motor sin tocar nada | Tarea 8 (auto-update diario) |
| Actualizar el APK a distancia | Tarea 14, caso 6 |
| Funciona sin que Fire esté presente | Todas las anteriores |
