package com.orbitasinertegia.orbita.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orbitasinertegia.orbita.data.Kinds
import com.orbitasinertegia.orbita.data.Profile
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.screens.AgendaScreen
import com.orbitasinertegia.orbita.ui.screens.AvisosScreen
import com.orbitasinertegia.orbita.ui.screens.CambioScreen
import com.orbitasinertegia.orbita.ui.screens.DetalleScreen
import com.orbitasinertegia.orbita.ui.screens.EnCursoScreen
import com.orbitasinertegia.orbita.ui.screens.LoginScreen
import com.orbitasinertegia.orbita.ui.screens.PerfilScreen
import com.orbitasinertegia.orbita.ui.screens.ProgramarScreen
import com.orbitasinertegia.orbita.ui.screens.SolicitarScreen
import com.orbitasinertegia.orbita.ui.screens.SolicitudesScreen
import com.orbitasinertegia.orbita.ui.theme.Violeta
import com.orbitasinertegia.orbita.ui.theme.VioletaClaro
import kotlinx.coroutines.launch

/** Cita que pidió abrir una notificación; la app navega a ella en cuanto hay sesión. */
object Enlaces {
    var citaPorAbrir by mutableStateOf<String?>(null)
}

sealed interface Ruta {
    data object Inicio : Ruta
    data class Detalle(val id: String) : Ruta
    data object Solicitar : Ruta
    data class Programar(val id: String?) : Ruta
    data class EnCurso(val id: String) : Ruta
    data class Cambio(val id: String, val userId: String) : Ruta
}

enum class Fase { Cargando, Login, Lista }

class Sesion : ViewModel() {
    var fase by mutableStateOf(Fase.Cargando)
    var perfil by mutableStateOf<Profile?>(null)
    var errorLogin by mutableStateOf<String?>(null)
    var tab by mutableIntStateOf(0)
    var noLeidos by mutableIntStateOf(0)
    val pila = mutableStateListOf<Ruta>(Ruta.Inicio)
    /** Sube con cada cambio de pantalla para que la pantalla visible vuelva a cargar sus datos. */
    var navegaciones by mutableIntStateOf(0)

    val myId get() = perfil?.id
    val isAdmin get() = perfil?.isAdmin == true

    init {
        viewModelScope.launch {
            val conSesion = runCatching { Repo.hasSession() }.getOrDefault(false)
            if (conSesion) {
                errorLogin = validarCuenta()
                fase = if (errorLogin == null) Fase.Lista else Fase.Login
            } else {
                fase = Fase.Login
            }
        }
    }

    /** Revisa perfil, cuenta activa y licencia. Devuelve el motivo si no puede entrar. */
    private suspend fun validarCuenta(): String? {
        val p = runCatching { Repo.myProfile() }.getOrElse { return it.friendly() }
        val motivo = when {
            p == null -> "Tu cuenta no tiene acceso. Abre tu invitación en la web de Órbita."
            p.active == false -> "Tu cuenta está desactivada. Contacta al administrador."
            // La app móvil es para clientes y administradores. El equipo trabaja desde la web.
            !p.isAdmin && p.role != "client" -> "La app móvil es solo para clientes y administradores. Entra a Órbita desde la web."
            p.isSuperAdmin != true && Repo.licenseSuspended() -> "La licencia de la agencia está suspendida. Contacta al administrador."
            else -> null
        }
        if (motivo != null) Repo.signOut() else { perfil = p; cargarTipos() }
        return motivo
    }

    suspend fun entrar(email: String, password: String) {
        errorLogin = null
        try {
            Repo.signIn(email, password)
        } catch (e: Exception) {
            errorLogin = e.friendly()
            return
        }
        errorLogin = validarCuenta()
        if (errorLogin == null) {
            pila.clear(); pila.add(Ruta.Inicio); tab = 0
            fase = Fase.Lista
        }
    }

    /** Entrar con la cuenta de Google del teléfono (Credential Manager + token con nonce). */
    suspend fun entrarConGoogle(context: android.content.Context) {
        errorLogin = null
        val rawNonce = java.util.UUID.randomUUID().toString()
        val hashed = java.security.MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        // Selector completo de cuentas de Google ("Iniciar sesión con Google"): muestra todas las cuentas
        // del teléfono y permite agregar una, no solo las que ya usaron la app.
        val opcion = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
            .Builder(com.orbitasinertegia.orbita.BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(hashed)
            .build()
        val pedido = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(opcion).build()
        try {
            val resultado = androidx.credentials.CredentialManager.create(context).getCredential(context, pedido)
            val google = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(resultado.credential.data)
            Repo.signInWithGoogle(google.idToken, rawNonce)
        } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
            return
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            // Android responde "sin credenciales" también cuando la app no está dada de alta en Google Cloud
            // (ID de cliente Android con el paquete y la huella SHA-1 de la firma).
            android.util.Log.w("Orbita", "Google: ${e.type} ${e.message}")
            errorLogin = "No se pudo entrar con Google. Si el problema sigue, el administrador debe dar de alta la app en Google Cloud. Mientras tanto, entra con tu correo."
            return
        } catch (e: Exception) {
            errorLogin = e.friendly()
            return
        }
        errorLogin = validarCuenta()
        if (errorLogin == null) {
            pila.clear(); pila.add(Ruta.Inicio); tab = 0
            fase = Fase.Lista
        }
    }

