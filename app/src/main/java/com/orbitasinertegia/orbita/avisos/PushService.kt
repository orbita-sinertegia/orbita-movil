package com.orbitasinertegia.orbita.avisos

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Recibe las notificaciones push de Firebase y registra el teléfono con la cuenta. */
class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val datos = mensaje.data
        if (datos["id"] == null) return
        Notificaciones.crearCanales(this)
        Notificaciones.mostrarPush(this, datos)
        // Para que la revisión periódica no lo vuelva a mostrar.
        val creado = Fechas.parse(datos["created_at"])?.toInstant()?.toEpochMilli() ?: return
        val prefs = getSharedPreferences("avisos", Context.MODE_PRIVATE)
        if (creado > prefs.getLong("ultimo", -1L)) prefs.edit().putLong("ultimo", creado).apply()
    }

    override fun onNewToken(token: String) {
        scope.launch {
            val conSesion = withTimeoutOrNull(15_000) { runCatching { Repo.hasSession() }.getOrDefault(false) } ?: false
            if (conSesion) runCatching { Repo.registerDeviceToken(token) }
        }
    }

    companion object {
        /** Token de Firebase de este teléfono (null si no hay Google Play o falla). */
        suspend fun token(): String? = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { t ->
                    cont.resume(if (t.isSuccessful) t.result else null)
                }
            }
        }

        suspend fun registrar() {
            token()?.let { runCatching { Repo.registerDeviceToken(it) } }
        }

        suspend fun quitar() {
            token()?.let { runCatching { Repo.unregisterDeviceToken(it) } }
        }
    }
}
