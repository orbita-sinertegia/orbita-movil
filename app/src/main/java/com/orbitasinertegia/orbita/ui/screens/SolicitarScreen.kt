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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.ProjectLite
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.Slot
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.Aviso
import com.orbitasinertegia.orbita.ui.DialogoNuevoTipo
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Etiqueta
import com.orbitasinertegia.orbita.ui.FilaHorario
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.SelectorHorario
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

@Composable
fun BarraClara(titulo: String, onBack: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar") }
        Text(titulo, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
        extra()
    }
}

/** Botones de tipo de cita (Reunión, Grabación, Entrevista, Accesos). */
@Composable
fun SelectorTipo(tipo: String, puedeAgregar: Boolean = false, onTipo: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var nuevo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val opciones = Kinds.activos.map { it.slug } + if (puedeAgregar) listOf(NUEVO_TIPO) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.chunked(2).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fila.forEach { k ->
                    val elegido = k == tipo
                    val esNuevo = k == NUEVO_TIPO
                    Text(
                        if (esNuevo) "+ Otro tipo" else Kinds.label(k),
                        textAlign = TextAlign.Center,
                        fontWeight = if (elegido || esNuevo) FontWeight.Bold else FontWeight.Medium,
                        color = when { elegido -> Indigo; esNuevo -> Violeta; else -> MaterialTheme.colorScheme.onSurface },
                        modifier = Modifier.weight(1f)
                            .background(if (elegido) VioletaClaro else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .border(if (elegido) 2.dp else 1.dp, if (elegido) Violeta else MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { if (esNuevo) nuevo = true else onTipo(k) }
                            .padding(vertical = 14.dp),
                    )
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        ErrorTexto(error)
    }
    if (nuevo) DialogoNuevoTipo(onDismiss = { nuevo = false }) { nombre, modalidad ->
        nuevo = false; error = null
        scope.launch {
            runCatching {
                val clave = Repo.addKind(nombre, modalidad)
                Kinds.lista = Repo.kinds()
                clave
            }.onSuccess(onTipo).onFailure { error = it.friendly() }
        }
    }
}

private const val NUEVO_TIPO = "__nuevo__"

/** Lista desplegable sencilla de proyectos. */
@Composable
fun SelectorProyecto(proyectos: List<ProjectLite>, elegido: ProjectLite?, permitirNinguno: Boolean, onElegir: (ProjectLite?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { abierto = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(elegido?.name ?: if (permitirNinguno) "Sin proyecto (interna)" else "Elige un proyecto", fontWeight = FontWeight.SemiBold)
                elegido?.let { descripcionProyecto(it) }?.let { Text(it, color = TextoSuave, fontSize = 13.sp) }
            }
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
            if (permitirNinguno) DropdownMenuItem(text = { Text("Sin proyecto (interna)") }, onClick = { onElegir(null); abierto = false })
            proyectos.forEach { p ->
                DropdownMenuItem(
                    text = { Column { Text(p.name); descripcionProyecto(p)?.let { Text(it, color = TextoSuave, fontSize = 12.sp) } } },
                    onClick = { onElegir(p); abierto = false },
                )
            }
        }
    }
}

fun descripcionProyecto(p: ProjectLite): String? {
    val modo = when (p.recordingMode) { "oficina" -> "Modalidad Oficina"; "remota" -> "Modalidad Remota"; else -> null }
    val lanzamiento = p.launchDate?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
        ?.let { "Lanzamiento " + it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("es-MX"))).replace(".", "") }
    return listOfNotNull(modo, lanzamiento).joinToString(" · ").ifBlank { null }
}

@Composable
fun SolicitarScreen(sesion: Sesion) {
    val scope = rememberCoroutineScope()
    var proyectos by remember { mutableStateOf<List<ProjectLite>>(emptyList()) }
    var tipo by remember { mutableStateOf("reunion") }
    var proyecto by remember { mutableStateOf<ProjectLite?>(null) }
    var motivo by remember { mutableStateOf("") }
    val horarios = remember { mutableStateListOf<Slot>() }
    var eligiendo by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val esCliente = sesion.perfil?.role == "client"

    LaunchedEffect(Unit) {
        runCatching { Repo.projects() }.onSuccess {
            proyectos = it
            if (esCliente && it.size == 1) proyecto = it.first()
        }.onFailure { error = it.friendly() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        BarraClara("Solicitar cita", onBack = { sesion.atras() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column { Etiqueta("Tipo de actividad"); SelectorTipo(tipo, puedeAgregar = sesion.isAdmin) { tipo = it } }
            Column {
                Etiqueta("Proyecto")
                SelectorProyecto(proyectos, proyecto, permitirNinguno = !esCliente) { proyecto = it }
            }
            Aviso(
                listOfNotNull(
                    when (proyecto?.recordingMode) { "oficina" -> "Proyecto en Oficina"; "remota" -> "Proyecto remoto"; else -> null },
                    "${Kinds.label(tipo)}: se sugiere cita ${Kinds.suggestedModality(tipo)}" +
                        if (Kinds.suggestedModality(tipo) == "virtual") " por Meet o Zoom." else ".",
                ).joinToString(" · "),
            )
            Column {
                Etiqueta("Motivo")
                OutlinedTextField(
                    motivo, { motivo = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ej. Revisar los accesos a la cuenta de Meta para empezar campañas.") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiqueta("Horarios propuestos (1 a 3)")
                horarios.forEachIndexed { i, s -> FilaHorario(s, onRemove = { horarios.removeAt(i) }) }
                if (horarios.size < 3) {
                    Text(
                        "+ Agregar horario", color = Violeta, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .clickable { eligiendo = true }.padding(16.dp),
                    )
                }
            }
            ErrorTexto(error)
        }
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BotonPrincipal(
                if (enviando) "Enviando…" else "Enviar solicitud",
                onClick = {
                    enviando = true; error = null
                    scope.launch {
                        runCatching { Repo.requestAppointment(proyecto?.id, tipo, motivo.trim(), horarios.toList()) }
                            .onSuccess { sesion.reemplazar(Ruta.Detalle(it)) }
                            .onFailure { error = it.friendly() }
                        enviando = false
                    }
                },
                enabled = !enviando && horarios.isNotEmpty() && motivo.trim().length >= 3 && (!esCliente || proyecto != null),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "La solicitud no reserva el horario; el coordinador lo confirma.",
                color = TextoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (eligiendo) SelectorHorario(onDismiss = { eligiendo = false }) { horarios.add(it); eligiendo = false }
}

