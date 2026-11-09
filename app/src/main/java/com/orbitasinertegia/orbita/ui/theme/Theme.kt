package com.orbitasinertegia.orbita.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Violeta,
    onPrimary = Color.White,
    primaryContainer = VioletaClaro,
    onPrimaryContainer = Indigo,
    secondary = Lila,
    onSecondary = Color.White,
    secondaryContainer = LilaClaro,
    onSecondaryContainer = Indigo,
    tertiary = Ambar,
    onTertiary = Noche,
    tertiaryContainer = Crema,
    onTertiaryContainer = Noche,
    background = Fondo,
    onBackground = Noche,
    surface = Color.White,
    onSurface = Noche,
    surfaceVariant = Fondo,
    onSurfaceVariant = TextoSuave,
    outline = Linea,
    error = Peligro,
)

private val DarkColorScheme = darkColorScheme(
    primary = VioletaSuave,
    onPrimary = Noche,
    primaryContainer = Indigo,
    onPrimaryContainer = VioletaClaro,
    secondary = Lila,
    onSecondary = Noche,
    tertiary = Ambar,
    onTertiary = Noche,
    tertiaryContainer = Crema,
    onTertiaryContainer = Noche,
    background = Noche,
    onBackground = Color.White,
    surface = Color(0xFF24233A),
    onSurface = Color.White,
    surfaceVariant = Indigo,
    onSurfaceVariant = Color(0xFFC9C3E3),
    outline = Color(0xFF3B3A55),
    error = Color(0xFFFF8A9B),
)

// Sin colores dinámicos de Android 12: la app siempre usa la paleta de Órbita.
@Composable
fun OrbitaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
