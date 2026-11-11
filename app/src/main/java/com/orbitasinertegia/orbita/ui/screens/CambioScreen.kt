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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.data.Appointment
import com.orbitasinertegia.orbita.data.Fechas
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.BotonSecundario
import com.orbitasinertegia.orbita.ui.DialogoTexto
import com.orbitasinertegia.orbita.ui.ErrorTexto
import com.orbitasinertegia.orbita.ui.EtiquetaRol
import com.orbitasinertegia.orbita.ui.InicialesRol
import com.orbitasinertegia.orbita.ui.Ruta
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.theme.Fondo
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.Peligro
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

/** Revisar el cambio de horario que pide un invitado (solo admins). */
@Composable
fun CambioScreen(sesion: Sesion, id: String, userId: String) {
    val scope = rememberCoroutineScope()
    var cita by remember { mutableStateOf<Appointment?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elegido by remember { mutableIntStateOf(0) }
    var rechazando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { cita = Repo.appointment(id) }.onFailure { error = it.friendly() }
        cargando = false
    }

    fun resolver(aceptar: Boolean, nota: String?) {
        ocupado = true; error = null
        scope.launch {
            runCatching { Repo.resolveChange(id, userId, aceptar, elegido, nota) }
                .onSuccess {
                    sesion.atras()
                    if (sesion.pila.lastOrNull() == Ruta.Detalle(id)) sesion.atras()
                    sesion.ir(Ruta.Detalle(id))
                }
                .onFailure { error = it.friendly() }
            ocupado = false
        }
    }

    val c = cita
    val pide = c?.participants?.firstOrNull { it.userId == userId }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BarraClara("Cambio de horario", onBack = { sesion.atras() }) { EtiquetaRol("Administrador") }
        if (cargando) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Violeta) }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ErrorTexto(error)
            if (c == null || pide == null) {
                Text("Este cambio ya no está pendiente.", color = TextoSuave)
                return@Column
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text(c.title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(
                    listOfNotNull(Kinds.label(c.kind), c.modality?.replaceFirstChar { it.uppercase() }, c.project?.name?.let { "Proyecto $it" })
                        .joinToString(" · "),
                    color = TextoSuave, fontSize = 15.sp,
                )
                Fechas.parse(c.startsAt)?.let {
                    Text("Actual: ${Fechas.diaCorto(it)} · ${Fechas.hora(it)}", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFF2C4CB), RoundedCornerShape(18.dp)).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InicialesRol(pide.user?.name ?: "?", pide.user?.role)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(pide.user?.name ?: "Participante", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(if (pide.response == "cambio_solicitado") "Pide cambio" else "Ya respondió", color = Peligro, fontSize = 14.sp)
                    }
                }
                if (!pide.responseNote.isNullOrBlank()) {
                    Text(
                        "“${pide.responseNote}”", fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().background(Fondo, RoundedCornerShape(12.dp)).padding(14.dp),
                    )
                }
                if (pide.proposedSlots.isNotEmpty()) {
                    Text("Propone:", color = Indigo, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    pide.proposedSlots.forEachIndexed { i, slot ->
                        val activo = i == elegido
                        Text(
                            "· " + Fechas.rango(slot), fontSize = 16.sp,
                            fontWeight = if (activo && pide.proposedSlots.size > 1) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (activo && pide.proposedSlots.size > 1) VioletaClaro else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { elegido = i }.padding(8.dp),
                        )
                    }
                    if (pide.proposedSlots.size > 1) Text("Toca el horario que quieres aceptar.", color = TextoSuave, fontSize = 13.sp)
                }
            }
        }
        if (c != null && pide?.response == "cambio_solicitado") Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotonPrincipal("Aceptar el nuevo horario", { resolver(true, null) }, Modifier.fillMaxWidth(), enabled = !ocupado && pide.proposedSlots.isNotEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotonSecundario("Reprogramar", { sesion.ir(Ruta.Programar(id)) }, Modifier.weight(1f), enabled = !ocupado)
                OutlinedButton(
                    onClick = { rechazando = true }, enabled = !ocupado,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF2C4CB)),
                ) { Text("Rechazar", color = Peligro, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }

    if (rechazando) DialogoTexto(
        "Rechazar el cambio", "Mensaje para ${pide?.user?.name ?: "la persona"} (opcional)", "Rechazar", minimo = 0,
        onDismiss = { rechazando = false },
    ) { nota -> rechazando = false; resolver(false, nota.ifBlank { null }) }
}
