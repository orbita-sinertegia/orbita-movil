package com.orbitasinertegia.orbita.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.orbitasinertegia.orbita.data.Agreement
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.Slot
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BarraOscura
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.BotonSecundario
import com.orbitasinertegia.orbita.ui.Chip
import com.orbitasinertegia.orbita.ui.DialogoTexto
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.FilaHorario
import com.orbitasinertegia.orbita.ui.InicialesRol
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.SelectorHorario
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.Tarjeta
import com.orbitasinertegia.orbita.ui.Titulo
import com.orbitasinertegia.orbita.ui.estadoDe
import com.orbitasinertegia.orbita.ui.respuestaColor
import com.orbitasinertegia.orbita.ui.respuestaTexto
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import kotlinx.coroutines.launch

@Composable
fun DetalleScreen(sesion: Sesion, id: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var cita by remember { mutableStateOf<Appointment?>(null) }
    var acuerdos by remember { mutableStateOf<List<Agreement>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var dialogo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recarga) {
        runCatching { Repo.appointment(id) }
            .onSuccess { cita = it; error = if (it == null) "No encontramos esta cita o ya no tienes acceso." else null }
            .onFailure { error = it.friendly() }
        if (cita != null && cita!!.status in listOf("en_curso", "realizada", "incompleta")) {
            acuerdos = runCatching { Repo.agreements(id) }.getOrDefault(emptyList())
        }
        cargando = false
    }

    fun accion(bloque: suspend () -> Unit) {
        ocupado = true; error = null
        scope.launch {
            runCatching { bloque() }.onFailure { error = it.friendly() }.onSuccess { recarga++ }
            ocupado = false
        }
    }

    val c = cita
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BarraOscura(c?.title ?: "Cita", onBack = { sesion.atras() }) {
            if (c != null) Column {
                Chip(estadoDe(c, sesion.myId, sesion.isAdmin))
                Spacer(Modifier.height(6.dp))
                Text(
                    listOfNotNull(Kinds.label(c.kind), c.project?.name ?: if (c.projectId == null) "Interna" else null).joinToString(" · "),
                    color = Color(0xFFC9C3E3), fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Violeta) }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorTexto(error)
            if (c == null) return@Column

            Tarjeta {
                val inicio = Fechas.parse(c.startsAt)
                val fin = Fechas.parse(c.endsAt)
                if (inicio != null && fin != null) {
                    Dato(Icons.Outlined.CalendarMonth, Fechas.diaLargo(inicio), "${Fechas.hora(inicio)} – ${Fechas.hora(fin)} · ${c.timezone}")
                } else {
                    Dato(Icons.Outlined.CalendarMonth, "Sin fecha todavía", "Un administrador elegirá el horario.")
                }
                when (c.modality) {
                    "presencial" -> Dato(Icons.Outlined.Place, "Presencial", c.location ?: "Lugar por definir")
                    "virtual" -> Dato(Icons.Outlined.Videocam, "Virtual", plataforma(c.meetingUrl)?.let { "Por $it" } ?: "Enlace por definir")
                }
                if (c.modality == "virtual" && c.meetingUrl != null && c.status in listOf("programada", "en_curso")) {
                    Spacer(Modifier.height(4.dp))
                    BotonPrincipal(
                        "Unirse por ${plataforma(c.meetingUrl)}",
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, c.meetingUrl.toUri())) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (c.relatedTaskTitle != null) Dato(Icons.Outlined.TaskAlt, c.relatedTaskTitle, "Tarea relacionada")
            }

            if (!c.reason.isNullOrBlank()) Tarjeta { Titulo("Motivo"); Text(c.reason, fontSize = 15.sp) }

            if (c.status == "solicitada" && c.proposedSlots.isNotEmpty()) Tarjeta {
                Titulo("Horarios propuestos")
                c.proposedSlots.forEach { Text("• " + Fechas.rango(it), fontSize = 15.sp) }
                c.requester?.let { Text("Pidió: ${it.name}", color = TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }

            if (c.participants.isNotEmpty()) Tarjeta {
                Titulo("Participantes")
                c.participants.sortedByDescending { it.userId == c.coordinatorId }.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        InicialesRol(p.user?.name ?: "?", p.user?.role)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.user?.name ?: "Participante", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(p.roleLabel, if (p.required) "Obligatorio" else "Opcional").joinToString(" · "),
                                color = TextoSuave, fontSize = 13.sp,
                            )
                            if (!p.responseNote.isNullOrBlank()) Text("“${p.responseNote}”", color = TextoSuave, fontSize = 13.sp)
                            p.proposedSlots.forEach { Text("Propone: " + Fechas.rango(it), color = TextoSuave, fontSize = 13.sp) }
                        }
                        val texto = when (p.attendance) { "presente" -> "Asistió"; "ausente" -> "Faltó"; else -> respuestaTexto(p.response) }
                        Text(texto, color = respuestaColor(p.response), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            if (acuerdos.isNotEmpty()) Tarjeta {
                Titulo("Acuerdos")
                acuerdos.forEach { a ->
                    Text("• " + a.body, fontSize = 15.sp)
                    if (sesion.perfil?.role != "client") {
                        Text(if (a.visibleToClient) "Visible para el cliente" else "Interno", color = TextoSuave, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            c.cancelReason?.let { Tarjeta { Titulo("Motivo de cancelación"); Text(it) } }
            c.closeNote?.let { Tarjeta { Titulo("Nota de cierre"); Text(it) } }
        }

        if (c != null) Acciones(c, sesion, ocupado, onAccion = { dialogo = it }, onGo = { sesion.ir(it) }, onAceptar = {
            accion { Repo.respond(c.id, "aceptada", null, null) }
        }, onIniciar = {
            accion { Repo.start(c.id); sesion.ir(Ruta.EnCurso(c.id)) }
        })
    }

    val actual = cita
    when (dialogo) {
        "rechazar" -> DialogoTexto("Rechazar cita", "¿Por qué no podrás asistir?", "Rechazar", onDismiss = { dialogo = null }) { motivo ->
            dialogo = null; accion { Repo.respond(id, "rechazada", motivo, null) }
        }
        "cancelar" -> DialogoTexto(
            if (actual?.status == "solicitada") "Cancelar solicitud" else "Cancelar cita",
            "Motivo (lo verán los invitados)", "Cancelar cita", onDismiss = { dialogo = null },
        ) { motivo -> dialogo = null; accion { Repo.cancel(id, motivo) } }
        "proponer" -> ProponerHorarios(onDismiss = { dialogo = null }) { slots, nota ->
            dialogo = null; accion { Repo.respond(id, "cambio_solicitado", nota, slots) }
        }
    }
}

@Composable
private fun Dato(icono: ImageVector, titulo: String, detalle: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icono, null, tint = Violeta)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(detalle, color = TextoSuave, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Acciones(
    c: Appointment, sesion: Sesion, ocupado: Boolean,
    onAccion: (String) -> Unit, onGo: (Ruta) -> Unit, onAceptar: () -> Unit, onIniciar: () -> Unit,
) {
    val mio = c.participants.firstOrNull { it.userId == sesion.myId }
    val soyInvitado = mio != null && c.coordinatorId != sesion.myId
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            c.status == "programada" && soyInvitado -> {
                Text("¿Asistirás?", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonSecundario("Rechazar", { onAccion("rechazar") }, Modifier.weight(1f), enabled = !ocupado && mio.response != "rechazada")
                    BotonPrincipal(if (mio.response == "aceptada") "Aceptada" else "Aceptar", onAceptar, Modifier.weight(1f), enabled = !ocupado && mio.response != "aceptada")
                }
                TextButton(onClick = { onAccion("proponer") }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                    Text("Proponer otro horario", color = Violeta, fontWeight = FontWeight.SemiBold)
                }
            }
            sesion.isAdmin && c.status == "solicitada" -> {
                BotonPrincipal("Programar", { onGo(Ruta.Programar(c.id)) }, Modifier.fillMaxWidth(), enabled = !ocupado)
                TextButton(onClick = { onAccion("cancelar") }, modifier = Modifier.fillMaxWidth()) { Text("Rechazar solicitud", color = TextoSuave) }
            }
            sesion.isAdmin && c.status == "programada" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BotonSecundario("Reprogramar", { onGo(Ruta.Programar(c.id)) }, Modifier.weight(1f), enabled = !ocupado)
                    BotonPrincipal("Iniciar", onIniciar, Modifier.weight(1f), enabled = !ocupado)
                }
                c.participants.firstOrNull { it.response == "cambio_solicitado" }?.let { p ->
                    TextButton(onClick = { onGo(Ruta.Cambio(c.id, p.userId)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Revisar el cambio que pide ${p.user?.name ?: "un invitado"}", color = Violeta, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { onAccion("cancelar") }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar cita", color = TextoSuave) }
            }
            sesion.isAdmin && c.status == "en_curso" -> {
                BotonPrincipal("Continuar cita en curso", { onGo(Ruta.EnCurso(c.id)) }, Modifier.fillMaxWidth())
            }
            c.status == "solicitada" && c.requestedBy == sesion.myId -> {
                Text("La solicitud no reserva el horario; un administrador la confirma.", color = TextoSuave, fontSize = 13.sp)
                BotonSecundario("Cancelar solicitud", { onAccion("cancelar") }, Modifier.fillMaxWidth(), enabled = !ocupado)
            }
        }
    }
}

/** Pedir otro horario: hoja inferior con 1 a 3 opciones y una nota. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProponerHorarios(onDismiss: () -> Unit, onConfirm: (List<Slot>, String?) -> Unit) {
    val horarios = remember { mutableStateListOf<Slot>() }
    var eligiendo by remember { mutableStateOf(false) }
    var nota by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Proponer otro horario", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Tus horarios (1 a 3)", color = com.orbitasinertegia.orbita.ui.theme.Indigo, fontWeight = FontWeight.SemiBold)
            horarios.forEachIndexed { i, s -> FilaHorario(s, onRemove = { horarios.removeAt(i) }) }
            if (horarios.size < 3) Text(
                "+ Agregar horario", color = Violeta, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { eligiendo = true }.padding(16.dp),
            )
            Text("Nota (opcional)", color = com.orbitasinertegia.orbita.ui.theme.Indigo, fontWeight = FontWeight.SemiBold)
            androidx.compose.material3.OutlinedTextField(
                nota, { nota = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cuéntale al coordinador por qué") },
            )
            BotonPrincipal(
                "Enviar", { onConfirm(horarios.toList(), nota.trim().ifBlank { null }) },
                Modifier.fillMaxWidth(), enabled = horarios.isNotEmpty(),
            )
        }
    }
    if (eligiendo) SelectorHorario(onDismiss = { eligiendo = false }) { horarios.add(it); eligiendo = false }
}
