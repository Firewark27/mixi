package com.fire.mixi

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * El motor: inicializa yt-dlp + ffmpeg, arma las peticiones de descarga
 * y mantiene el motor al día.
 */
object EngineManager {

    private const val PREFS = "mixi_motor"
    private const val CLAVE_ULTIMA = "ultima_revision_motor"
    private const val UN_DIA = 24L * 60 * 60 * 1000

    @Volatile
    private var iniciado = false

    /** Arranca el motor. Se puede llamar varias veces sin problema. */
    fun iniciar(context: Context) {
        if (iniciado) return
        synchronized(this) {
            if (iniciado) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            iniciado = true
        }
    }

    /**
     * NIVEL 1 de actualización: baja la última versión del motor sin que
     * nadie toque nada. Máximo una vez cada 24 h, siempre en segundo plano.
     *
     * Esto es lo que arregla sola la app cuando YouTube cambia algo.
     */
    fun mantenerAlDia(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ultima = prefs.getLong(CLAVE_ULTIMA, 0L)
        if (System.currentTimeMillis() - ultima < UN_DIA) return

        Thread {
            runCatching {
                YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                prefs.edit().putLong(CLAVE_ULTIMA, System.currentTimeMillis()).apply()
            }
        }.start()
    }

    /** Carpeta temporal donde cae el archivo antes de ir a Galería o Música. */
    fun carpetaTemporal(context: Context): File {
        val carpeta = File(context.filesDir, "descargas")
        if (!carpeta.exists()) carpeta.mkdirs()
        return carpeta
    }

    /** Limpia lo que haya quedado de descargas anteriores. */
    fun limpiarTemporales(context: Context) {
        runCatching {
            carpetaTemporal(context).listFiles()?.forEach { it.delete() }
        }
    }

    /** Video a 720p — formato directo, sin romper nada. */
    fun peticionDeVideo(url: String, destino: File): YoutubeDLRequest =
        YoutubeDLRequest(url).apply {
            addOption("-f", "best[height<=720][ext=mp4]/best[height<=720]/best")
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--no-part")
            addOption("-o", File(destino, "%(title)s.%(ext)s").absolutePath)
        }

    /** Solo audio, en m4a (lo lee cualquier reproductor). */
    fun peticionDeAudio(url: String, destino: File): YoutubeDLRequest =
        YoutubeDLRequest(url).apply {
            addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            addOption("--extract-audio")
            addOption("--audio-format", "m4a")
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--no-part")
            addOption("-o", File(destino, "%(title)s.%(ext)s").absolutePath)
        }

    /**
     * Traduce cualquier error del motor a algo que una persona pueda entender.
     * Regla de oro: nunca se muestra un mensaje técnico en pantalla.
     */
    fun traducirError(error: Throwable?): String {
        val bruto = (error?.message ?: "") + " " + (error?.cause?.message ?: "")
        val t = bruto.lowercase()

        return when {
            t.contains("sign in to confirm") ||
                t.contains("unable to extract") ||
                t.contains("failed to extract") ||
                t.contains("no video formats") ||
                t.contains("unable to download") -> "No se pudo. Dele a ACTUALIZAR 🔄"

            t.contains("unknownhost") ||
                t.contains("unable to resolve host") ||
                t.contains("network is unreachable") ||
                t.contains("connection") -> "Revisa tu internet 📶"

            t.contains("no space left") ||
                t.contains("enospc") -> "Tu cel está lleno 📦"

            t.contains("private video") ||
                t.contains("video unavailable") -> "Ese video no está disponible 😕"

            else -> "Algo salió mal. Dele a ACTUALIZAR"
        }
    }
}
