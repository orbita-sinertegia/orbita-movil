package com.orbitasinertegia.orbita.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.orbitasinertegia.orbita.BuildConfig
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Notice
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.Slot
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.data.hace
import com.orbitasinertegia.orbita.ui.Chip
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Estado
import com.orbitasinertegia.orbita.ui.EtiquetaRol
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Crema
import com.orbitasinertegia.orbita.ui.theme.Fondo
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.Linea
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.Peligro
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

@Composable
private fun Encabezado(titulo: String, subtitulo: String? = null, derecha: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Noche).statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitulo != null) Text(subtitulo, color = Color(0xFFC9C3E3), fontSize = 15.sp)
            Text(titulo, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        derecha()
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
}

@Composable
private fun Seccion(texto: String) {
    Text(texto.uppercase(), color = TextoSuave, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

private fun corto(slot: Slot): String {
    val a = Fechas.parse(slot.startsAt) ?: return ""
    return "${Fechas.diaSemana(a.toLocalDate())} ${a.dayOfMonth} · ${Fechas.hora(a)}"
}

// Solicitudes ------------------------------------------------------------------------------

@Composable
fun SolicitudesScreen(sesion: Sesion) {
    var citas by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { Repo.appointments() }.onSuccess { citas = it; error = null }.onFailure { error = it.friendly() }
        cargando = false
    }

    val porProgramar = citas.filter { it.status == "solicitada" && (sesion.isAdmin || it.requestedBy == sesion.myId) }
        .sortedBy { it.createdAt }
    val conCambios = citas.filter { c ->
        c.status == "programada" && c.participants.any { p ->
            p.response in listOf("rechazada", "cambio_solicitado") && (sesion.isAdmin || p.userId == sesion.myId)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            Encabezado("Solicitudes", if (sesion.isAdmin) "Bandeja" else "Lo que pediste") {
                if (sesion.isAdmin) EtiquetaRol("Administrador")
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (error != null) item { ErrorTexto(error) }
                if (cargando) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Violeta) } }
                item { Seccion(if (sesion.isAdmin) "Por programar" else "Esperando que las programen") }
                if (!cargando && porProgramar.isEmpty()) item { Vacio("No hay solicitudes pendientes.") }
                items(porProgramar, key = { "s" + it.id }) { cita ->
                    if (sesion.isAdmin) SolicitudAdmin(cita) { sesion.ir(Ruta.Detalle(cita.id)) }
                    else SolicitudCliente(cita) { sesion.ir(Ruta.Detalle(cita.id)) }
                }
                item { Seccion(if (sesion.isAdmin) "Piden cambios o no asistirán" else "Cambios que pediste") }
                if (!cargando && conCambios.isEmpty()) item { Vacio("Nada por ahora.") }
                items(conCambios, key = { "c" + it.id }) { cita -> CambioPedido(cita, sesion) }
            }
        }
        if (!sesion.isAdmin) {
            FloatingActionButton(
                onClick = { sesion.ir(Ruta.Solicitar) }, containerColor = Violeta, contentColor = Color.White,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(64.dp),
            ) { Icon(Icons.Default.Add, "Solicitar cita") }
        }
    }
}

@Composable
private fun TarjetaBlanca(borde: Color? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .then(if (borde != null) Modifier.border(1.dp, borde, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick).padding(18.dp),
    ) { content() }
}

