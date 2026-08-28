package hu.konyvtar.tts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF00390D),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFA5D6A7),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB5B5B5),
    outline = Color(0xFF3A3A3A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF33691E),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF484848),
    outline = Color(0xFFBDBDBD)
)

/** Sűrű, adattáblához illő tipográfia — Total Commander hangulat. */
private val DenseTypography = Typography(
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp)
)

@Composable
fun KonyvtarTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = DenseTypography,
        content = content
    )
}
