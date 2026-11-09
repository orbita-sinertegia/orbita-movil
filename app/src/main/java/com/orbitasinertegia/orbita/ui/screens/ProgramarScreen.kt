package com.orbitasinertegia.orbita.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Conflict
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Invitee
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Profile
import com.orbitasinertegia.orbita.data.ProjectLite
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.Slot
import com.orbitasinertegia.orbita.data.TaskLite
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Etiqueta
import com.orbitasinertegia.orbita.ui.FilaHorario
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.SelectorHorario
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.Tarjeta
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.Peligro
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

private fun etiquetaRol(p: Profile) = when {
    p.role == "client" -> "Cliente"
    p.isAdmin -> "Administración"
    else -> "Equipo"
}

@Composable
fun ProgramarScreen(sesion: Sesion, id: String?) {
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var cita by remember { mutableStateOf<Appointment?>(null) }
    var usuarios by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var proyectos by remember { mutableStateOf<List<ProjectLite>>(emptyList()) }
    var tareas by remember { mutableStateOf<List<TaskLite>>(emptyList()) }

    var tipo by remember { mutableStateOf("reunion") }
    var titulo by remember { mutableStateOf("") }
    var proyecto by remember { mutableStateOf<ProjectLite?>(null) }
    var horario by remember { mutableStateOf<Slot?>(null) }
    var modalidad by remember { mutableStateOf("virtual") }
    var lugar by remember { mutableStateOf("") }
    var enlace by remember { mutableStateOf("") }
    val invitados = remember { mutableStateListOf<Invitee>() }
    var tarea by remember { mutableStateOf<TaskLite?>(null) }
    var conflictos by remember { mutableStateOf<List<Conflict>>(emptyList()) }

    var eligiendoHorario by remember { mutableStateOf(false) }
    var agregando by remember { mutableStateOf(false) }
    var publicando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            usuarios = Repo.activeUsers()
            proyectos = Repo.projects()
            if (id != null) cita = Repo.appointment(id)
        }.onFailure { error = it.friendly() }
        cita?.let { c ->
            tipo = c.kind
            titulo = c.title
            proyecto = proyectos.firstOrNull { it.id == c.projectId } ?: c.project
            horario = if (c.startsAt != null && c.endsAt != null) Slot(c.startsAt, c.endsAt) else c.proposedSlots.firstOrNull()
            modalidad = c.modality ?: Kinds.suggestedModality(c.kind)
            lugar = c.location.orEmpty()
            enlace = c.meetingUrl.orEmpty()
            c.participants.filter { it.userId != sesion.myId && it.user != null }.forEach {
                invitados.add(Invitee(it.user!!, it.required, it.roleLabel ?: etiquetaRol(it.user)))
            }
            val solicitante = c.requester
            if (solicitante != null && solicitante.id != sesion.myId && invitados.none { it.user.id == solicitante.id }) {
                invitados.add(Invitee(solicitante, true, etiquetaRol(solicitante)))
            }
            if (c.relatedTaskId != null) tarea = TaskLite(c.relatedTaskId, c.relatedTaskTitle ?: "Tarea")
        }
        cargando = false
    }

    LaunchedEffect(proyecto?.id) {
        tareas = proyecto?.let { p -> runCatching { Repo.projectTasks(p.id) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    val ids = invitados.map { it.user.id }
    LaunchedEffect(horario, ids) {
        val h = horario ?: return@LaunchedEffect run { conflictos = emptyList() }
        conflictos = runCatching { Repo.conflicts(h, ids + listOfNotNull(sesion.myId), id) }.getOrDefault(emptyList())
    }

    fun ocupado(userId: String) = conflictos.any { it.userId == userId }
    val bloqueados = invitados.filter { it.required && ocupado(it.user.id) }.map { it.user.name } +
        listOfNotNull(sesion.perfil?.name?.takeIf { sesion.myId?.let(::ocupado) == true }?.let { "Tú" })
    val opcionalesOcupados = invitados.filter { !it.required && ocupado(it.user.id) }.map { it.user.name }
    val listo = titulo.trim().length >= 3 && horario != null && invitados.isNotEmpty() &&
        (if (modalidad == "presencial") lugar.trim().length >= 2 else enlace.trim().startsWith("https://")) && bloqueados.isEmpty()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        BarraClara(if (cita?.status == "programada") "Reprogramar cita" else "Programar cita", onBack = { sesion.atras() }) {
            Text(
                "Coordinador", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Noche, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        if (cargando) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Violeta) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            cita?.takeIf { it.status == "solicitada" }?.let { c ->
                Tarjeta {
                    Text("SOLICITUD DE ${(c.requester?.name ?: "").uppercase()}", color = TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${Kinds.label(c.kind)}${c.project?.let { " · ${it.name}" } ?: ""}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    c.reason?.let { Text(it, color = TextoSuave, fontSize = 14.sp) }
                    if (c.proposedSlots.isNotEmpty()) {
                        Text("Toca un horario propuesto para usarlo:", color = TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            c.proposedSlots.forEach { s -> FilaHorario(s, onRemove = null, onClick = { horario = s }) }
                        }
                    }
                }
            }

            Column { Etiqueta("Tipo"); SelectorTipo(tipo) { tipo = it; modalidad = Kinds.suggestedModality(it) } }
            Column {
                Etiqueta("Título")
                OutlinedTextField(titulo, { titulo = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. Sesión de grabación VSL") })
            }
            Column { Etiqueta("Proyecto"); SelectorProyecto(proyectos, proyecto, permitirNinguno = true) { proyecto = it; tarea = null } }

            Column {
                Etiqueta("Horario")
                if (horario != null) FilaHorario(horario!!, onRemove = null, onClick = { eligiendoHorario = true })
                else Text(
                    "Elegir día y hora", color = Violeta, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { eligiendoHorario = true }.padding(16.dp),
                )
            }

            Column {
                Etiqueta("Modalidad")
                Row(Modifier.fillMaxWidth().background(Color(0xFFE4E2EE), RoundedCornerShape(14.dp)).padding(4.dp)) {
                    listOf("presencial" to "Presencial", "virtual" to "Virtual").forEach { (valor, texto) ->
                        val elegido = modalidad == valor
                        Text(
                            texto, textAlign = TextAlign.Center, fontWeight = if (elegido) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                                .background(if (elegido) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { modalidad = valor }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
            if (modalidad == "presencial") Column {
                Etiqueta("Lugar")
                OutlinedTextField(lugar, { lugar = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ej. Estudio Órbita") })
            } else Column {
                Etiqueta("Enlace de Meet o Zoom")
                OutlinedTextField(
                    enlace, { enlace = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://meet.google.com/…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta("Invitados")
                Text("Tú participas como coordinador.", color = TextoSuave, fontSize = 13.sp)
                invitados.forEachIndexed { i, inv ->
                    val estaOcupado = ocupado(inv.user.id)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, if (estaOcupado && inv.required) Color(0xFFF4A62A) else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.user.name, fontWeight = FontWeight.SemiBold)
                            Text(inv.roleLabel ?: "", color = TextoSuave, fontSize = 12.sp)
                        }
                        Text(
                            if (inv.required) "Obligatorio" else "Opcional", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (inv.required) Indigo else TextoSuave,
                            modifier = Modifier.background(if (inv.required) VioletaClaro else Color(0xFFECEBF2), RoundedCornerShape(10.dp))
                                .clickable { invitados[i] = inv.copy(required = !inv.required) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        if (horario != null) Text(
                            if (estaOcupado) "Ocupado" else "Libre", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (estaOcupado) Color(0xFFB4720C) else Violeta,
                        )
                        IconButton(onClick = { invitados.removeAt(i) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, "Quitar invitado", tint = TextoSuave)
                        }
                    }
                }
                Text(
                    "+ Agregar invitado", color = Violeta, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .clickable { agregando = true }.padding(14.dp),
                )
            }

            if (proyecto != null) Column {
                Etiqueta("Tarea de referencia (opcional)")
                SelectorTarea(tareas, tarea) { tarea = it }
            }
            ErrorTexto(error)
        }

        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bloqueados.isNotEmpty()) {
                Text("${bloqueados.joinToString(", ")} ya tiene otra cita en ese horario.", color = Peligro, fontSize = 13.sp, textAlign = TextAlign.Center)
            } else if (opcionalesOcupados.isNotEmpty()) {
                Text("Aviso: ${opcionalesOcupados.joinToString(", ")} (opcional) tiene otra cita en ese horario.", color = Color(0xFFB4720C), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.size(8.dp))
            BotonPrincipal(
                if (publicando) "Publicando…" else "Publicar cita",
                onClick = {
                    publicando = true; error = null
                    scope.launch {
                        runCatching {
                            Repo.scheduleAppointment(
                                id, proyecto?.id, tipo, titulo.trim(), horario!!, modalidad,
                                lugar.trim().ifBlank { null }, enlace.trim().ifBlank { null }, invitados.toList(), tarea?.id,
                            )
                        }.onSuccess { nueva ->
                            // Cambia esta pantalla por el detalle actualizado, sin duplicarlo en la pila.
                            sesion.atras()
                            if (sesion.pila.lastOrNull() == Ruta.Detalle(nueva)) sesion.atras()
                            sesion.ir(Ruta.Detalle(nueva))
                        }.onFailure { error = it.friendly() }
                        publicando = false
                    }
                },
                enabled = listo && !publicando,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (eligiendoHorario) SelectorHorario(onDismiss = { eligiendoHorario = false }) { horario = it; eligiendoHorario = false }
    if (agregando) ElegirPersona(
        usuarios.filter { u -> u.id != sesion.myId && invitados.none { it.user.id == u.id } },
        onDismiss = { agregando = false },
    ) { u -> invitados.add(Invitee(u, u.role == "client", etiquetaRol(u))); agregando = false }
}

@Composable
private fun SelectorTarea(tareas: List<TaskLite>, elegida: TaskLite?, onElegir: (TaskLite?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { abierto = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(elegida?.title ?: "Ninguna", modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Ninguna") }, onClick = { onElegir(null); abierto = false })
            if (tareas.isEmpty()) DropdownMenuItem(text = { Text("Este proyecto no tiene tareas", color = TextoSuave) }, onClick = { abierto = false })
            tareas.forEach { t -> DropdownMenuItem(text = { Text(t.title) }, onClick = { onElegir(t); abierto = false }) }
        }
    }
}

@Composable
private fun ElegirPersona(personas: List<Profile>, onDismiss: () -> Unit, onElegir: (Profile) -> Unit) {
    var buscar by remember { mutableStateOf("") }
    val filtradas = personas.filter { buscar.isBlank() || it.name.contains(buscar, true) || (it.email ?: "").contains(buscar, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar invitado") },
        text = {
            Column {
                OutlinedTextField(buscar, { buscar = it }, singleLine = true, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(filtradas, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { onElegir(p) }.padding(vertical = 10.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${etiquetaRol(p)} · ${p.email ?: ""}", color = TextoSuave, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}
