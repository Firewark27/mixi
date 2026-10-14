# Mixi — Aplicación de descarga de video y audio para Android

- **Fecha:** 2026-10-14
- **Estado:** ✅ **APROBADO** — 2026-10-14
- **Responsable de producto:** Fire
- **Usuario final:** la mamá de Fire (usuaria no técnica)

---

## 1. Contexto y problema

La mamá de Fire consume contenido religioso (predicaciones, alabanzas) en YouTube y quiere
guardarlo para verlo y escucharlo **sin internet**. No maneja bien la tecnología.

Restricciones reales del caso:

| Restricción | Consecuencia de diseño |
|---|---|
| La usuaria es no técnica | Un solo camino: pegar link → tocar → listo |
| Fire **no vive con ella** | Todo debe funcionar sin él; debe poder actualizar a distancia |
| Debe ser gratis | Solo servicios con plan gratuito |
| Si falla, Fire debe poder arreglarlo fácil | Auto-reparación + botón de actualizar |

### 1.1 Por qué NO se puede hacer como iCash (página web sola)

Una página web **no puede** descargar de YouTube:

1. YouTube bloquea las peticiones del navegador (CORS + firmas cifradas)
2. Se necesita un motor real (`yt-dlp`) que descifre el video y `ffmpeg` para extraer audio
3. El navegador no puede escribir en la Galería del celular

→ **Se necesita una app instalada (APK).**

---

## 2. Decisiones de arquitectura

### 2.1 Descartado: Vercel (o cualquier servidor) como motor

Investigación verificada (octubre 2026):

- YouTube **bloquea las IPs de datacenter** (AWS, GCP, Azure) con el error
  `Sign in to confirm you're not a bot`, **antes** de evaluar la petición.
  Vercel corre sobre AWS → bloqueado.
- **No existe librería que lo evite.** Se verificó `@distube/ytdl-core`, `youtubei.js`,
  `play-dl` y `distube`: todas fallan igual desde servidor. **El bloqueo es por IP, no por código.**
