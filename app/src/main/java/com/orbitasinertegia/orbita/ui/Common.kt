package com.orbitasinertegia.orbita.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Slot
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Crema
import com.orbitasinertegia.orbita.ui.theme.Exito
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.Lila
import com.orbitasinertegia.orbita.ui.theme.LilaClaro
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.Peligro
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

data class Estado(val texto: String, val fondo: Color, val color: Color)

/** Estado que ve cada persona: para un invitado, depende de su respuesta. */
fun estadoDe(cita: Appointment, myId: String?, isAdmin: Boolean): Estado {
    val pendiente = Estado("Pendiente de confirmar", Crema, Color(0xFF8A4B00))
    val confirmada = Estado("Confirmada", VioletaClaro, Indigo)
    val ajuste = Estado("Requiere ajuste", Color(0xFFFCE4E8), Peligro)
    return when (cita.status) {
        "solicitada" -> Estado("Solicitada", LilaClaro, Color(0xFF774D88))
        "en_curso" -> Estado("En curso", Ambar, Noche)
        "realizada" -> Estado("Realizada", Color(0xFFE4F7F0), Color(0xFF0C8A66))
        "incompleta" -> Estado("Incompleta", Color(0xFFFCE4E8), Peligro)
        "cancelada" -> Estado("Cancelada", Color(0xFFECEBF2), TextoSuave)
        else -> {
            val otros = cita.participants.filter { it.userId != cita.coordinatorId }
            val mio = cita.participants.firstOrNull { it.userId == myId }
            when {
                isAdmin && otros.any { it.required && it.response in listOf("rechazada", "cambio_solicitado") } -> ajuste
                isAdmin && otros.any { it.response == "pendiente" } -> pendiente
                isAdmin -> confirmada
                mio?.response == "aceptada" -> confirmada
                mio?.response == "pendiente" -> pendiente
                mio?.response == "rechazada" -> Estado("Rechazaste", Color(0xFFECEBF2), TextoSuave)
                mio?.response == "cambio_solicitado" -> ajuste
                else -> confirmada
            }
        }
    }
}

fun respuestaTexto(response: String) = when (response) {
    "aceptada" -> "Aceptó"
    "rechazada" -> "Rechazó"
    "cambio_solicitado" -> "Pide cambio"
    else -> "Pendiente"
}

fun respuestaColor(response: String) = when (response) {
    "aceptada" -> Violeta
    "rechazada" -> Peligro
    "cambio_solicitado" -> Color(0xFFB4720C)
    else -> TextoSuave
}

@Composable
fun Chip(estado: Estado) {
    Text(
        estado.texto,
        color = estado.color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(estado.fondo, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Iniciales(nombre: String, fondo: Color = VioletaClaro, color: Color = Indigo) {
    val iniciales = nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(Modifier.size(40.dp).background(fondo, CircleShape), contentAlignment = Alignment.Center) {
        Text(iniciales.ifBlank { "?" }, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun Titulo(texto: String) {
    Text(texto, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun Etiqueta(texto: String) {
    Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun BarraOscura(titulo: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(Noche).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = Color.White) }
            Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        Box(Modifier.padding(horizontal = 12.dp)) { extra() }
    }
}

@Composable
fun BotonPrincipal(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Violeta, contentColor = Color.White),
    ) { Text(texto, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp), shape = RoundedCornerShape(26.dp)) {
        Text(texto, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun Aviso(texto: String, fondo: Color = VioletaClaro, color: Color = Indigo) {
    Text(
        texto,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().background(fondo, RoundedCornerShape(12.dp)).padding(12.dp),
    )
}

@Composable
fun ErrorTexto(texto: String?) {
    if (texto != null) Aviso(texto, Color(0xFFFCE4E8), Peligro)
}

/** Diálogo para escribir un motivo (rechazar, cancelar, cerrar incompleta). */
@Composable
fun DialogoTexto(
    titulo: String, etiqueta: String, confirmar: String, minimo: Int = 3,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                texto, { texto = it }, label = { Text(etiqueta) }, minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(texto.trim()) }, enabled = texto.trim().length >= minimo) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Diálogo para escribir un acuerdo y decidir si el cliente lo ve. */
@Composable
fun DialogoAcuerdo(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var texto by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo acuerdo") },
        text = {
            Column {
                OutlinedTextField(
                    texto, { texto = it }, label = { Text("Acuerdo") }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible para el cliente", modifier = Modifier.weight(1f))
                    Switch(visible, { visible = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(texto.trim(), visible) }, enabled = texto.trim().length >= 2) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Elegir un horario en tres pasos: día, hora de inicio y duración.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorHorario(onDismiss: () -> Unit, onSlot: (Slot) -> Unit) {
    var paso by remember { mutableStateOf(0) }
    var dia by remember { mutableStateOf<LocalDate?>(null) }
    var inicio by remember { mutableStateOf<LocalTime?>(null) }
    val hoyUtc = remember { Fechas.hoy().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

    when (paso) {
        0 -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = hoyUtc,
                selectableDates = object : androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= hoyUtc
                },
            )
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let {
                            dia = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            paso = 1
                        }
                    }) { Text("Siguiente") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            ) { DatePicker(state, title = { Text("Elige el día", Modifier.padding(16.dp)) }) }
        }
        1 -> {
            val state = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Hora de inicio") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = { inicio = LocalTime.of(state.hour, state.minute); paso = 2 }) { Text("Siguiente") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            )
        }
        else -> {
            val opciones = listOf(30L to "30 min", 60L to "1 hora", 90L to "1 h 30 min", 120L to "2 horas", 180L to "3 horas", 240L to "4 horas")
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("¿Cuánto dura?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        opciones.forEach { (minutos, texto) ->
                            OutlinedButton(
                                onClick = {
                                    val d = dia!!
                                    val i = inicio!!
                                    val fin = java.time.ZonedDateTime.of(d, i, Fechas.ZONA).plusMinutes(minutos)
                                    onSlot(Slot(Fechas.iso(d, i), fin.toOffsetDateTime().toString()))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(texto) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            )
        }
    }
}

@Composable
fun FilaHorario(slot: Slot, onRemove: (() -> Unit)?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fechas.rango(slot), modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Quitar horario", tint = TextoSuave)
            }
        }
    }
}

