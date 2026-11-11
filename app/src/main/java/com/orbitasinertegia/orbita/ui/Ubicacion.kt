package com.orbitasinertegia.orbita.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.ContextCompat
import com.orbitasinertegia.orbita.data.Lugar
import com.orbitasinertegia.orbita.data.Place
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Lo que se va eligiendo en el formulario de lugar. */
class EstadoLugar(tipo: String) {
    var tipo by mutableStateOf(tipo)          // "estudio", "cliente" u "otro"
    var sitio by mutableStateOf<Place?>(null)
    /** Estudio que ya traía la cita; se elige en cuanto carga la lista. */
    var sitioInicial: String? = null
    var nombre by mutableStateOf("")
    var direccion by mutableStateOf("")
    var lat by mutableStateOf<Double?>(null)
    var lng by mutableStateOf<Double?>(null)

    fun aLugar() = Lugar(
        tipo = tipo,
        placeId = if (tipo == "estudio") sitio?.id else null,
        nombre = nombre.trim().ifBlank { null },
        direccion = direccion.trim().ifBlank { null },
        lat = if (tipo == "estudio") null else lat,
        lng = if (tipo == "estudio") null else lng,
    )

    /** Al programar (estricto) el estudio debe estar elegido; al solicitar puede quedar "por confirmar". */
    fun completo(estricto: Boolean) = when (tipo) {
        "estudio" -> !estricto || sitio != null
        "cliente" -> direccion.trim().length >= 5 || lat != null
        else -> nombre.trim().length >= 2
    }
}

/**
 * Dónde será la cita presencial.
 * Cliente (al solicitar): "En el estudio" o "En mi domicilio u otra dirección".
 * Admin (al programar): estudio de la agencia, domicilio del cliente u otro lugar; puede agregar estudios.
 */
@Composable
fun SelectorLugar(estado: EstadoLugar, esAdmin: Boolean, alSolicitar: Boolean) {
    val scope = rememberCoroutineScope()
    var sitios by remember { mutableStateOf<List<Place>>(emptyList()) }
    var recarga by remember { mutableStateOf(0) }
    var nuevoSitio by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recarga) {
        runCatching { Repo.places() }.onSuccess { lista ->
            sitios = lista
            if (estado.sitio == null) estado.sitio = lista.firstOrNull { it.id == estado.sitioInicial }
            if (estado.sitio == null && esAdmin && lista.size == 1 && estado.tipo == "estudio") estado.sitio = lista.first()
        }
    }

    val opciones = if (alSolicitar) listOf("estudio" to "En el estudio", "cliente" to "En mi domicilio")
    else listOf("estudio" to "Estudio", "cliente" to "Domicilio del cliente", "otro" to "Otro lugar")

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFFE4E2EE), RoundedCornerShape(14.dp)).padding(4.dp)) {
            opciones.forEach { (valor, texto) ->
                val elegido = estado.tipo == valor
                Text(
                    texto, textAlign = TextAlign.Center, fontSize = 14.sp,
                    fontWeight = if (elegido) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                        .background(if (elegido) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable { estado.tipo = valor }.padding(vertical = 12.dp),
                )
            }
        }

        when (estado.tipo) {
            "estudio" -> {
                if (sitios.isEmpty() && !esAdmin) {
                    Text("La agencia te confirmará en qué estudio será.", color = TextoSuave, fontSize = 14.sp)
                } else {
                    ListaSitios(sitios, estado.sitio, permitirNinguno = alSolicitar) { estado.sitio = it }
                    estado.sitio?.address?.let { Text(it, color = TextoSuave, fontSize = 13.sp) }
                }
                if (esAdmin) Text(
                    "+ Agregar estudio o lugar de la agencia", color = Violeta, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { nuevoSitio = true }.padding(vertical = 4.dp),
                )
            }
            "cliente" -> {
                OutlinedTextField(
                    estado.direccion, { estado.direccion = it.take(300) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (alSolicitar) "Dirección (calle, número, colonia, ciudad)" else "Dirección del cliente") },
                )
                if (alSolicitar) BotonUbicacionActual { lat, lng, dir ->
                    estado.lat = lat; estado.lng = lng
                    if (estado.direccion.isBlank() && dir != null) estado.direccion = dir
                }
                if (estado.lat != null) Text("📍 Ubicación del teléfono agregada", color = Indigo, fontSize = 13.sp)
            }
            else -> {
                OutlinedTextField(estado.nombre, { estado.nombre = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre del lugar") }, placeholder = { Text("Ej. Oficina del cliente") })
                OutlinedTextField(estado.direccion, { estado.direccion = it.take(300) }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Dirección (opcional)") })
            }
        }
        ErrorTexto(error)
    }

    if (nuevoSitio) DialogoNuevoSitio(onDismiss = { nuevoSitio = false }) { nombre, dir, lat, lng ->
        nuevoSitio = false; error = null
        scope.launch {
            runCatching { Repo.addPlace(nombre, dir, lat, lng) }
                .onSuccess { id ->
                    sitios = runCatching { Repo.places() }.getOrDefault(sitios)
                    estado.sitio = sitios.firstOrNull { it.id == id }
                }
                .onFailure { error = it.friendly() }
        }
    }
}

@Composable
private fun ListaSitios(sitios: List<Place>, elegido: Place?, permitirNinguno: Boolean, onElegir: (Place?) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable { abierto = true }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(elegido?.name ?: if (permitirNinguno) "Que la agencia elija el estudio" else "Elige el estudio", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
            if (permitirNinguno) DropdownMenuItem(text = { Text("Que la agencia elija el estudio") }, onClick = { onElegir(null); abierto = false })
            if (sitios.isEmpty()) DropdownMenuItem(text = { Text("Aún no hay estudios dados de alta", color = TextoSuave) }, onClick = { abierto = false })
            sitios.forEach { s ->
                DropdownMenuItem(
                    text = { Column { Text(s.name); s.address?.let { Text(it, color = TextoSuave, fontSize = 12.sp) } } },
                    onClick = { onElegir(s); abierto = false },
                )
            }
        }
    }
}