- El límite de **4.5 MB por respuesta** de Vercel Functions impediría entregar el archivo.
- La instancia pública de **Cobalt está bloqueada para YouTube** desde mediados de 2025
  (issue #1356 del repo oficial; sigue así en 2026).

**Conclusión:** el motor debe correr en una **IP residencial** — y la IP más a mano es la del celular.

### 2.2 Elegida: APK cáscara (UI remota + motor local)

```
┌──────────────────────────────────────────────┐
│  APK "Mixi" EN EL CELULAR                    │
│  ┌────────────────────────────────────────┐  │
│  │ BARRA NATIVA: [logo]   🔄 ACTUALIZAR   │  │ ← siempre existe
│  ├────────────────────────────────────────┤  │
│  │ WEBVIEW → UI servida desde Vercel      │  │ ← Fire la actualiza
│  │ campo link · VIDEO · MÚSICA · barra    │  │   sin tocar el cel
│  ├────────────────────────────────────────┤  │
│  │ MOTOR nativo: yt-dlp + ffmpeg          │  │ ← IP del cel = no bloquea
│  │ guarda en Galería / Música             │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

**Por qué esta y no otra:**

| Alternativa | Motivo del descarte |
|---|---|
| APK 100% nativo (Compose) | Cada cambio de diseño obliga a recompilar + reinstalar |
| PWA + servidor en la PC de Fire | Depende de que la PC esté encendida |
| Servidor en Vercel/VPS | IP de datacenter → bloqueado por YouTube |
| Instalar una app ya hecha (Seal) | Es funcional pero tiene menús de calidad/formato que confunden a la usuaria |

---

## 3. Componentes

### 3.1 APK — Mixi (Kotlin)

| Aspecto | Valor |
|---|---|
| Paquete | `com.fire.mixi` |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 (Android 15) — requerido para soporte de páginas de 16 KB |
| ABI | `arm64-v8a` (principal) + `armeabi-v7a` si el celular lo requiere |
| Motor | `io.github.junkfood02.youtubedl-android:library:0.18.1` (Maven Central) |
| FFmpeg | `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` |
| Manifest | `android:extractNativeLibs="true"` (exigido por la librería) |

**Clases:**

| Clase | Responsabilidad |
|---|---|
| `MainActivity` | WebView + barra superior nativa (logo + botón ACTUALIZAR) |
| `DownloadBridge` | `@JavascriptInterface` — API que la web llama |
| `EngineManager` | Inicializa yt-dlp/ffmpeg y actualiza yt-dlp en segundo plano |
| `MediaSaver` | Inserta el archivo en `MediaStore` (Video/Audio) |
| `UpdaterManager` | Compara versiones, descarga el APK nuevo y lanza el instalador |

**Permisos:** `INTERNET`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES`,
`WRITE_EXTERNAL_STORAGE` (solo API ≤ 28).

### 3.2 UI — Web (GitHub Pages)

- `index.html` + `app.js` + `style.css` (JavaScript puro, sin frameworks)
- **Pantalla única:** campo de link, botón VIDEO, botón SOLO MÚSICA, barra de progreso, historial
- `version.json`: manifiesto de versión del APK que consulta la app
- Carga con parámetro anti-caché para que los cambios se vean al instante

### 3.3 Puente de comunicación

| Dirección | Llamada |
|---|---|
| Web → Kotlin | `window.Mixi.downloadVideo(url)` · `downloadAudio(url)` · `checkAppUpdate()` |
| Kotlin → Web | `MixiUI.progress(45)` · `MixiUI.done(nombre)` · `MixiUI.error(mensaje)` |

### 3.4 Compilación — GitHub Actions

- Workflow `.github/workflows/build-apk.yml` (JDK 17 + Android SDK, incluidos en el runner)
- Firma con **keystore guardada en GitHub Secrets** (la misma siempre → las actualizaciones instalan encima)
- Publica el APK en **GitHub Releases** y actualiza `version.json`

### 3.5 Distribución

- **Primera instalación:** Fire la hace **en persona** durante su visita (~5 minutos)
- **Después:** botón ACTUALIZAR dentro de la app

---

## 4. Sistema de actualización (3 niveles)

| Nivel | Mecanismo | Disparador | Toques de la usuaria | Peso |
|---|---|---|---|---|
| **1. Motor yt-dlp** | `updateYoutubeDL(context, NIGHTLY)` | Automático al abrir (máx. 1 vez/día) | **0** | ~3 MB |
| **2. Interfaz** | Deploy automático de GitHub Pages al hacer push | Al abrir la app | **0** | ~0 |
| **3. APK** | `version.json` + descarga + Intent de instalación | Botón ACTUALIZAR / banner | **3** (guiados por Fire) | ~100 MB |

**Flujo del botón ACTUALIZAR:**

1. Consulta `version.json`; si `versionCode` remoto > local → botón en modo brillante + banner
2. Al tocar: descarga el APK nuevo mostrando barra de progreso
3. Lanza el instalador de Android (`FileProvider` + `ACTION_VIEW`)
4. Android pide confirmación → **INSTALAR**
5. Si no hay versión nueva: `✓ Tu app está al día`

> **Nota honesta:** Android no permite instalar en silencio sin root. Siempre serán 3 toques.
> La ganancia real es que **el 90 % de los fallos los arregla el Nivel 1 sin que nadie toque nada.**

---

## 5. Manejo de errores (lenguaje humano)

Nunca se muestra un error técnico a la usuaria.

| Fallo | Mensaje en pantalla | Acción ofrecida |
|---|---|---|
| Sin internet | "Revisa tu internet 📶" | Reintentar |
| Link inválido | "Ese link no sirve. Copia otro 😕" | — |
| YouTube rompió el extractor | "No se pudo. Dele a ACTUALIZAR 🔄" | Botón ACTUALIZAR |
| Sin espacio | "Tu cel está lleno 📦" | — |
| Error desconocido | "Algo salió mal. Dele a ACTUALIZAR" | ACTUALIZAR |

> **Decidido:** **no** se incluye ningún botón de aviso por WhatsApp en la versión 1.

---

## 6. Experiencia de uso

- Botones ≥ 56 dp, texto ≥ 18 sp, alto contraste
- **Auto-detección del link:** al copiar en YouTube y abrir Mixi, el campo ya viene lleno
- Notificación + vibración al terminar: "✅ Listo, está en tu galería"
- Historial visible de lo ya descargado
- **Guardado directo en Galería y en la app de Música** (vía `MediaStore`) — sin carpetas perdidas

---

## 7. Fuera de alcance (versión 1)

- Descarga de playlists o canales completos
- Soporte para iOS
- Descarga con la app cerrada (servicio en segundo plano)
- Selección de calidad (se fija **720p** — aprobado: se ve bien y descarga rápido)
- Botón de aviso por WhatsApp ("Avisar a mi hijo") — descartado por Fire
- Registro de errores en servidor

---

## 8. Riesgos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| YouTube rompe el extractor | Alta | Alta | Nivel 1 automático (yt-dlp nightly) |
| Vercel no carga | Baja | Media | UI en caché + pantalla nativa de respaldo |
| Pérdida de la keystore | Baja | Alta | Respaldo de la keystore fuera de GitHub |
| El celular no es arm64 | Baja | Alta | Verificar modelo antes de compilar |
| `MediaStore` falla | Media | Media | Guardar en `Download/` como respaldo |
| APK demasiado grande para enviar | Media | Baja | Distribución por Google Drive, no WhatsApp |

---

## 9. Estructura del proyecto

```
C:\Users\Fire\mixi\
├─ docs\
│  └─ specs\
│     └─ 2026-10-14-mixi-design.md      ← este documento
├─ web\                                 ← se publica en GitHub Pages
│  ├─ index.html
│  ├─ app.js
│  ├─ style.css
│  └─ version.json
├─ android\                             ← el APK
│  ├─ app\
│  │  ├─ src\main\
│  │  │  ├─ java\com\fire\mixi\
│  │  │  │  ├─ MainActivity.kt
│  │  │  │  ├─ DownloadBridge.kt
│  │  │  │  ├─ EngineManager.kt
│  │  │  │  ├─ MediaSaver.kt
│  │  │  │  └─ UpdaterManager.kt
│  │  │  ├─ assets\fallback.html        ← UI de respaldo sin internet
│  │  │  ├─ res\
│  │  │  └─ AndroidManifest.xml
│  │  └─ build.gradle.kts
│  ├─ build.gradle.kts
│  └─ settings.gradle.kts
├─ .github\workflows\build-apk.yml      ← compila el APK en la nube
└─ README.md
```

---

## 10. Próximos pasos

1. Verificar modelo y versión de Android del celular de su mamá (requisito: API 26+)
2. Crear el repositorio en GitHub y conectarlo a Vercel
3. Implementar la web (pantalla única + puente)
4. Implementar el APK (WebView + motor + MediaStore + updater)
5. Compilar vía GitHub Actions y probar en un celular de prueba
6. Probar todas las rutas de error
7. Generar la guía visual de instalación (Fire la instalará en persona)

---

## 11. Decisiones cerradas

| # | Pregunta | Decisión |
|---|---|---|
| 1 | Modelo y versión de Android del celular | ✅ Android moderno (API 26+, arm64) — sin problema |
| 2 | ¿Se incluye el botón "Avisar a mi hijo"? | ❌ **No se incluye** |
| 3 | ¿Calidad fija 720p es suficiente? | ✅ **Sí, 720p** — se ve bien y descarga rápido |

**No quedan preguntas abiertas. El diseño está aprobado y listo para implementar.**
