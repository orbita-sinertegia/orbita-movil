package com.orbitasinertegia.orbita.avisos

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Repo
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Revisa cada 15 minutos (el mínimo de Android) si hay avisos nuevos y los muestra como notificación.
 * No necesita Firebase. Cuando se agreguen notificaciones push, este trabajo queda como respaldo.
 */
class AvisosWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val conSesion = withTimeoutOrNull(15_000) { runCatching { Repo.hasSession() }.getOrDefault(false) } ?: false
        if (!conSesion) return Result.success()

        val avisos = runCatching { Repo.notices() }.getOrElse { return Result.retry() }
        val prefs = applicationContext.getSharedPreferences("avisos", Context.MODE_PRIVATE)
        val ultimo = prefs.getLong("ultimo", -1L)
        val ahora = System.currentTimeMillis()
        fun ms(valor: String?) = Fechas.parse(valor)?.toInstant()?.toEpochMilli() ?: 0L

        // La primera vez solo se muestran los de la última media hora, para no llenar el teléfono de avisos viejos.
        val desde = if (ultimo < 0) ahora - 30 * 60 * 1000 else ultimo
        val nuevos = avisos.filter { !it.read && ms(it.createdAt) > desde }.sortedBy { ms(it.createdAt) }.takeLast(5)

        Notificaciones.crearCanales(applicationContext)
        for (aviso in nuevos) {
            val cita = aviso.appointmentId?.let { runCatching { Repo.appointment(it) }.getOrNull() }
            Notificaciones.mostrar(applicationContext, aviso, cita)
        }
        val masReciente = avisos.maxOfOrNull { ms(it.createdAt) } ?: 0L
        prefs.edit().putLong("ultimo", maxOf(masReciente, if (ultimo < 0) ahora else ultimo)).apply()
        return Result.success()
    }

    companion object {
        private val conInternet = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Programa la revisión periódica y hace una revisión inmediata. */
        fun programar(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                "avisos-periodicos", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AvisosWorker>(15, TimeUnit.MINUTES).setConstraints(conInternet).build(),
            )
            wm.enqueueUniqueWork(
                "avisos-ahora", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AvisosWorker>().setConstraints(conInternet).build(),
            )
        }

        fun cancelar(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("avisos-periodicos")
            context.getSharedPreferences("avisos", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
