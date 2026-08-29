package hu.konyvtar.tts.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.R

/** Egy választható színséma világos és sötét változata. */
data class AppScheme(
    val id: String,
    val nameRes: Int,
    val light: ColorScheme,
    val dark: ColorScheme
)

/**
 * A megjelenés futásidejű állapota. A beállítások képernyő ezt írja,
 * a téma pedig innen olvas — így a váltás azonnal látszik.
 */
object ThemeState {
    var mode by mutableStateOf("system")
    var schemeId by mutableStateOf("klasszikus")
    var uiScale by mutableFloatStateOf(1.0f)

    fun load(context: Context) {
        mode = Prefs.themeMode(context)
        schemeId = Prefs.colorScheme(context)
        uiScale = Prefs.uiScale(context)
    }
}

/** A fejezetek elé tett feltűnő sáv színe (minden sémában erős piros). */
val ChapterBandColor = Color(0xFFB3131C)

private fun scheme(
    primary: Color,
    onPrimary: Color,
    container: Color,
    onContainer: Color,
    dark: Boolean,
    background: Color,
    surfaceVariant: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    outline: Color,
    tertiary: Color,
    tertiaryContainer: Color
): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary,
        tertiary = tertiary,
        tertiaryContainer = tertiaryContainer,
        background = background,
        onBackground = onSurface,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline
    )
}

/** A választható színsémák. Új séma felvétele egyetlen bejegyzés. */
val APP_SCHEMES: List<AppScheme> = listOf(
    AppScheme(
        id = "klasszikus",
        nameRes = R.string.scheme_classic,
        light = scheme(
            primary = Color(0xFF1B5E20), onPrimary = Color.White,
            container = Color(0xFFC8E6C9), onContainer = Color(0xFF00210A),
            dark = false, background = Color(0xFFFAFAFA),
            surfaceVariant = Color(0xFFEEEEEE), onSurface = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFF484848), outline = Color(0xFFBDBDBD),
            tertiary = Color(0xFF6A4C00), tertiaryContainer = Color(0xFFFFE082)
        ),
        dark = scheme(
            primary = Color(0xFF81C784), onPrimary = Color(0xFF00390D),
            container = Color(0xFF1B5E20), onContainer = Color(0xFFC8E6C9),
            dark = true, background = Color(0xFF121212),
            surfaceVariant = Color(0xFF1E1E1E), onSurface = Color(0xFFE6E6E6),
            onSurfaceVariant = Color(0xFFB5B5B5), outline = Color(0xFF3A3A3A),
            tertiary = Color(0xFFFFCC80), tertiaryContainer = Color(0xFF5A4000)
        )
    ),
    AppScheme(
        id = "tenger",
        nameRes = R.string.scheme_ocean,
        light = scheme(
            primary = Color(0xFF0D47A1), onPrimary = Color.White,
            container = Color(0xFFBBDEFB), onContainer = Color(0xFF001B3D),
            dark = false, background = Color(0xFFF7F9FC),
            surfaceVariant = Color(0xFFE8EDF4), onSurface = Color(0xFF16191D),
            onSurfaceVariant = Color(0xFF44505F), outline = Color(0xFFB3C0D0),
            tertiary = Color(0xFF00695C), tertiaryContainer = Color(0xFFB2DFDB)
        ),
        dark = scheme(
            primary = Color(0xFF82B1FF), onPrimary = Color(0xFF002B60),
            container = Color(0xFF0D47A1), onContainer = Color(0xFFD6E4FF),
            dark = true, background = Color(0xFF0E1116),
            surfaceVariant = Color(0xFF1A1F26), onSurface = Color(0xFFE3E7EC),
            onSurfaceVariant = Color(0xFFAAB6C4), outline = Color(0xFF39424D),
            tertiary = Color(0xFF80CBC4), tertiaryContainer = Color(0xFF004D40)
        )
    ),
    AppScheme(
        id = "szepia",
        nameRes = R.string.scheme_sepia,
        light = scheme(
            primary = Color(0xFF7B4B1E), onPrimary = Color.White,
            container = Color(0xFFEBD9BF), onContainer = Color(0xFF2B1A08),
            dark = false, background = Color(0xFFF6EEE0),
            surfaceVariant = Color(0xFFEDE2CE), onSurface = Color(0xFF2A2115),
            onSurfaceVariant = Color(0xFF5C4B36), outline = Color(0xFFC9B79B),
            tertiary = Color(0xFF6D4C41), tertiaryContainer = Color(0xFFE0C9A6)
        ),
        dark = scheme(
            primary = Color(0xFFD7B486), onPrimary = Color(0xFF3A2611),
            container = Color(0xFF5A3E22), onContainer = Color(0xFFF0DFC6),
            dark = true, background = Color(0xFF1A1611),
            surfaceVariant = Color(0xFF262019), onSurface = Color(0xFFE8DFD0),
            onSurfaceVariant = Color(0xFFBAAC97), outline = Color(0xFF463D31),
            tertiary = Color(0xFFBCAAA4), tertiaryContainer = Color(0xFF4E342E)
        )
    ),
    AppScheme(
        id = "naplemente",
        nameRes = R.string.scheme_sunset,
        light = scheme(
            primary = Color(0xFFBF360C), onPrimary = Color.White,
            container = Color(0xFFFFCCBC), onContainer = Color(0xFF3E1000),
            dark = false, background = Color(0xFFFFF8F5),
            surfaceVariant = Color(0xFFF7E7E0), onSurface = Color(0xFF231A16),
            onSurfaceVariant = Color(0xFF5F4A42), outline = Color(0xFFD8BCB1),
            tertiary = Color(0xFF7B5800), tertiaryContainer = Color(0xFFFFE082)
        ),
        dark = scheme(
            primary = Color(0xFFFFAB91), onPrimary = Color(0xFF4A1800),
            container = Color(0xFF8C2A08), onContainer = Color(0xFFFFDBCF),
            dark = true, background = Color(0xFF16100D),
            surfaceVariant = Color(0xFF241A16), onSurface = Color(0xFFEFE2DC),
            onSurfaceVariant = Color(0xFFC0A99F), outline = Color(0xFF4A3830),
            tertiary = Color(0xFFFFD180), tertiaryContainer = Color(0xFF614000)
        )
    ),
    AppScheme(
        id = "ejszaka",
        nameRes = R.string.scheme_night,
        light = scheme(
            primary = Color(0xFF4E342E), onPrimary = Color.White,
            container = Color(0xFFD7CCC8), onContainer = Color(0xFF1B0F0C),
            dark = false, background = Color(0xFFF5F0EC),
            surfaceVariant = Color(0xFFE7E0DA), onSurface = Color(0xFF241E1B),
            onSurfaceVariant = Color(0xFF554B45), outline = Color(0xFFC0B5AD),
            tertiary = Color(0xFF33691E), tertiaryContainer = Color(0xFFDCEDC8)
        ),
        dark = scheme(
            primary = Color(0xFFB98A5A), onPrimary = Color(0xFF1A1207),
            container = Color(0xFF3B2A17), onContainer = Color(0xFFE8D3B8),
            dark = true, background = Color(0xFF000000),
            surfaceVariant = Color(0xFF141414), onSurface = Color(0xFFCFC6BC),
            onSurfaceVariant = Color(0xFF9A9088), outline = Color(0xFF2C2A27),
            tertiary = Color(0xFF9CCC65), tertiaryContainer = Color(0xFF2C3D14)
        )
    ),
    AppScheme(
        id = "kontraszt",
        nameRes = R.string.scheme_contrast,
        light = scheme(
            primary = Color(0xFF000000), onPrimary = Color.White,
            container = Color(0xFFFFE600), onContainer = Color(0xFF000000),
            dark = false, background = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F0F0), onSurface = Color(0xFF000000),
            onSurfaceVariant = Color(0xFF2A2A2A), outline = Color(0xFF000000),
            tertiary = Color(0xFF0000CC), tertiaryContainer = Color(0xFFCCE0FF)
        ),
        dark = scheme(
            primary = Color(0xFFFFE600), onPrimary = Color(0xFF000000),
            container = Color(0xFF3A3400), onContainer = Color(0xFFFFF59D),
            dark = true, background = Color(0xFF000000),
            surfaceVariant = Color(0xFF141414), onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFFD8D8D8), outline = Color(0xFF6E6E6E),
            tertiary = Color(0xFF80D8FF), tertiaryContainer = Color(0xFF00344A)
        )
    )
)