@Composable
private fun SolicitudCliente(cita: Appointment, onClick: () -> Unit) {
    TarjetaBlanca(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Text(cita.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Chip(Estado("Solicitada", Crema, Color(0xFF8A4B00)))
        }
        Text(
            listOfNotNull(Kinds.label(cita.kind), cita.project?.name?.let { "Proyecto $it" } ?: cita.modality?.replaceFirstChar { it.uppercase() })
                .joinToString(" · "),
            color = TextoSuave, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
        )
        if (cita.proposedSlots.isNotEmpty()) {
            Text("Propusiste:", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            cita.proposedSlots.forEach { Text("· " + Fechas.rango(it), fontSize = 15.sp) }
        }
    }
}

@Composable
private fun SolicitudAdmin(cita: Appointment, onClick: () -> Unit) {
    TarjetaBlanca(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cita.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Pedida por ${cita.requester?.name ?: "alguien"} · ${Kinds.label(cita.kind)}",
                    color = TextoSuave, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextoSuave)
        }
        if (cita.proposedSlots.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cita.proposedSlots.forEach {
                    Text(
                        corto(it), color = Indigo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.background(VioletaClaro, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CambioPedido(cita: Appointment, sesion: Sesion) {
    val pedidos = cita.participants.filter { p ->
        p.response in listOf("rechazada", "cambio_solicitado") && (sesion.isAdmin || p.userId == sesion.myId)
    }
    val primero = pedidos.firstOrNull()
    TarjetaBlanca(
        borde = if (sesion.isAdmin) Color(0xFFF2C4CB) else null,
        onClick = {
            if (sesion.isAdmin && primero?.response == "cambio_solicitado") sesion.ir(Ruta.Cambio(cita.id, primero.userId))
            else sesion.ir(Ruta.Detalle(cita.id))
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(cita.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (sesion.isAdmin) Chip(Estado("Requiere ajuste", Color(0xFFFCE4E8), Peligro))
            else Chip(Estado("En revisión", VioletaClaro, Indigo))
        }
        pedidos.forEach { p ->
            val quien = if (p.userId == sesion.myId) "Pediste" else p.user?.name ?: "Alguien"
            val texto = when {
                p.response == "rechazada" && p.userId == sesion.myId -> "Rechazaste la cita."
                p.response == "rechazada" -> "$quien no asistirá"
                p.userId == sesion.myId -> "Pediste mover a " + p.proposedSlots.joinToString(" o ") { corto(it) } + "."
                else -> "$quien pide cambio de horario"
            }
            Text(texto, color = TextoSuave, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            if (sesion.isAdmin && !p.responseNote.isNullOrBlank()) {
                Text(
                    "“${p.responseNote}”", fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(Fondo, RoundedCornerShape(12.dp)).padding(12.dp),
                )
            }
        }
    }
}

// Avisos -----------------------------------------------------------------------------------

@Composable
fun AvisosScreen(sesion: Sesion) {
    val scope = rememberCoroutineScope()
    var avisos by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }

    LaunchedEffect(recarga) {
        runCatching { Repo.notices() }.onSuccess { avisos = it; error = null }.onFailure { error = it.friendly() }
        cargando = false
        sesion.contarAvisos()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Encabezado("Avisos", "Notificaciones") {
            if (avisos.any { !it.read }) {
                OutlinedButton(
                    onClick = { scope.launch { runCatching { Repo.markAllRead() }; recarga++ } },
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A4A55)),
                ) { Text("Marcar todas como leídas", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (error != null) item { Box(Modifier.padding(16.dp)) { ErrorTexto(error) } }
            if (cargando) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Violeta) } }
            if (!cargando && avisos.isEmpty()) item { Vacio("No tienes avisos.") }
            items(avisos, key = { it.id }) { aviso ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (aviso.read) Fondo else MaterialTheme.colorScheme.surface)
                        .clickable {
                            scope.launch {
                                if (!aviso.read) { runCatching { Repo.markRead(aviso.id) }; recarga++ }
                                aviso.appointmentId?.let { sesion.ir(Ruta.Detalle(it)) }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 7.dp).size(10.dp).background(if (aviso.read) Color.Transparent else Ambar, CircleShape))
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                aviso.title, fontWeight = if (aviso.read) FontWeight.SemiBold else FontWeight.Bold, fontSize = 17.sp,
                                color = if (aviso.read) Color(0xFF4A4760) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(Fechas.hace(aviso.createdAt), color = TextoSuave, fontSize = 13.sp)
                        }
                        aviso.message?.let { Text(it, color = TextoSuave, fontSize = 15.sp) }
                    }
                }
                HorizontalDivider(color = Linea)
            }
        }
    }
}

// Perfil -----------------------------------------------------------------------------------

@Composable
fun PerfilScreen(sesion: Sesion) {
    val context = LocalContext.current
    val perfil = sesion.perfil
    val nombre = perfil?.fullName?.takeIf { it.isNotBlank() } ?: sesion.saludo
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxWidth().background(Noche).statusBarsPadding().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val iniciales = nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            Box(Modifier.size(96.dp).background(Violeta, CircleShape), contentAlignment = Alignment.Center) {
                Text(iniciales.ifBlank { "?" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(nombre, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(perfil?.email ?: "", color = Color(0xFFC9C3E3), fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            EtiquetaRol(perfil?.roleLabel ?: "")
        }
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))) {
                FilaDato("Nombre", nombre)
                HorizontalDivider(color = Linea)
                FilaDato("Correo", perfil?.email ?: "")
                HorizontalDivider(color = Linea)
                FilaDato("Rol", perfil?.roleLabel ?: "")
            }
            Text("Roles: Cliente · Equipo · Administrador. Tu rol lo define el administrador de la agencia.", color = TextoSuave, fontSize = 13.sp)
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.WEB_URL.toUri())) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Language, null, tint = Violeta)
                Spacer(Modifier.size(12.dp))
                Text("Abrir Órbita web", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextoSuave)
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = { sesion.salir() },
                modifier = Modifier.fillMaxWidth().height(56.dp).navigationBarsPadding(),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2C4CB)),
            ) { Text("Cerrar sesión", color = Peligro, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Text("Órbita Citas · versión ${BuildConfig.VERSION_NAME}", color = TextoSuave, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(etiqueta, color = TextoSuave, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(valor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
