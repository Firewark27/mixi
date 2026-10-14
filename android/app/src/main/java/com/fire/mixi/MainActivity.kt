package com.fire.mixi

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fire.mixi.databinding.ActivityMainBinding
import com.yausername.youtubedl_android.YoutubeDL

/** Pantalla única de Mixi: barra nativa arriba + interfaz web debajo. */
class MainActivity : Activity() {

    private lateinit var vista: ActivityMainBinding
    private var descargando = false

    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        vista = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vista.root)

        crearCanalDeNotificaciones()
        pedirPermisoDeNotificaciones()
        configurarWebView()
        conectarBotones()
        arrancarMotor()

        vista.webview.loadUrl(URL_WEB)
        revisarActualizacion(manual = false)
    }

    /* ---------------- WebView ---------------- */

    private fun configurarWebView() = with(vista.webview) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true          // sin esto el historial no persiste
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(vista: WebView?, url: String?) {
                super.onPageFinished(vista, url)
                mostrarSinInternet(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                vista: WebView?,
                codigo: Int,
                descripcion: String?,
                urlFallida: String?
            ) {
                super.onReceivedError(vista, codigo, descripcion, urlFallida)
                if (urlFallida == null || urlFallida.startsWith(URL_WEB)) {
                    mostrarSinInternet(true)
                }
            }
        }

        addJavascriptInterface(
            DownloadBridge(
                alPedirVideo = { url -> runOnUiThread { descargar(url, esVideo = true) } },
                alPedirAudio = { url -> runOnUiThread { descargar(url, esVideo = false) } },
                alBuscarActualizacion = { runOnUiThread { revisarActualizacion(manual = true) } },
                leerPortapapeles = { leerPortapapeles() }
            ),
            "Mixi"
        )
    }

    private fun conectarBotones() {
        // El botón ACTUALIZAR también refresca la interfaz: así llega siempre la última versión
        vista.btnActualizar.setOnClickListener {
            vista.webview.clearCache(true)
            vista.webview.reload()
            revisarActualizacion(manual = true)
        }

        vista.btnReintentar.setOnClickListener {
            mostrarSinInternet(false)
            vista.webview.reload()
        }
    }

    private fun mostrarSinInternet(si: Boolean) {
        vista.panelSinInternet.visibility = if (si) View.VISIBLE else View.GONE
    }

    /* ---------------- Motor ---------------- */

    private fun arrancarMotor() {
        Thread {
            runCatching {
                EngineManager.iniciar(applicationContext)
                EngineManager.mantenerAlDia(applicationContext)
            }
        }.start()
    }

    fun descargar(url: String, esVideo: Boolean) {
        val limpia = url.trim()
        if (limpia.isEmpty()) {
            avisarError("Primero pega el enlace del video 😊")
            return
        }
        if (descargando) {
            avisarError("Espera, ya hay una descarga en curso ⏳")
            return
        }

        descargando = true
        vista.progreso.visibility = View.VISIBLE
        avisarProgreso(0)

        Thread {
            try {
                EngineManager.limpiarTemporales(applicationContext)
                val carpeta = EngineManager.carpetaTemporal(applicationContext)

                val peticion = if (esVideo) {
                    EngineManager.peticionDeVideo(limpia, carpeta)
                } else {
                    EngineManager.peticionDeAudio(limpia, carpeta)
                }

                YoutubeDL.getInstance().execute(peticion, "mixi") { progreso, _ ->
                    avisarProgreso((progreso * 100).toInt().coerceIn(0, 100))
                }

                // El archivo que acaba de caer es el más reciente de la carpeta
                val archivo = carpeta.listFiles()
                    ?.filter { it.isFile && it.length() > 0 }
                    ?.maxByOrNull { it.lastModified() }

                val nombre = archivo?.let { MediaSaver.guardar(applicationContext, it, esVideo) }

                if (nombre != null) {
                    avisarTerminado(nombre)
                } else {
                    avisarError("No se pudo guardar el archivo 😕")
                }
            } catch (e: Exception) {
                avisarError(EngineManager.traducirError(e))
            } finally {
                descargando = false
                runOnUiThread { vista.progreso.visibility = View.GONE }
            }
        }.start()
    }

    /* ---------------- Actualizaciones (nivel 3) ---------------- */

    fun revisarActualizacion(manual: Boolean) {
        if (manual) {
            vista.progreso.visibility = View.VISIBLE
            vista.btnActualizar.isEnabled = false
        }

        UpdaterManager.consultar { info ->
            runOnUiThread {
                if (manual) {
                    vista.progreso.visibility = View.GONE
                    vista.btnActualizar.isEnabled = true
                }

                when {
                    info == null -> if (manual) brindis("Revisa tu internet 📶")

                    info.hayNueva() -> {
                        vista.btnActualizar.text = getString(R.string.btn_actualizar_hay)
                        if (manual) {
                            bajarActualizacion(info)
                        } else {
                            avisarWeb("window.MixiUI.error('⬆️ Hay una versión nueva. Toca ACTUALIZAR')")
                        }
                    }

                    manual -> brindis(getString(R.string.al_dia))
                }
            }
        }
    }

    private fun bajarActualizacion(info: UpdaterManager.InfoVersion) {
        vista.progreso.visibility = View.VISIBLE
        vista.btnActualizar.isEnabled = false
        brindis(getString(R.string.actualizando))

        UpdaterManager.descargarEInstalar(applicationContext, info) { progreso ->
            runOnUiThread {
                when {
                    progreso < 0 -> {
                        vista.progreso.visibility = View.GONE
                        vista.btnActualizar.isEnabled = true
                        brindis("No se pudo descargar la actualización 😕")
                    }

                    progreso >= 100 -> {
                        vista.progreso.visibility = View.GONE
                        vista.btnActualizar.isEnabled = true
                        brindis("Ya casi: dele a INSTALAR")
                    }

                    else -> avisarWeb("window.MixiUI.progress($progreso)")
                }
            }
        }
    }

    /* ---------------- Avisos hacia la web ---------------- */

    private fun avisarWeb(js: String) {
        vista.webview.post { vista.webview.evaluateJavascript(js, null) }
    }

    private fun avisarProgreso(porcentaje: Int) {
        avisarWeb("window.MixiUI.progress($porcentaje)")
    }

    private fun avisarTerminado(nombre: String) {
        avisarWeb("window.MixiUI.done(${textoParaJs(nombre)})")
        vibrar()
        notificarListo(nombre)
    }

    private fun avisarError(mensaje: String) {
        avisarWeb("window.MixiUI.error(${textoParaJs(mensaje)})")
    }

    /* ---------------- Portapapeles ---------------- */

    fun leerPortapapeles(): String = runCatching {
        val gestor = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        gestor.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }.getOrDefault("")

    /* ---------------- Notificaciones y vibración ---------------- */

    private fun crearCanalDeNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL,
                getString(R.string.notif_canal),
                NotificationManager.IMPORTANCE_LOW
            )
            val gestor = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            gestor.createNotificationChannel(canal)
        }
    }

    private fun pedirPermisoDeNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
            }
        }
    }

    private fun notificarListo(nombre: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val notificacion = NotificationCompat.Builder(this, CANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(R.string.notif_listo))
                .setContentText(nombre)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(this).notify(1, notificacion)
        }
    }

    private fun vibrar() {
        runCatching {
            val vibrador = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrador.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrador.vibrate(250)
            }
        }
    }

    private fun brindis(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    /* ---------------- Ciclo de vida ---------------- */

    override fun onBackPressed() {
        // El botón atrás no debe sacar a la usuaria de la app por accidente
        if (vista.webview.canGoBack()) {
            vista.webview.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        const val URL_WEB = "https://firewark27.github.io/mixi/"
        private const val CANAL = "mixi_descargas"
    }
}