@Composable
private fun DialogoNuevoSitio(onDismiss: () -> Unit, onConfirm: (String, String?, Double?, Double?) -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo estudio o lugar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nombre, { nombre = it.take(80) }, singleLine = true, label = { Text("Nombre") }, placeholder = { Text("Ej. Bunker") })
                OutlinedTextField(direccion, { direccion = it.take(300) }, minLines = 2, label = { Text("Dirección") })
                Text("Si estás en el lugar, puedes guardar su ubicación exacta:", color = TextoSuave, fontSize = 13.sp)
                BotonUbicacionActual { la, ln, dir -> lat = la; lng = ln; if (direccion.isBlank() && dir != null) direccion = dir }
                if (lat != null) Text("📍 Ubicación guardada", color = Indigo, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nombre.trim(), direccion.trim().ifBlank { null }, lat, lng) },
                enabled = nombre.trim().length >= 2 && (direccion.trim().length >= 5 || lat != null),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Pide permiso de ubicación, toma la posición del teléfono y, si se puede, su dirección. */
@Composable
fun BotonUbicacionActual(onUbicacion: (Double, Double, String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    fun buscar() {
        buscando = true; aviso = null
        scope.launch {
            val punto = ubicacionActual(context)
            if (punto == null) {
                aviso = "No se pudo obtener la ubicación. Activa el GPS o escribe la dirección."
            } else {
                val dir = withContext(Dispatchers.IO) { direccionDe(context, punto.first, punto.second) }
                onUbicacion(punto.first, punto.second, dir)
            }
            buscando = false
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) buscar() else aviso = "Sin permiso de ubicación. Escribe la dirección."
    }

    Row(
        Modifier.border(1.dp, Violeta.copy(alpha = 0.5f), RoundedCornerShape(20.dp)).clickable(enabled = !buscando) {
            val concedido = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
            if (concedido) buscar()
            else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (buscando) CircularProgressIndicator(Modifier.size(18.dp), color = Violeta, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.MyLocation, null, tint = Violeta)
        Spacer(Modifier.size(8.dp))
        Text(if (buscando) "Buscando…" else "Usar mi ubicación actual", color = Violeta, fontWeight = FontWeight.SemiBold)
    }
    aviso?.let { Text(it, color = TextoSuave, fontSize = 13.sp) }
}

@SuppressLint("MissingPermission")
private suspend fun ubicacionActual(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val proveedor = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { lm.isProviderEnabled(it) }
        ?: return null
    val actual = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { cont ->
            lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(context)) { loc -> cont.resume(loc) }
        }
    } ?: lm.getLastKnownLocation(proveedor)
    return actual?.let { it.latitude to it.longitude }
}

@Suppress("DEPRECATION")
private fun direccionDe(context: Context, lat: Double, lng: Double): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    Geocoder(context, java.util.Locale.forLanguageTag("es-MX")).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
}.getOrNull()

