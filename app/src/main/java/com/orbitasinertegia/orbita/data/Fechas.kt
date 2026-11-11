package com.orbitasinertegia.orbita.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Todas las horas se muestran en la zona de la agencia. */
object Fechas {
    val ZONA: ZoneId = ZoneId.of("America/Mexico_City")
    private val es = Locale.forLanguageTag("es-MX")
    private val hora = DateTimeFormatter.ofPattern("HH:mm", es)
    private val diaLargo = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es)
    private val diaCorto = DateTimeFormatter.ofPattern("EEE d MMM", es)
    private val diaSemana = DateTimeFormatter.ofPattern("EEE", es)

    fun parse(value: String?): ZonedDateTime? = value?.let {
        runCatching { OffsetDateTime.parse(it.replace(' ', 'T')).atZoneSameInstant(ZONA) }.getOrNull()
    }

    fun iso(date: LocalDate, time: LocalTime): String =
        ZonedDateTime.of(date, time, ZONA).toOffsetDateTime().toString()

    fun hora(z: ZonedDateTime) = z.format(hora)
    fun diaLargo(z: ZonedDateTime) = z.format(diaLargo).replaceFirstChar { it.uppercase() }
    fun diaLargo(d: LocalDate) = d.format(diaLargo).replaceFirstChar { it.uppercase() }
    fun diaCorto(z: ZonedDateTime) = z.format(diaCorto).replace(".", "").replaceFirstChar { it.uppercase() }
    fun diaSemana(d: LocalDate) = d.format(diaSemana).replace(".", "").replaceFirstChar { it.uppercase() }

    fun rango(slot: Slot): String {
        val a = parse(slot.startsAt) ?: return ""
        val b = parse(slot.endsAt) ?: return ""
        return "${diaCorto(a)} · ${hora(a)} – ${hora(b)}"
    }

    fun hoy(): LocalDate = LocalDate.now(ZONA)
}

/** "hace 5 min", "hace 1 h", "ayer" o la fecha corta. */
fun Fechas.hace(value: String?): String {
    val t = parse(value) ?: return ""
    val minutos = java.time.Duration.between(t, ZonedDateTime.now(ZONA)).toMinutes()
    return when {
        minutos < 1 -> "ahora"
        minutos < 60 -> "hace $minutos min"
        minutos < 24 * 60 -> "hace ${minutos / 60} h"
        t.toLocalDate() == hoy().minusDays(1) -> "ayer"
        else -> diaCorto(t).substringAfter(' ')
    }
}
