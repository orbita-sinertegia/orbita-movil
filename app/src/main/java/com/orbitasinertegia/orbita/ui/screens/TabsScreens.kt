package com.orbitasinertegia.orbita.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.orbitasinertegia.orbita.BuildConfig
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Notice
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.BotonSecundario
import com.orbitasinertegia.orbita.ui.Chip
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Iniciales
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.Tarjeta
import com.orbitasinertegia.orbita.ui.estadoDe
import com.orbitasinertegia.orbita.ui.respuestaTexto
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

@Composable
private fun Encabezado(titulo: String, subtitulo: String? = null) {
    Column(Modifier.fillMaxWidth().background(Noche).statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)) {
        if (subtitulo != null) Text(subtitulo, color = Color(0xFFC9C3E3), fontSize = 15.sp)
        Text(titulo, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
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
            Encabezado("Solicitudes", if (sesion.isAdmin) "Por coordinar" else "Lo que pediste")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (error != null) item { ErrorTexto(error) }
                if (cargando) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Violeta) } }
                item { Seccion(if (sesion.isAdmin) "Solicitudes por programar" else "Esperando que las programen") }
                if (!cargando && porProgramar.isEmpty()) item { Vacio("No hay solicitudes pendientes.") }
                items(porProgramar, key = { "s" + it.id }) { cita -> FilaSolicitud(cita, sesion) }
                item { Seccion(if (sesion.isAdmin) "Piden cambios o no asistirán" else "Cambios que pediste") }
                if (!cargando && conCambios.isEmpty()) item { Vacio("Nada por ahora.") }
                items(conCambios, key = { "c" + it.id }) { cita -> FilaSolicitud(cita, sesion) }
            }
        }
        if (!sesion.isAdmin) {
            FloatingActionButton(
                onClick = { sesion.ir(Ruta.Solicitar) }, containerColor = Violeta, contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Icon(Icons.Default.Add, "Solicitar cita") }
        }
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto.uppercase(), color = TextoSuave, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FilaSolicitud(cita: Appointment, sesion: Sesion) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable { sesion.ir(Ruta.Detalle(cita.id)) }.padding(16.dp),
    ) {
        Text(cita.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        val quien = cita.requester?.name
        Text(
            listOfNotNull(Kinds.label(cita.kind), quien?.let { "Pidió: $it" }).joinToString(" · "),
            color = TextoSuave, fontSize = 14.sp,
        )
        if (cita.status == "solicitada" && cita.proposedSlots.isNotEmpty()) {
            Text("Propuso: " + cita.proposedSlots.joinToString(" / ") { Fechas.rango(it) }, color = TextoSuave, fontSize = 13.sp)
        }
        cita.participants.filter { it.response in listOf("rechazada", "cambio_solicitado") }.forEach { p ->
            Text("${p.user?.name ?: "Alguien"}: ${respuestaTexto(p.response).lowercase()}" + (p.responseNote?.let { " · $it" } ?: ""), color = TextoSuave, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Chip(estadoDe(cita, sesion.myId, sesion.isAdmin))
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
        Encabezado("Avisos")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (error != null) item { ErrorTexto(error) }
            if (avisos.any { !it.read }) item {
                TextButton(onClick = { scope.launch { runCatching { Repo.markAllRead() }; recarga++ } }) {
                    Text("Marcar todas como leídas", color = Violeta)
                }
            }
            if (cargando) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Violeta) } }
            if (!cargando && avisos.isEmpty()) item { Vacio("No tienes avisos.") }
            items(avisos, key = { it.id }) { aviso ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (aviso.read) MaterialTheme.colorScheme.surface else VioletaClaro, RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch {
                                if (!aviso.read) { runCatching { Repo.markRead(aviso.id) }; recarga++ }
                                aviso.appointmentId?.let { sesion.ir(Ruta.Detalle(it)) }
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).background(if (aviso.read) Color.Transparent else Ambar, CircleShape))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(aviso.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        aviso.message?.let { Text(it, color = TextoSuave, fontSize = 14.sp) }
                        Fechas.parse(aviso.createdAt)?.let {
                            Text("${Fechas.diaCorto(it)} · ${Fechas.hora(it)}", color = TextoSuave, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// Perfil -----------------------------------------------------------------------------------

@Composable
fun PerfilScreen(sesion: Sesion) {
    val context = LocalContext.current
    val perfil = sesion.perfil
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Encabezado("Perfil")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Iniciales(perfil?.name ?: "?")
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(perfil?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(perfil?.email ?: "", color = TextoSuave, fontSize = 14.sp)
                        Text(perfil?.roleLabel ?: "", color = Violeta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (sesion.isAdmin) {
                Text("Como administrador coordinas las citas: programas, inicias y cierras.", color = TextoSuave, fontSize = 14.sp)
            }
            BotonSecundario(
                "Abrir Órbita web",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.WEB_URL.toUri())) },
                modifier = Modifier.fillMaxWidth(),
            )
            BotonPrincipal("Cerrar sesión", onClick = { sesion.salir() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Órbita Citas · versión ${BuildConfig.VERSION_NAME}", color = TextoSuave, fontSize = 12.sp)
        }
    }
}
