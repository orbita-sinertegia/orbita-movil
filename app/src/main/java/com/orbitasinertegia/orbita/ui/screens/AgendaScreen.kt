package com.orbitasinertegia.orbita.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.Chip
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.estadoDe
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import java.time.DayOfWeek
import java.time.LocalDate

private val ACTIVAS = setOf("programada", "en_curso", "realizada", "incompleta")

@Composable
fun AgendaScreen(sesion: Sesion) {
    var citas by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var lunes by remember { mutableStateOf(Fechas.hoy().with(DayOfWeek.MONDAY)) }
    var diaElegido by remember { mutableStateOf<LocalDate?>(null) }
    var tipo by remember { mutableStateOf<String?>(null) }
    var estado by remember { mutableStateOf("todas") }

    LaunchedEffect(recarga) {
        cargando = true
        runCatching { Repo.appointments() }
            .onSuccess { citas = it.filter { c -> c.status in ACTIVAS && c.startsAt != null }; error = null }
            .onFailure { error = it.friendly() }
        cargando = false
    }

    val semana = (0L..6L).map { lunes.plusDays(it) }
    val visibles = citas.filter { c ->
        val dia = Fechas.parse(c.startsAt)!!.toLocalDate()
        val enRango = if (diaElegido != null) dia == diaElegido else dia in semana
        val texto = estadoDe(c, sesion.myId, sesion.isAdmin).texto
        enRango && (tipo == null || c.kind == tipo) && when (estado) {
            "pendientes" -> texto == "Pendiente de confirmar" || texto == "Requiere ajuste"
            "confirmadas" -> texto == "Confirmada" || texto == "En curso"
            else -> true
        }
    }.groupBy { Fechas.parse(it.startsAt)!!.toLocalDate() }.toSortedMap()
    val conCitas = citas.map { Fechas.parse(it.startsAt)!!.toLocalDate() }.toSet()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Encabezado oscuro con la semana.
            Column(Modifier.fillMaxWidth().background(Noche).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hola, ${sesion.perfil?.name?.substringBefore(' ') ?: ""}", color = Color(0xFFC9C3E3), fontSize = 15.sp)
                        Text("Mi agenda", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { recarga++ }) { Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { lunes = lunes.minusWeeks(1); diaElegido = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronLeft, "Semana anterior", tint = Color.White)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        semana.forEach { dia ->
                            val elegido = dia == diaElegido
                            val hoy = dia == Fechas.hoy()
                            Column(
                                Modifier.width(40.dp)
                                    .background(if (elegido) Violeta else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { diaElegido = if (elegido) null else dia }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(Fechas.diaSemana(dia), color = Color(0xFFC9C3E3), fontSize = 12.sp)
                                Text(
                                    "${dia.dayOfMonth}", color = if (hoy && !elegido) Ambar else Color.White,
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                )
                                Box(Modifier.size(5.dp).background(if (dia in conCitas) Ambar else Color.Transparent, CircleShape))
                            }
                        }
                    }
                    IconButton(onClick = { lunes = lunes.plusWeeks(1); diaElegido = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronRight, "Semana siguiente", tint = Color.White)
                    }
                }
            }

            // Filtros: tipo y estado.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 12.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filtro("Todas", tipo == null) { tipo = null }
                Kinds.all.forEach { k -> Filtro(Kinds.label(k), tipo == k) { tipo = if (tipo == k) null else k } }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filtro("Cualquier estado", estado == "todas") { estado = "todas" }
                Filtro("Pendientes", estado == "pendientes") { estado = "pendientes" }
                Filtro("Confirmadas", estado == "confirmadas") { estado = "confirmadas" }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (error != null) item { ErrorTexto(error) }
                if (cargando && citas.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Violeta) }
                }
                if (!cargando && visibles.isEmpty() && error == null) item {
                    Text(
                        "No hay citas en estos días.", color = TextoSuave, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                visibles.forEach { (dia, delDia) ->
                    item(key = dia.toString()) {
                        Text(
                            Fechas.diaLargo(dia).uppercase(), color = TextoSuave, fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(delDia, key = { it.id }) { cita -> TarjetaCita(cita, sesion) { sesion.ir(Ruta.Detalle(cita.id)) } }
                }
                item {
                    Text(
                        "Horas en America/Mexico_City", color = TextoSuave, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { sesion.ir(if (sesion.isAdmin) Ruta.Programar(null) else Ruta.Solicitar) },
            containerColor = Violeta, contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, if (sesion.isAdmin) "Programar cita" else "Solicitar cita") }
    }
}

@Composable
fun Filtro(texto: String, elegido: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = elegido, onClick = onClick, label = { Text(texto) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Noche, selectedLabelColor = Color.White),
    )
}

@Composable
fun TarjetaCita(cita: Appointment, sesion: Sesion, onClick: () -> Unit) {
    val inicio = Fechas.parse(cita.startsAt)
    val fin = Fechas.parse(cita.endsAt)
    val lugar = when (cita.modality) {
        "presencial" -> "Presencial" + (cita.location?.let { " · $it" } ?: "")
        "virtual" -> "Virtual" + plataforma(cita.meetingUrl)?.let { " · $it" }.orEmpty()
        else -> null
    }
    val detalle = listOfNotNull(cita.project?.name ?: if (cita.projectId == null) "Interna" else null, lugar).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        Column(Modifier.width(64.dp)) {
            if (inicio != null) Text(Fechas.hora(inicio), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (fin != null) Text(Fechas.hora(fin), color = TextoSuave, fontSize = 13.sp)
            if (inicio == null) Text("Sin fecha", color = TextoSuave, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(cita.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (detalle.isNotBlank()) Text(detalle, color = TextoSuave, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Chip(estadoDe(cita, sesion.myId, sesion.isAdmin))
        }
    }
}

fun plataforma(url: String?): String? = when {
    url == null -> null
    url.contains("meet.google") -> "Meet"
    url.contains("zoom.") -> "Zoom"
    url.contains("teams.") -> "Teams"
    else -> "En línea"
}
