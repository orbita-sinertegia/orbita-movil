package com.orbitasinertegia.orbita.avisos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.orbitasinertegia.orbita.MainActivity
import com.orbitasinertegia.orbita.R
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Notice
import com.orbitasinertegia.orbita.data.mapsUrl

/**
 * Las tres clases de notificación de la app:
 * 1. Básica: título y mensaje (asistencia y avisos generales). Al tocarla abre la app.
 * 2. De toque: al tocarla abre directamente el detalle de la cita.
 * 3. Con botones: invitaciones y reprogramaciones (Ver / Aceptar) y recordatorios (Ver / Unirse o Cómo llegar).
 */
object Notificaciones {
    const val CANAL_CITAS = "citas"
    const val CANAL_RECORDATORIOS = "recordatorios"
    const val CANAL_GENERAL = "general"
    const val EXTRA_CITA = "cita_id"

    fun crearCanales(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CANAL_CITAS, "Citas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Invitaciones, cambios de horario y cancelaciones"
                },
                NotificationChannel(CANAL_RECORDATORIOS, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos 24 horas y 1 hora antes de cada cita"
                },
                NotificationChannel(CANAL_GENERAL, "Avisos generales", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Asistencia y otros avisos de Órbita"
                },
            ),
        )
    }

    fun permitidas(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun idDe(aviso: Notice) = aviso.id.hashCode()

    /** Abre la app; si hay cita, en su detalle. */
    fun abrirApp(context: Context, citaId: String?, codigo: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (citaId != null) putExtra(EXTRA_CITA, citaId)
        }
        return PendingIntent.getActivity(context, codigo, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun abrirEnlace(context: Context, url: String, codigo: Int): PendingIntent =
        PendingIntent.getActivity(
            context, codigo, Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun mostrar(context: Context, aviso: Notice, cita: Appointment?) {
        if (!permitidas(context)) return
        val id = idDe(aviso)
        val esRecordatorio = aviso.title.contains("Recordatorio")
        val esInvitacion = aviso.title.contains("Nueva cita") || aviso.title.contains("Cita reprogramada") ||
            aviso.title.contains("Cambio no aceptado")
        val canal = when {
            esRecordatorio -> CANAL_RECORDATORIOS
            aviso.appointmentId != null -> CANAL_CITAS
            else -> CANAL_GENERAL
        }
        val b = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF6A5ACD.toInt())
            .setContentTitle(aviso.title)
            .setContentText(aviso.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(aviso.message))
            .setAutoCancel(true)
            .setPriority(if (canal == CANAL_GENERAL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            // 1 y 2: tocar la notificación abre la app (o el detalle de la cita).
            .setContentIntent(abrirApp(context, aviso.appointmentId, id))

        // 3: botones.
        if (aviso.appointmentId != null && (esInvitacion || esRecordatorio)) {
            b.addAction(0, "Ver", abrirApp(context, aviso.appointmentId, id + 1))
        }
        val mio = cita?.participants?.firstOrNull { it.userId == com.orbitasinertegia.orbita.data.Repo.myId() }
        if (esInvitacion && aviso.appointmentId != null && cita?.status == "programada" && mio != null && mio.response != "aceptada") {
            b.addAction(0, "Aceptar", AccionReceiver.aceptar(context, aviso.appointmentId, aviso.id, id))
        }
        if (esRecordatorio && cita != null) {
            if (cita.modality == "virtual" && cita.meetingUrl != null) {
                b.addAction(0, "Unirse", abrirEnlace(context, cita.meetingUrl, id + 2))
            } else mapsUrl(cita.address, cita.latitude, cita.longitude)?.let {
                b.addAction(0, "Cómo llegar", abrirEnlace(context, it, id + 2))
            }
        }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build())
        } catch (_: SecurityException) {
            // Se quitó el permiso mientras tanto.
        }
    }

    fun confirmar(context: Context, id: Int, titulo: String, texto: String, citaId: String?) {
        if (!permitidas(context)) return
        val n = NotificationCompat.Builder(context, CANAL_CITAS)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF6A5ACD.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
            .setContentIntent(abrirApp(context, citaId, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
