package com.fire.mixi

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Deja el archivo donde la gente realmente lo busca: la Galería y la app de Música.
 * Sin esto, el archivo queda en una carpeta que nadie encuentra.
 */
object MediaSaver {

    /**
     * @return el nombre visible del archivo, o null si no se pudo guardar.
     */
    fun guardar(context: Context, archivo: File, esVideo: Boolean): String? {
        if (!archivo.exists() || archivo.length() == 0L) return null

        val nombre = archivo.name
        val guardado = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            guardarConMediaStore(context, archivo, esVideo, nombre)
        } else {
            guardarCarpetaPublica(context, archivo, esVideo)
        }

        if (guardado) archivo.delete()
        return if (guardado) nombre else null
    }

    /** Android 10 en adelante: MediaStore es la vía correcta y garantiza que aparezca. */
    private fun guardarConMediaStore(
        context: Context,
        archivo: File,
        esVideo: Boolean,
        nombre: String
    ): Boolean = runCatching {
        val resolutor = context.contentResolver
        val coleccion = if (esVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val subcarpeta = if (esVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC

        val valores = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
            put(MediaStore.MediaColumns.MIME_TYPE, if (esVideo) "video/mp4" else "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$subcarpeta/Mixi")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri: Uri = resolutor.insert(coleccion, valores) ?: return false

        resolutor.openOutputStream(uri)?.use { salida ->
            archivo.inputStream().use { entrada -> entrada.copyTo(salida, 64 * 1024) }
        } ?: return false

        valores.clear()
        valores.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolutor.update(uri, valores, null, null)
        true
    }.getOrDefault(false)

    /** Android 9 y anteriores: se copia a la carpeta pública y se avisa al sistema. */
    private fun guardarCarpetaPublica(context: Context, archivo: File, esVideo: Boolean): Boolean =
        runCatching {
            val tipo = if (esVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
            val destino = File(Environment.getExternalStoragePublicDirectory(tipo), "Mixi")
            if (!destino.exists()) destino.mkdirs()

            val copia = File(destino, archivo.name)
            archivo.inputStream().use { entrada ->
                copia.outputStream().use { salida -> entrada.copyTo(salida, 64 * 1024) }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(copia.absolutePath),
                null,
                null
            )
            copia.exists() && copia.length() > 0
        }.getOrDefault(false)
}
