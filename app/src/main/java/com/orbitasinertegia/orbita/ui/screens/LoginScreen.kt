package com.orbitasinertegia.orbita.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitasinertegia.orbita.R
import com.orbitasinertegia.orbita.data.Repo
import com.orbitasinertegia.orbita.data.friendly
import com.orbitasinertegia.orbita.ui.BotonPrincipal
import com.orbitasinertegia.orbita.ui.Sesion
import com.orbitasinertegia.orbita.ui.theme.Ambar
import com.orbitasinertegia.orbita.ui.theme.Noche
import com.orbitasinertegia.orbita.ui.theme.Violeta
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(sesion: Sesion) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(Noche).systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.size(64.dp).background(Violeta, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.logo_orbita), "Logo de Órbita",
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Órbita Citas", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Coordina reuniones, grabaciones y entrevistas de tus proyectos.", color = Color(0xFFC9C3E3), fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    email, { email = it }, label = { Text("Correo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    clave, { clave = it }, label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { verClave = !verClave }) {
                            Icon(
                                if (verClave) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (verClave) "Ocultar contraseña" else "Ver contraseña",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                BotonPrincipal(
                    if (cargando) "Entrando…" else "Iniciar sesión",
                    onClick = {
                        cargando = true; info = null
                        scope.launch { sesion.entrar(email, clave); cargando = false }
                    },
                    enabled = !cargando && email.contains("@") && clave.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        if (!email.contains("@")) { info = "Escribe tu correo arriba y vuelve a tocar aquí."; return@TextButton }
                        scope.launch {
                            info = runCatching { Repo.resetPassword(email) }
                                .fold({ "Si el correo tiene cuenta, te llegará un enlace para cambiar tu contraseña." }, { it.friendly() })
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("¿Olvidaste tu contraseña?", color = Violeta, textDecoration = TextDecoration.Underline) }
            }
        }

        val mensaje = sesion.errorLogin ?: info
        if (mensaje != null) {
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF3A2A12), RoundedCornerShape(14.dp)).padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = Ambar)
                Spacer(Modifier.size(12.dp))
                Text(mensaje, color = Color(0xFFFBE7C6), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Las cuentas se crean por invitación desde la web.", color = Color(0xFF9A99A8), fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}
