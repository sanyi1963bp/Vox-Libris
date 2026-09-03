package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R

/** Melyik fő nézetben járunk éppen — ez világít a sávban. */
enum class MainView { LIBRARY, FILES, READER, OTHER }

/**
 * A mindenhol jelen lévő navigáció adatai.
 *
 * Egyetlen objektumban utazik a képernyőkig, hogy ne kelljen hat callbacket
 * végigfűzni mindegyiken.
 */
data class MainNav(
    val current: MainView,
    /** Van-e egyáltalán mire ugrani az olvasó gombbal. */
    val readerEnabled: Boolean,
    val onLibrary: () -> Unit,
    val onFiles: () -> Unit,
    val onReader: () -> Unit
)

/**
 * A képernyők alsó sávja: „most szól" csík, alatta a navigáció.
 *
 * A kettő egymás fölött, egy egységként ül — ez a zenelejátszókból ismerős
 * elrendezés, és megspórolja, hogy két külön sáv egye a helyet. A „most szól"
 * csík csak akkor látszik, ha van betöltött könyv.
 *
 * Az olvasó képernyő nem ezt használja: ott a saját vezérlősávja alá kerül a
 * [MainNavBar] külön, mert a „most szól" csíkra ott nincs szükség.
 */
@Composable
fun MainBottomBars(nav: MainNav) {
    Column {
        NowPlayingBar(onOpen = nav.onReader)
        MainNavBar(nav)
    }
}

/**
 * A navigációs sáv: könyvtár, fájlok, olvasó.
 *
 * Szándékosan alacsonyabb a Material alapértelmezett 80 dp-jénél: az olvasó
 * képernyőn a vezérlősáv alatt ül, és ott minden képpont a szövegé.
 */
@Composable
fun MainNavBar(nav: MainNav) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    label = stringResource(R.string.nav_view_library),
                    selected = nav.current == MainView.LIBRARY,
                    onClick = nav.onLibrary
                )
                NavItem(
                    icon = Icons.Filled.Folder,
                    label = stringResource(R.string.nav_view_files),
                    selected = nav.current == MainView.FILES,
                    onClick = nav.onFiles
                )
                NavItem(
                    icon = Icons.Filled.AutoStories,
                    label = stringResource(R.string.nav_view_reader),
                    selected = nav.current == MainView.READER,
                    enabled = nav.readerEnabled,
                    onClick = nav.onReader
                )
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .weight(1f)
            .height(42.dp)
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(19.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