fun schemeById(id: String): AppScheme =
    APP_SCHEMES.firstOrNull { it.id == id } ?: APP_SCHEMES[0]

/** Sűrű, adattáblához illő tipográfia, a felület betűméret-szorzójával. */
private fun denseTypography(scale: Float): Typography {
    fun s(v: Float) = (v * scale).sp
    return Typography(
        bodyLarge = TextStyle(fontSize = s(14f), lineHeight = s(18f)),
        bodyMedium = TextStyle(fontSize = s(12.5f), lineHeight = s(16f)),
        bodySmall = TextStyle(fontSize = s(11f), lineHeight = s(14f)),
        titleLarge = TextStyle(fontSize = s(19f), lineHeight = s(24f)),
        titleMedium = TextStyle(fontSize = s(15f), lineHeight = s(20f)),
        titleSmall = TextStyle(fontSize = s(13f), lineHeight = s(17f)),
        labelLarge = TextStyle(fontSize = s(13f), lineHeight = s(16f)),
        labelMedium = TextStyle(fontSize = s(11f), lineHeight = s(14f)),
        labelSmall = TextStyle(fontSize = s(10f), lineHeight = s(13f))
    )
}

@Composable
fun KonyvtarTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemeState.mode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val app = schemeById(ThemeState.schemeId)
    MaterialTheme(
        colorScheme = if (dark) app.dark else app.light,
        typography = denseTypography(ThemeState.uiScale),
        content = content
    )
}
