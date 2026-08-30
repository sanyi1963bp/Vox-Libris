package hu.konyvtar.tts.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.tts.TtsService
import java.util.Locale

/**
 * „Most szól" sáv a képernyők alján.
 *
 * Böngészés közben látod, melyik könyv szól és hol tart, egy koppintással
 * visszaugrasz hozzá, a gombbal pedig bárhonnan elnémítható. Csak akkor
 * jelenik meg, ha van betöltött könyv — máskor egy képpontot sem foglal.
 *
 * Az olvasó képernyőn nincs rá szükség: ott a teljes vezérlősáv van alul.
 */
@Composable
fun NowPlayingBar(onOpen: () -> Unit) {
    val player by TtsService.state.collectAsState()
    if (player.path == null) return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) }
                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f%%", player.percent) +
                        if (player.preparing) stringResource(R.string.explorer_preparing) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            NowPlayingButton()
        }
    }
}
