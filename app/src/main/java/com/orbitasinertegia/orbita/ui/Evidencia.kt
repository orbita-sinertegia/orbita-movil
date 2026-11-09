package com.orbitasinertegia.orbita.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.orbitasinertegia.orbita.data.AppointmentFile
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.theme.Fondo
import com.orbitasinertegia.orbita.ui.theme.Indigo
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.TextoSuave
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private const val MAXIMO = 10 * 1024 * 1024

/** Archivo elegido, ya listo para subir. */
private class Pendiente(val bytes: ByteArray, val nombre: String, val mime: String)

/**
 * Fotos y PDF de una cita. Los admins suben (cámara o archivo) y borran; los demás solo ven
 * lo que la base les deja ver (los clientes, solo lo marcado como visible para ellos).
 */
@Composable
fun EvidenciaGaleria(appointmentId: String, puedeEditar: Boolean, mostrarVacio: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var archivos by remember { mutableStateOf<List<AppointmentFile>>(emptyList()) }
    var recarga by remember { mutableIntStateOf(0) }
    var subiendo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendiente by remember { mutableStateOf<Pendiente?>(null) }
    var fotoUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(recarga) {
        runCatching { Repo.files(appointmentId) }.onSuccess { archivos = it }.onFailure { error = it.friendly() }
    }

    fun preparar(uri: Uri) {
        scope.launch {
            error = null
            val listo = withContext(Dispatchers.IO) { runCatching { leer(context, uri) } }
            listo.onSuccess { pendiente = it }.onFailure { error = it.message ?: "No se pudo leer el archivo." }
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) fotoUri?.let { preparar(it) }
    }
    val archivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) preparar(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val celdas: List<@Composable () -> Unit> = archivos.map { a ->
            @Composable {
                Miniatura(a, puedeEditar,
                    onAbrir = {
                        scope.launch {
                            runCatching { Repo.evidenceUrl(a.path) }
                                .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
                                .onFailure { error = it.friendly() }
                        }
                    },
                    onBorrar = {
                        scope.launch {
                            runCatching { Repo.deleteEvidence(a.id) }.onFailure { error = it.friendly() }
                            recarga++
                        }
                    },
                )
            }
        } + if (puedeEditar) listOf(
            @Composable {
                Agregar(Icons.Outlined.PhotoCamera, "Cámara", enabled = !subiendo) {
                    val carpeta = File(context.cacheDir, "evidencia").apply { mkdirs() }
                    val destino = File(carpeta, "foto_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fotos", destino)
                    fotoUri = uri
                    camara.launch(uri)
                }
            },
            @Composable {
                Agregar(Icons.Outlined.AttachFile, "Foto o PDF", enabled = !subiendo) {
                    archivo.launch(arrayOf("image/*", "application/pdf"))
                }
            },
        ) else emptyList()

        if (celdas.isEmpty() && mostrarVacio) {
            Text("Todavía no hay evidencia.", color = TextoSuave, fontSize = 14.sp)
        }
        celdas.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { celda -> Box(Modifier.weight(1f)) { celda() } }
                repeat(3 - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (subiendo) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Violeta, strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
            Text("Subiendo…", color = TextoSuave, fontSize = 13.sp)
        }
        if (puedeEditar) Text("JPG, PNG, WEBP o PDF · máximo 10 MB", color = TextoSuave, fontSize = 12.sp)
        ErrorTexto(error)
    }

    pendiente?.let { p ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("¿Quién puede verla?") },
            text = { Text("${p.nombre}\n\nInterna: solo administración y equipo invitado.\nCliente: también los clientes invitados a la cita.") },
            confirmButton = {
                TextButton(onClick = {
                    pendiente = null; subiendo = true
                    scope.launch {
                        runCatching { Repo.uploadEvidence(appointmentId, p.bytes, p.nombre, p.mime, true) }.onFailure { error = it.friendly() }
                        subiendo = false; recarga++
                    }
                }) { Text("Visible para el cliente") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendiente = null; subiendo = true
                    scope.launch {
                        runCatching { Repo.uploadEvidence(appointmentId, p.bytes, p.nombre, p.mime, false) }.onFailure { error = it.friendly() }
                        subiendo = false; recarga++
                    }
                }) { Text("Interna") }
            },
        )
    }
}

@Composable
private fun Miniatura(a: AppointmentFile, puedeBorrar: Boolean, onAbrir: () -> Unit, onBorrar: () -> Unit) {
    var imagen by remember(a.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(a.id) {
        if (a.esImagen) imagen = withContext(Dispatchers.IO) {
            runCatching { reducir(Repo.downloadEvidence(a.path), 360)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Fondo).clickable(onClick = onAbrir),
    ) {
        val img = imagen
        if (img != null) {
            Image(img, a.fileName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (a.esImagen) CircularProgressIndicator(Modifier.size(20.dp), color = Violeta, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = Indigo)
                    Text(a.fileName, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        Text(
            if (a.visibleToClient) "Cliente" else "Interna",
            color = if (a.visibleToClient) Indigo else Noche, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(if (a.visibleToClient) VioletaClaro else Color.White, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (puedeBorrar) Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).background(Color(0xAA000000), CircleShape).clickable(onClick = onBorrar),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Close, "Borrar archivo", tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun Agregar(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .border(1.dp, Violeta.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icono, null, tint = Violeta)
        Text(texto, color = Violeta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Lee el archivo; las fotos se reducen a 1920 px en JPEG para que suban rápido y pesen poco. */
private fun leer(context: Context, uri: Uri): Pendiente {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    var nombre = "evidencia"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) nombre = c.getString(0) ?: nombre
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo.")
    if (mime == "application/pdf") {
        if (bytes.size > MAXIMO) error("El PDF pesa más de 10 MB.")
        return Pendiente(bytes, nombre, mime)
    }
    if (!mime.startsWith("image/")) error("Solo se aceptan fotos o PDF.")
    val reducida = reducir(bytes, 1920) ?: error("No se pudo abrir la foto.")
    val salida = ByteArrayOutputStream()
    reducida.compress(Bitmap.CompressFormat.JPEG, 85, salida)
    val jpg = salida.toByteArray()
    if (jpg.size > MAXIMO) error("La foto pesa más de 10 MB.")
    return Pendiente(jpg, nombre.substringBeforeLast('.') + ".jpg", "image/jpeg")
}

private fun reducir(bytes: ByteArray, maximo: Int): Bitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    var escala = 1
    while (medidas.outWidth / (escala * 2) >= maximo || medidas.outHeight / (escala * 2) >= maximo) escala *= 2
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = escala }) ?: return null
    val mayor = maxOf(bmp.width, bmp.height)
    if (mayor <= maximo) return bmp
    val factor = maximo.toFloat() / mayor
    return Bitmap.createScaledBitmap(bmp, (bmp.width * factor).toInt(), (bmp.height * factor).toInt(), true)
}

