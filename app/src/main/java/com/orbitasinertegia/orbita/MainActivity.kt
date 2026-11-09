package com.orbitasinertegia.orbita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.orbitasinertegia.orbita.ui.OrbitaApp
import com.orbitasinertegia.orbita.ui.theme.OrbitaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // La app usa siempre la paleta clara de Órbita, como los mockups.
            OrbitaTheme(darkTheme = false) { OrbitaApp() }
        }
    }
}
