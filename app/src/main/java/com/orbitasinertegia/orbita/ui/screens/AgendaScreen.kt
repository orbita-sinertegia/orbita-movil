package com.orbitasinertegia.orbita.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.orbitasinertegia.orbita.data.hace
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
    var actualizado by remember { mutableStateOf<String?>(null) }
    var lunes by remember { mutableStateOf(Fechas.hoy().with(DayOfWeek.MONDAY)) }
    var diaElegido by remember { mutableStateOf<LocalDate?>(null) }
    var tipo by remember { mutableStateOf<String?>(null) }
    var estado by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recarga) {
        cargando = true
        runCatching { Repo.appointments() }
            .onSuccess {
                citas = it.filter { c -> c.status in ACTIVAS && c.startsAt != null }
                error = null
                actualizado = java.time.OffsetDateTime.now().toString()
            }
            .onFailure { error = it.friendly() }
        cargando = false
    }

    val semana = (0L..6L).map { lunes.plusDays(it) }
    val habiles = semana.take(5)
    val visibles = citas.filter { c ->
        val dia = Fechas.parse(c.startsAt)!!.toLocalDate()
        val enRango = if (diaElegido != null) dia == diaElegido else dia in semana
        val texto = estadoDe(c, sesion.myId, sesion.isAdmin).texto
        enRango && (tipo == null || c.kind == tipo) && when (estado) {
            "pendientes" -> texto == "Pendiente de confirmar" || texto == "Requiere ajuste"
            "confirmadas" -> texto == "Confirmada" || texto == "En curso"
            "realizadas" -> c.status in listOf("realizada", "incompleta")
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
                        Text("Hola, ${sesion.saludo}", color = Color(0xFFC9C3E3), fontSize = 15.sp)
                        Text("Mi agenda", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(48.dp).background(Color(0xFF2A2A33), CircleShape).clickable { sesion.tab = 2 },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Notifications, "Avisos", tint = Color.White)
                        if (sesion.noLeidos > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).background(Ambar, CircleShape))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flecha(Icons.Default.ChevronLeft, "Semana anterior") { lunes = lunes.minusWeeks(1); diaElegido = null }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        habiles.forEach { dia ->
                            val elegido = dia == diaElegido
                            val hoy = dia == Fechas.hoy()
                            Column(
                                Modifier.width(48.dp)
                                    .background(if (elegido) Violeta else Color.Transparent, RoundedCornerShape(14.dp))
                                    .clickable { diaElegido = if (elegido) null else dia }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(Fechas.diaSemana(dia), color = Color(0xFFC9C3E3), fontSize = 13.sp)
                                Text("${dia.dayOfMonth}", color = if (hoy && !elegido) Ambar else Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                                Box(Modifier.size(5.dp).background(if (dia in conCitas && !elegido) Ambar else Color.Transparent, CircleShape))
                            }
                        }
                    }
                    Flecha(Icons.Default.ChevronRight, "Semana siguiente") { lunes = lunes.plusWeeks(1); diaElegido = null }
                }
            }

            // Filtros: Todas, Tipo y Estado.
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filtro("Todas", tipo == null && estado == null) { tipo = null; estado = null }
                Desplegable(
                    texto = tipo?.let { Kinds.label(it) } ?: "Tipo",
                    elegido = tipo != null,
                    opciones = listOf<Pair<String?, String>>(null to "Todos los tipos") + Kinds.activos.map { it.slug to it.label },
                ) { tipo = it }
                Desplegable(
                    texto = when (estado) { "pendientes" -> "Pendientes"; "confirmadas" -> "Confirmadas"; "realizadas" -> "Realizadas"; else -> "Estado" },
                    elegido = estado != null,
                    opciones = listOf<Pair<String?, String>>(null to "Cualquier estado", "pendientes" to "Pendientes", "confirmadas" to "Confirmadas", "realizadas" to "Realizadas"),
                ) { estado = it }
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
                        if (diaElegido != null) "No hay citas este día." else "No hay citas esta semana.",
                        color = TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
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
                        "Horas en America/Mexico_City · " + (actualizado?.let { "actualizado ${Fechas.hace(it)}" } ?: "cargando") + " · toca para actualizar",
                        color = TextoSuave, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { recarga++ }.padding(vertical = 8.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { sesion.ir(if (sesion.isAdmin) Ruta.Programar(null) else Ruta.Solicitar) },
            containerColor = Violeta, contentColor = Color.White, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(64.dp),
        ) { Icon(Icons.Default.Add, if (sesion.isAdmin) "Programar cita" else "Solicitar cita") }
    }
}

@Composable
private fun Flecha(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).background(Color(0xFF2A2A33), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icono, descripcion, tint = Color.White) }
}

/** Chip con menú: "Tipo ▾", "Estado ▾". */
@Composable
fun <T> Desplegable(texto: String, elegido: Boolean, opciones: List<Pair<T, String>>, onElegir: (T) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Filtro("$texto ▾", elegido) { abierto = true }
        DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { (valor, etiqueta) ->
                DropdownMenuItem(text = { Text(etiqueta) }, onClick = { onElegir(valor); abierto = false })
            }
        }
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
