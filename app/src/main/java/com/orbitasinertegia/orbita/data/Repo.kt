package com.orbitasinertegia.orbita.data

import com.orbitasinertegia.orbita.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlin.time.Duration.Companion.minutes
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Acceso a Supabase. Las reglas (quién ve y quién cambia qué) viven en la base de datos. */
object Repo {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        defaultSerializer = KotlinXSerializer(json)
    }

    private const val APPOINTMENT_COLUMNS =
        "*, project:projects(id,name,recording_mode,launch_date,status), " +
            "requester:users!appointments_requested_by_fkey(id,full_name,email,role), " +
            "participants:appointment_participants(*, user:users(id,full_name,email,role,is_super_admin))"

    // Sesión ---------------------------------------------------------------------------

    suspend fun hasSession(): Boolean {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        return status is SessionStatus.Authenticated
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Google: el token lo da el selector de cuentas del teléfono; Supabase valida token y nonce. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut() = runCatching { client.auth.signOut() }

    suspend fun resetPassword(email: String) =
        client.auth.resetPasswordForEmail(email.trim(), redirectUrl = BuildConfig.WEB_URL)

    fun myId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun myProfile(): Profile? {
        val id = myId() ?: return null
        return client.from("users").select(Columns.list("id", "email", "full_name", "role", "active", "is_super_admin")) {
            filter { eq("id", id) }
        }.decodeSingleOrNull<Profile>()
    }

    suspend fun licenseSuspended(): Boolean = runCatching {
        client.from("app_license").select(Columns.list("status")) { filter { eq("id", 1) } }
            .decodeSingleOrNull<LicenseStatus>()?.status == "suspended"
    }.getOrDefault(false)

    // Lecturas -------------------------------------------------------------------------

    suspend fun appointments(): List<Appointment> =
        client.from("appointments").select(Columns.raw(APPOINTMENT_COLUMNS)) {
            order("starts_at", Order.ASCENDING, nullsFirst = false)
            limit(400)
        }.decodeList()

    suspend fun appointment(id: String): Appointment? =
        client.from("appointments").select(Columns.raw(APPOINTMENT_COLUMNS)) {
            filter { eq("id", id) }
        }.decodeSingleOrNull()

    suspend fun agreements(appointmentId: String): List<Agreement> =
        client.from("appointment_agreements").select {
            filter { eq("appointment_id", appointmentId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun projects(): List<ProjectLite> =
        client.from("projects").select(Columns.list("id", "name", "recording_mode", "launch_date", "status")) {
            filter { neq("status", "completado") }
            order("name", Order.ASCENDING)
        }.decodeList()

    suspend fun activeUsers(): List<Profile> =
        client.from("users").select(Columns.list("id", "email", "full_name", "role", "active", "is_super_admin")) {
            order("full_name", Order.ASCENDING)
        }.decodeList<Profile>().filter { it.active != false }

    suspend fun projectTasks(projectId: String): List<TaskLite> =
        client.from("tasks").select(Columns.list("id", "title")) {
            filter { eq("project_id", projectId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList()

    suspend fun kinds(): List<AppointmentKind> =
        client.from("appointment_kinds").select {
            order("sort_order", Order.ASCENDING)
        }.decodeList()

    suspend fun addKind(label: String, defaultModality: String): String =
        rpc("add_appointment_kind", buildJsonObject {
            put("p_label", label); put("p_default_modality", defaultModality)
        }).decodeAs<String>()

    suspend fun resolveChange(appointmentId: String, userId: String, accept: Boolean, slotIndex: Int, note: String?) {
        rpc("resolve_change_request", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_user_id", userId); put("p_accept", accept)
            put("p_slot_index", slotIndex); put("p_note", note)
        })
    }

    // Evidencia (bucket privado) ------------------------------------------------------

    private const val EVIDENCIA = "appointment-evidence"

    suspend fun files(appointmentId: String): List<AppointmentFile> =
        client.from("appointment_files").select {
            filter { eq("appointment_id", appointmentId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    suspend fun uploadEvidence(appointmentId: String, bytes: ByteArray, fileName: String, mime: String, visibleToClient: Boolean) {
        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heic" -> "heic"; "application/pdf" -> "pdf"; else -> "jpg" }
        val path = "$appointmentId/${java.util.UUID.randomUUID()}.$ext"
        client.storage.from(EVIDENCIA).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        rpc("register_appointment_file", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_path", path); put("p_file_name", fileName)
            put("p_mime_type", mime); put("p_size_bytes", bytes.size.toLong()); put("p_visible_to_client", visibleToClient)
        })
    }

    suspend fun downloadEvidence(path: String): ByteArray = client.storage.from(EVIDENCIA).downloadAuthenticated(path)

    suspend fun evidenceUrl(path: String): String = client.storage.from(EVIDENCIA).createSignedUrl(path, 10.minutes)

    suspend fun deleteEvidence(fileId: String) {
        val path = rpc("delete_appointment_file", buildJsonObject { put("p_file_id", fileId) }).decodeAs<String>()
        runCatching { client.storage.from(EVIDENCIA).delete(path) }
    }

    suspend fun notices(): List<Notice> {
        val id = myId() ?: return emptyList()
        return client.from("notifications").select(Columns.list("id", "title", "message", "type", "read", "created_at", "appointment_id")) {
            filter { eq("user_id", id) }
            order("created_at", Order.DESCENDING)
            limit(60)
        }.decodeList()
    }

    suspend fun markRead(noticeId: String) {
        client.from("notifications").update({ set("read", true) }) { filter { eq("id", noticeId) } }
    }

    suspend fun markAllRead() {
        val id = myId() ?: return
        client.from("notifications").update({ set("read", true) }) {
            filter { eq("user_id", id); eq("read", false) }
        }
    }

    // Acciones (funciones de la base) --------------------------------------------------

    private fun slotsJson(slots: List<Slot>): JsonArray = buildJsonArray {
        slots.forEach { addJsonObject { put("starts_at", it.startsAt); put("ends_at", it.endsAt) } }
    }

    private suspend fun rpc(name: String, params: JsonObject) = client.postgrest.rpc(name, params)

    suspend fun requestAppointment(projectId: String?, kind: String, reason: String, slots: List<Slot>): String =
        rpc("request_appointment", buildJsonObject {
            put("p_project_id", projectId)
            put("p_kind", kind)
            put("p_reason", reason)
            put("p_slots", slotsJson(slots))
        }).decodeAs<String>()

    suspend fun scheduleAppointment(
        appointmentId: String?, projectId: String?, kind: String, title: String, slot: Slot,
        modality: String, location: String?, meetingUrl: String?, invitees: List<Invitee>, relatedTaskId: String?,
    ): String = rpc("schedule_appointment", buildJsonObject {
        put("p_appointment_id", appointmentId)
        put("p_project_id", projectId)
        put("p_kind", kind)
        put("p_title", title)
        put("p_starts", slot.startsAt)
        put("p_ends", slot.endsAt)
        put("p_modality", modality)
        put("p_location", location)
        put("p_meeting_url", meetingUrl)
        putJsonArray("p_participants") {
            invitees.forEach {
                addJsonObject {
                    put("user_id", it.user.id)
                    put("required", it.required)
                    put("role_label", it.roleLabel)
                }
            }
        }
        put("p_related_task_id", relatedTaskId)
    }).decodeAs<String>()

    suspend fun conflicts(slot: Slot, userIds: List<String>, excludeAppointment: String?): List<Conflict> {
        if (userIds.isEmpty()) return emptyList()
        return rpc("check_appointment_conflicts", buildJsonObject {
            put("p_starts", slot.startsAt)
            put("p_ends", slot.endsAt)
            putJsonArray("p_user_ids") { userIds.forEach { add(it) } }
            put("p_exclude_appointment", excludeAppointment)
        }).decodeList()
    }

    suspend fun respond(appointmentId: String, response: String, note: String?, slots: List<Slot>?) {
        rpc("respond_appointment", buildJsonObject {
            put("p_appointment_id", appointmentId)
            put("p_response", response)
            put("p_note", note)
            put("p_slots", slots?.let { slotsJson(it) } ?: JsonNull)
        })
    }

    suspend fun cancel(appointmentId: String, reason: String) {
        rpc("cancel_appointment", buildJsonObject { put("p_appointment_id", appointmentId); put("p_reason", reason) })
    }

    suspend fun start(appointmentId: String) {
        rpc("start_appointment", buildJsonObject { put("p_appointment_id", appointmentId) })
    }

    suspend fun setAttendance(appointmentId: String, userId: String, attendance: String) {
        rpc("set_appointment_attendance", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_user_id", userId); put("p_attendance", attendance)
        })
    }

    suspend fun addAgreement(appointmentId: String, body: String, visibleToClient: Boolean) {
        rpc("add_appointment_agreement", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_body", body); put("p_visible_to_client", visibleToClient)
        })
    }

    suspend fun deleteAgreement(agreementId: String) {
        rpc("delete_appointment_agreement", buildJsonObject { put("p_agreement_id", agreementId) })
    }

    suspend fun close(appointmentId: String, status: String, note: String?) {
        rpc("close_appointment", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_status", status); put("p_note", note)
        })
    }
}

/** Mensaje entendible para la persona a partir de un error de Supabase. */
fun Throwable.friendly(): String = when (this) {
    is AuthRestException -> when {
        (errorDescription + error).contains("saving new user", ignoreCase = true) ->
            "Esta cuenta no tiene una invitación. Pídele a tu administrador un enlace de invitación y regístrate desde ahí."
        error.contains("invalid", ignoreCase = true) -> "Correo o contraseña incorrectos."
        else -> errorDescription.ifBlank { error }
    }
    is RestException -> error.removePrefix("CONFLICTO: ").ifBlank { "No se pudo completar la acción." }
    is HttpRequestException -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    else -> message ?: "Ocurrió un error inesperado."
}
