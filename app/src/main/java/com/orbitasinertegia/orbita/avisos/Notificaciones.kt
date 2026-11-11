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

    /** Desde el trabajo periódico: calcula los botones con la cita descargada. */
    fun mostrar(context: Context, aviso: Notice, cita: Appointment?) {
        val mio = cita?.participants?.firstOrNull { it.userId == com.orbitasinertegia.orbita.data.Repo.myId() }
        mostrar(
            context, aviso.id, aviso.title, aviso.message, aviso.appointmentId,
            puedeAceptar = cita?.status == "programada" && mio != null && mio.response != "aceptada",
            meetingUrl = if (cita?.modality == "virtual") cita.meetingUrl else null,
            mapsUrl = if (cita?.modality == "presencial") mapsUrl(cita.address, cita.latitude, cita.longitude) else null,
        )
    }

    /** Desde una notificación push: los datos ya vienen calculados por el servidor. */
    fun mostrarPush(context: Context, datos: Map<String, String>) {
        val id = datos["id"] ?: return
        mostrar(
            context, id, datos["title"].orEmpty(), datos["message"], datos["appointment_id"]?.ifBlank { null },
            puedeAceptar = datos["puede_aceptar"] == "1",
            meetingUrl = datos["meeting_url"]?.ifBlank { null },
            mapsUrl = datos["maps_url"]?.ifBlank { null },
        )
    }

    fun mostrar(
        context: Context, avisoId: String, titulo: String, mensaje: String?, citaId: String?,
        puedeAceptar: Boolean, meetingUrl: String?, mapsUrl: String?,
    ) {
        if (!permitidas(context)) return
        val id = avisoId.hashCode()
        val esRecordatorio = titulo.contains("Recordatorio")
        val esInvitacion = titulo.contains("Nueva cita") || titulo.contains("Cita reprogramada") || titulo.contains("Cambio no aceptado")
        val canal = when {
            esRecordatorio -> CANAL_RECORDATORIOS
            citaId != null -> CANAL_CITAS
            else -> CANAL_GENERAL
        }
        val b = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF6A5ACD.toInt())
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setAutoCancel(true)
            // Si el mismo aviso llega por push y por la revisión periódica, suena una sola vez.
            .setOnlyAlertOnce(true)
            .setPriority(if (canal == CANAL_GENERAL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            // 1 y 2: tocar la notificación abre la app (o el detalle de la cita).
            .setContentIntent(abrirApp(context, citaId, id))

        // 3: botones.
        if (citaId != null && (esInvitacion || esRecordatorio)) {
            b.addAction(0, "Ver", abrirApp(context, citaId, id + 1))
        }
        if (esInvitacion && citaId != null && puedeAceptar) {
            b.addAction(0, "Aceptar", AccionReceiver.aceptar(context, citaId, avisoId, id))
        }
        if (esRecordatorio) {
            if (meetingUrl != null) b.addAction(0, "Unirse", abrirEnlace(context, meetingUrl, id + 2))
            else if (mapsUrl != null) b.addAction(0, "Cómo llegar", abrirEnlace(context, mapsUrl, id + 2))
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
