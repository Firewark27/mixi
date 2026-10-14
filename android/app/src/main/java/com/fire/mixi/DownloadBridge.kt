package com.fire.mixi

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Puente entre la interfaz web (JavaScript) y el motor (Kotlin).
 *
 * La web llama a estos métodos con `window.Mixi.algo(...)`.
 * Los métodos de @JavascriptInterface se ejecutan en un hilo aparte,
 * por eso se delega a MainActivity, que ya se encarga de volver al hilo de interfaz.
 */
class DownloadBridge(
    private val alPedirVideo: (String) -> Unit,
    private val alPedirAudio: (String) -> Unit,
    private val alBuscarActualizacion: () -> Unit,
    private val leerPortapapeles: () -> String
) {

    @JavascriptInterface
    fun downloadVideo(url: String) {
        alPedirVideo(url)
    }

    @JavascriptInterface
    fun downloadAudio(url: String) {
        alPedirAudio(url)
    }

    @JavascriptInterface
    fun checkAppUpdate() {
        alBuscarActualizacion()
    }

    @JavascriptInterface
    fun getVersionName(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    @JavascriptInterface
    fun getClipboardText(): String = runCatching { leerPortapapeles() }.getOrDefault("")
}

/** Escapa un texto para meterlo dentro de JavaScript sin romperlo. */
fun textoParaJs(valor: String): String = JSONObject.quote(valor)
