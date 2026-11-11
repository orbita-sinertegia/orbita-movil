package com.orbitasinertegia.orbita.avisos

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Botón "Aceptar" de la notificación: confirma la asistencia sin abrir la app. */
class AccionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACCION_ACEPTAR) return
        val citaId = intent.getStringExtra(EXTRA_CITA) ?: return
        val avisoId = intent.getStringExtra(EXTRA_AVISO)
        val notificacion = intent.getIntExtra(EXTRA_NOTIFICACION, 0)
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val listo = withTimeoutOrNull(20_000) {
                    runCatching {
                        Repo.hasSession()
                        Repo.respond(citaId, "aceptada", null, null)
                        avisoId?.let { Repo.markRead(it) }
                    }
                }
                when {
                    listo == null -> Notificaciones.confirmar(context, notificacion, "Sin conexión", "Abre la app para confirmar tu asistencia.", citaId)
                    listo.isSuccess -> Notificaciones.confirmar(context, notificacion, "✅ Asistencia confirmada", "Le avisamos al coordinador.", citaId)
                    else -> Notificaciones.confirmar(context, notificacion, "No se pudo confirmar", listo.exceptionOrNull()!!.friendly(), citaId)
                }
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        const val ACCION_ACEPTAR = "com.orbitasinertegia.orbita.ACEPTAR_CITA"
        private const val EXTRA_CITA = "cita_id"
        private const val EXTRA_AVISO = "aviso_id"
        private const val EXTRA_NOTIFICACION = "notificacion_id"

        fun aceptar(context: Context, citaId: String, avisoId: String, notificacion: Int): PendingIntent {
            val intent = Intent(context, AccionReceiver::class.java).apply {
                action = ACCION_ACEPTAR
                putExtra(EXTRA_CITA, citaId)
                putExtra(EXTRA_AVISO, avisoId)
                putExtra(EXTRA_NOTIFICACION, notificacion)
            }
            return PendingIntent.getBroadcast(
                context, notificacion + 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
