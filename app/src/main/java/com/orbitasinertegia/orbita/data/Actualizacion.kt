package com.orbitasinertegia.orbita.data

import android.content.Context
import com.orbitasinertegia.orbita.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Revisa si hay una versión nueva publicada en GitHub (Releases de orbita-movil).
 * Fuera de Google Play, Android no instala actualizaciones solo: la app avisa y la persona confirma.
 */
object Actualizacion {
    const val DESCARGA = "https://github.com/orbita-sinertegia/orbita-movil/releases/latest/download/orbita-citas.apk"
    private const val ULTIMA = "https://api.github.com/repos/orbita-sinertegia/orbita-movil/releases/latest"

    /** Devuelve la versión nueva (por ejemplo "1.4.0") o null si ya está al día o no hay internet. */
    suspend fun versionNueva(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("actualizacion", Context.MODE_PRIVATE)
        // Como mucho una revisión cada 6 horas.
        val ahora = System.currentTimeMillis()
        if (ahora - prefs.getLong("revisado", 0L) < 6 * 60 * 60 * 1000) return@withContext prefs.getString("nueva", null)?.takeIf { esMayor(it, BuildConfig.VERSION_NAME) }
        val etiqueta = runCatching {
            val con = (URL(ULTIMA).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            con.inputStream.bufferedReader().use { it.readText() }.let {
                Json.parseToJsonElement(it).jsonObject["tag_name"]?.jsonPrimitive?.content
            }
        }.getOrNull()?.removePrefix("v") ?: return@withContext null
        prefs.edit().putLong("revisado", ahora).putString("nueva", etiqueta).apply()
        etiqueta.takeIf { esMayor(it, BuildConfig.VERSION_NAME) }
    }

    fun esMayor(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
