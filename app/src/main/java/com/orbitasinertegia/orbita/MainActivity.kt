package com.orbitasinertegia.orbita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.orbitasinertegia.orbita.avisos.Notificaciones
import com.orbitasinertegia.orbita.ui.Enlaces
import com.orbitasinertegia.orbita.ui.OrbitaApp
import com.orbitasinertegia.orbita.ui.theme.OrbitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notificaciones.crearCanales(this)
        abrirDesde(intent)
        setContent {
            // La app usa siempre la paleta clara de Órbita, como los mockups.
            OrbitaTheme(darkTheme = false) { OrbitaApp() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirDesde(intent)
    }

    /** Si se abrió desde una notificación de cita, la app va directo a su detalle. */
    private fun abrirDesde(intent: Intent?) {
        intent?.getStringExtra(Notificaciones.EXTRA_CITA)?.let { Enlaces.citaPorAbrir = it }
    }
}
