package cc.aaro.qrst.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Amber marks the direction where a key leaves this phone. Never used for "verified" (§11.2b). */
val Release = Color(0xFFF2B33D)
val Receipt = Color(0xFF7FC8C0)
val Danger = Color(0xFFE5736B)

private val scheme = darkColorScheme(
    primary = Color(0xFFE7E3DA),
    onPrimary = Color(0xFF15171A),
    secondary = Receipt,
    background = Color(0xFF101214),
    surface = Color(0xFF101214),
    surfaceVariant = Color(0xFF1B1F24),
    onSurface = Color(0xFFE7E3DA),
    onSurfaceVariant = Color(0xFFA9ADB3),
    outline = Color(0xFF4A5058),
    error = Danger,
)

@Composable
fun QrstTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
