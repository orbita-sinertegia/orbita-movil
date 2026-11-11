package com.orbitasinertegia.orbita.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Agreement
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BarraOscura
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.BotonSecundario
import com.orbitasinertegia.orbita.ui.DialogoAcuerdo
import com.orbitasinertegia.orbita.ui.DialogoTexto
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.Tarjeta
import com.orbitasinertegia.orbita.ui.Titulo
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Fondo
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import kotlinx.coroutines.launch

@Composable
fun EnCursoScreen(sesion: Sesion, id: String) {
    val scope = rememberCoroutineScope()
    var cita by remember { mutableStateOf<Appointment?>(null) }
    var acuerdos by remember { mutableStateOf<List<Agreement>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var dialogo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recarga) {
        runCatching { cita = Repo.appointment(id); acuerdos = Repo.agreements(id) }.onFailure { error = it.friendly() }
        cargando = false
    }

    fun accion(alTerminar: () -> Unit = {}, bloque: suspend () -> Unit) {
        ocupado = true; error = null
        scope.launch {
            runCatching { bloque() }.onFailure { error = it.friendly() }.onSuccess { recarga++; alTerminar() }
            ocupado = false
        }
    }

    val c = cita
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BarraOscura(c?.title ?: "Cita en curso", onBack = { sesion.atras() }) {
            val inicio = Fechas.parse(c?.startedAt)
            if (c?.status == "en_curso") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(8.dp).background(Ambar, CircleShape))
                Spacer(Modifier.size(8.dp))
                Text("En curso" + (inicio?.let { " · inició ${Fechas.hora(it)}" } ?: ""), color = Ambar, fontWeight = FontWeight.SemiBold)
            }
        }
        if (cargando) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Violeta) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ErrorTexto(error)
            if (c == null) return@Column
            if (c.status != "en_curso") {
                Text("Esta cita ya no está en curso.", color = TextoSuave)
                return@Column
            }

            Tarjeta {
                Titulo("Asistencia")
                c.participants.filter { it.userId != c.coordinatorId }.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.user?.name ?: "Participante", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.background(Fondo, RoundedCornerShape(12.dp)).padding(3.dp)) {
                            listOf("presente" to "Presente", "ausente" to "Ausente").forEach { (valor, texto) ->
                                val elegido = p.attendance == valor
                                Text(
                                    texto, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                    color = if (elegido) Color.White else TextoSuave,
                                    modifier = Modifier.background(if (elegido) Violeta else Color.Transparent, RoundedCornerShape(10.dp))
                                        .clickable(enabled = !ocupado) { accion { Repo.setAttendance(c.id, p.userId, valor) } }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            Tarjeta {
                Titulo("Evidencia")
                Row(
                    Modifier.fillMaxWidth().background(com.orbitasinertegia.orbita.ui.theme.Crema, RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.PhotoCamera, null, tint = Color(0xFF8A4B00))
                    Spacer(Modifier.size(12.dp))
                    Text("La subida de fotos y PDF llegará en la siguiente versión.", color = Color(0xFF8A4B00), fontSize = 15.sp)
                }
            }

            Tarjeta {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Titulo("Acuerdos") }
                    TextButton(onClick = { dialogo = "acuerdo" }, enabled = !ocupado) { Text("+ Agregar", color = Violeta, fontWeight = FontWeight.SemiBold) }
                }
                if (acuerdos.isEmpty()) Text("Aún no hay acuerdos.", color = TextoSuave, fontSize = 14.sp)
                acuerdos.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Fondo, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.body, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (a.visibleToClient) "Visible para el cliente" else "Interno",
                                color = if (a.visibleToClient) Violeta else TextoSuave, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        IconButton(onClick = { accion { Repo.deleteAgreement(a.id) } }, enabled = !ocupado) {
                            Icon(Icons.Outlined.Delete, "Borrar acuerdo", tint = TextoSuave)
                        }
                    }
                }
            }
        }

        if (c?.status == "en_curso") Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BotonSecundario("Incompleta", { dialogo = "incompleta" }, Modifier.weight(1f), enabled = !ocupado)
            BotonPrincipal("Cerrar realizada", { dialogo = "realizada" }, Modifier.weight(1f), enabled = !ocupado)
        }
    }

    when (dialogo) {
        "acuerdo" -> DialogoAcuerdo(onDismiss = { dialogo = null }) { texto, visible ->
            dialogo = null; accion { Repo.addAgreement(id, texto, visible) }
        }
        "incompleta" -> DialogoTexto("Cerrar como incompleta", "¿Qué faltó?", "Cerrar", onDismiss = { dialogo = null }) { nota ->
            dialogo = null; accion(alTerminar = { sesion.atras() }) { Repo.close(id, "incompleta", nota) }
        }
        "realizada" -> AlertDialog(
            onDismissRequest = { dialogo = null },
            title = { Text("Cerrar cita como realizada") },
            text = { Text("Se avisará a los participantes y podrán ver los acuerdos visibles para ellos.") },
            confirmButton = {
                TextButton(onClick = { dialogo = null; accion(alTerminar = { sesion.atras() }) { Repo.close(id, "realizada", null) } }) { Text("Cerrar realizada") }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Cancelar") } },
        )
    }
}
