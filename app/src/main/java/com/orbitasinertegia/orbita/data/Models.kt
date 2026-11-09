package com.orbitasinertegia.orbita.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val role: String = "client",
    val active: Boolean? = true,
    @SerialName("is_super_admin") val isSuperAdmin: Boolean? = false,
) {
    val isAdmin get() = role == "admin" || isSuperAdmin == true
    val name get() = fullName?.takeIf { it.isNotBlank() } ?: email ?: "Sin nombre"
    val roleLabel get() = when {
        isSuperAdmin == true -> "Superadmin"
        role == "admin" -> "Administrador"
        role == "team" -> "Equipo"
        else -> "Cliente"
    }
}

@Serializable
data class ProjectLite(
    val id: String,
    val name: String,
    @SerialName("recording_mode") val recordingMode: String? = null,
    @SerialName("launch_date") val launchDate: String? = null,
    val status: String? = null,
)

@Serializable
data class Slot(
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
)

@Serializable
data class Participant(
    @SerialName("user_id") val userId: String,
    @SerialName("role_label") val roleLabel: String? = null,
    val required: Boolean = true,
    val response: String = "pendiente",
    @SerialName("response_note") val responseNote: String? = null,
    @SerialName("proposed_slots") val proposedSlots: List<Slot> = emptyList(),
    val attendance: String? = null,
    val user: Profile? = null,
)

@Serializable
data class Appointment(
    val id: String,
    @SerialName("project_id") val projectId: String? = null,
    val kind: String,
    val title: String,
    val reason: String? = null,
    val status: String,
    val modality: String? = null,
    val location: String? = null,
    @SerialName("meeting_url") val meetingUrl: String? = null,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val timezone: String = "America/Mexico_City",
    @SerialName("proposed_slots") val proposedSlots: List<Slot> = emptyList(),
    @SerialName("related_task_id") val relatedTaskId: String? = null,
    @SerialName("related_task_title") val relatedTaskTitle: String? = null,
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("coordinator_id") val coordinatorId: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("close_note") val closeNote: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val project: ProjectLite? = null,
    val requester: Profile? = null,
    val participants: List<Participant> = emptyList(),
)

@Serializable
data class Agreement(
    val id: String,
    val body: String,
    @SerialName("visible_to_client") val visibleToClient: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Notice(
    val id: String,
    val title: String,
    val message: String? = null,
    val type: String? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("appointment_id") val appointmentId: String? = null,
)

@Serializable
data class TaskLite(val id: String, val title: String)

@Serializable
data class Conflict(
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("appointment_title") val appointmentTitle: String,
    @SerialName("required_there") val requiredThere: Boolean = true,
)

@Serializable
data class LicenseStatus(val status: String? = null)

/** Invitado al programar una cita (pantalla "Programar cita"). */
data class Invitee(val user: Profile, val required: Boolean, val roleLabel: String?)

object Kinds {
    val all = listOf("grabacion", "reunion", "entrevista", "accesos")
    fun label(kind: String) = when (kind) {
        "grabacion" -> "Grabación"
        "reunion" -> "Reunión"
        "entrevista" -> "Entrevista"
        "accesos" -> "Accesos a cuentas"
        else -> kind
    }
    /** Grabación: presencial en el estudio. Lo demás (entrevistas, accesos a Meta, reuniones): virtual. */
    fun suggestedModality(kind: String) = if (kind == "grabacion") "presencial" else "virtual"
}
