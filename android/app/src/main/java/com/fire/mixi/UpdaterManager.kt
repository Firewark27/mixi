package com.fire.mixi

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * NIVEL 3 de actualización: el APK completo.
 * Consulta el version.json publicado y, si hay algo nuevo, lo baja e instala.
 */
object UpdaterManager {

    /** Manifiesto de versión publicado junto a la interfaz. */
    const val URL_VERSION = "https://firewark27.github.io/mixi/version.json"

    private val ejecutor = Executors.newSingleThreadExecutor()

    data class InfoVersion(
        val codigo: Int,
        val nombre: String,
        val url: String,
        val notas: String
    ) {
        fun hayNueva(): Boolean = codigo > BuildConfig.VERSION_CODE
    }

    /** Pregunta si hay versión nueva. Devuelve null si no se pudo consultar. */
    fun consultar(alTerminar: (InfoVersion?) -> Unit) {
        ejecutor.execute {
            val info = runCatching {
                val conexion = (URL(URL_VERSION).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "Mixi/${BuildConfig.VERSION_NAME}")
                }
                val texto = conexion.inputStream.bufferedReader().use { it.readText() }
                conexion.disconnect()

                val json = JSONObject(texto)
                InfoVersion(
                    codigo = json.getInt("versionCode"),
                    nombre = json.optString("versionName", ""),
                    url = json.getString("url"),
                    notas = json.optString("notes", "")
                )
            }.getOrNull()

            alTerminar(info)
        }
    }

    /**
     * Baja el APK nuevo y abre el instalador de Android.
     * @param alProgreso recibe 0..100, o -1 si falló.
     */
    fun descargarEInstalar(
        context: Context,
        info: InfoVersion,
        alProgreso: (Int) -> Unit
    ) {
        ejecutor.execute {
            val destino = File(context.cacheDir, "mixi-nueva.apk")

            val ok = runCatching {
                if (destino.exists()) destino.delete()

                val conexion = (URL(info.url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "Mixi/${BuildConfig.VERSION_NAME}")
                }
                conexion.connect()

                val total = conexion.contentLengthLong
                conexion.inputStream.use { entrada ->
                    destino.outputStream().use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        var acumulado = 0L
                        while (true) {
                            val leidos = entrada.read(buffer)
                            if (leidos <= 0) break
                            salida.write(buffer, 0, leidos)
                            acumulado += leidos
                            if (total > 0) {
                                alProgreso(((acumulado * 100) / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                conexion.disconnect()
                destino.exists() && destino.length() > 0
            }.getOrDefault(false)

            if (!ok) {
                alProgreso(-1)
                return@execute
            }

            val instalado = runCatching { instalar(context, destino) }.getOrDefault(false)
            if (!instalado) alProgreso(-1)
        }
    }

    /** Abre el diálogo de Android para instalar la versión nueva. */
    private fun instalar(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /** Abre los ajustes de "instalar apps desconocidas" por si hace falta. */
    fun abrirAjustesDeInstalacion(context: Context) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
