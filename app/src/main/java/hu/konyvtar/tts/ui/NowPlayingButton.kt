package hu.konyvtar.tts.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hu.konyvtar.tts.R
import hu.konyvtar.tts.tts.TtsService

/**
 * Indítás/szünet gomb, ami minden képernyő felső sávjában ott van.
 *
 * Csak akkor jelenik meg, ha van betöltött könyv — így nincs olyan gomb,
 * amire hiába nyomsz, viszont amint felolvasás indul, bárhonnan el lehet
 * némítani anélkül, hogy vissza kellene keresni a könyvet.
 *
 * Az olvasó képernyőn nincs rá szükség: ott a nagy vezérlőgombok vannak.
 */
@Composable
fun NowPlayingButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player by TtsService.state.collectAsState()
    if (player.path == null) return

    IconButton(
        onClick = { TtsService.send(context, TtsService.ACTION_TOGGLE) },
        modifier = modifier
    ) {
        Icon(
            imageVector = if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            // A felolvasott könyv címe is elhangzik a képernyőolvasónak
            contentDescription = stringResource(
                if (player.playing) R.string.common_pause else R.string.common_play
            ) + ": " + player.title,
            tint = if (player.playing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