    fun salir() {
        viewModelScope.launch {
            // El teléfono deja de recibir avisos de esta cuenta.
            runCatching { com.orbitasinertegia.orbita.avisos.PushService.quitar() }
            Repo.signOut()
            perfil = null
            fase = Fase.Login
        }
    }

    fun ir(ruta: Ruta) { pila.add(ruta); navegaciones++ }
    fun reemplazar(ruta: Ruta) { if (pila.size > 1) pila.removeAt(pila.lastIndex); pila.add(ruta); navegaciones++ }
    fun atras() { if (pila.size > 1) pila.removeAt(pila.lastIndex); navegaciones++ }

    fun cargarTipos() {
        viewModelScope.launch {
            runCatching { Repo.kinds() }.onSuccess { if (it.isNotEmpty()) Kinds.lista = it }
        }
    }

    /** Nombre corto para saludar: el primer nombre, o la parte del correo antes de la @. */
    val saludo get() = perfil?.fullName?.trim()?.takeIf { it.isNotBlank() && !it.contains('@') }?.substringBefore(' ')
        ?: perfil?.email?.substringBefore('@') ?: ""

    fun contarAvisos() {
        viewModelScope.launch {
            noLeidos = runCatching { Repo.notices().count { !it.read } }.getOrDefault(noLeidos)
        }
    }
}

@Composable
fun OrbitaApp(sesion: Sesion = viewModel()) {
    when (sesion.fase) {
        Fase.Cargando -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Violeta)
        }
        Fase.Login -> LoginScreen(sesion)
        Fase.Lista -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            val permiso = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { com.orbitasinertegia.orbita.avisos.AvisosWorker.programar(context) }
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= 33 && !com.orbitasinertegia.orbita.avisos.Notificaciones.permitidas(context)) {
                    permiso.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    com.orbitasinertegia.orbita.avisos.AvisosWorker.programar(context)
                }
            }
            // Push: registra este teléfono con la cuenta (si el teléfono tiene Google Play).
            LaunchedEffect(sesion.perfil?.id) { runCatching { com.orbitasinertegia.orbita.avisos.PushService.registrar() } }
            LaunchedEffect(Enlaces.citaPorAbrir) {
                Enlaces.citaPorAbrir?.let { sesion.ir(Ruta.Detalle(it)); Enlaces.citaPorAbrir = null }
            }
            BackHandler(enabled = sesion.pila.size > 1) { sesion.atras() }
            val ruta = sesion.pila.last()
            key(ruta, sesion.navegaciones) {
                when (ruta) {
                    Ruta.Inicio -> Inicio(sesion)
                    is Ruta.Detalle -> DetalleScreen(sesion, ruta.id)
                    Ruta.Solicitar -> SolicitarScreen(sesion)
                    is Ruta.Programar -> ProgramarScreen(sesion, ruta.id)
                    is Ruta.EnCurso -> EnCursoScreen(sesion, ruta.id)
                    is Ruta.Cambio -> CambioScreen(sesion, ruta.id, ruta.userId)
                }
            }
        }
    }
}

@Composable
private fun Inicio(sesion: Sesion) {
    LaunchedEffect(sesion.tab) { sesion.contarAvisos() }
    val pestañas = listOf(
        Triple("Agenda", Icons.Outlined.CalendarMonth, 0),
        Triple("Solicitudes", Icons.AutoMirrored.Outlined.Chat, 1),
        Triple("Avisos", Icons.Outlined.Notifications, 2),
        Triple("Perfil", Icons.Outlined.Person, 3),
    )
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                pestañas.forEach { (texto, icono, i) ->
                    NavigationBarItem(
                        selected = sesion.tab == i,
                        onClick = { sesion.tab = i },
                        icon = {
                            if (i == 2 && sesion.noLeidos > 0) {
                                BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("${sesion.noLeidos}") } }) {
                                    Icon(icono, texto)
                                }
                            } else Icon(icono, texto)
                        },
                        label = { Text(texto) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Violeta, selectedTextColor = Violeta, indicatorColor = VioletaClaro,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (sesion.tab) {
                0 -> AgendaScreen(sesion)
                1 -> SolicitudesScreen(sesion)
                2 -> AvisosScreen(sesion)
                else -> PerfilScreen(sesion)
            }
        }
    }
}
