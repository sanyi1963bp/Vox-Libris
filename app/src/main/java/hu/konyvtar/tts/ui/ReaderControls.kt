package hu.konyvtar.tts.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.tts.TtsService
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Az alsó sáv állapotsorának adatai. A fejezet- és százalékadat csak akkor
 * értelmes, ha éppen ez a könyv szól ([narrating]).
 */
data class ReaderStatus(
    val paraIndex: Int,
    val paraCount: Int,
    val chapterIndex: Int,
    val chapterCount: Int,
    val percent: Double,
    val listenedMs: Long,
    val narrating: Boolean
)

/**
 * Az olvasó alsó vezérlősávja: állapotsor, kinyitható betű- és hangbeállítás,
 * pozíció-csúszka, és alul a léptetőgombok.
 *
 * A léptetés mindig fejezet / bekezdés / mondat hármasban, mindkét irányba —
 * a lejátszás gombja középen, hüvelykujjnyi távolságra mindegyiktől.
 */
@Composable
fun ReaderControls(
    status: ReaderStatus,
    playing: Boolean,
    follow: Boolean,
    onToggleFollow: () -> Unit,
    fontSp: Float,
    onFontChange: (Float) -> Unit,
    /** Bionic Reading: a szavak elejének félkövér szedése. */
    bionic: Boolean,
    onToggleBionic: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedDone: () -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onPitchDone: () -> Unit,
    /** A pozíció-csúszka állása 0..1; null, ha nincs mit csúszkázni. */
    sliderFraction: Float?,
    onSeekDrag: (Float) -> Unit,
    onSeekDone: () -> Unit,
    onPlayPause: () -> Unit,
    onNav: (String) -> Unit
) {
    val context = LocalContext.current
    var toolsOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            // ---- állapotsor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val paraStatus = if (status.paraCount > 0) stringResource(
                    R.string.reader_status_para, status.paraIndex + 1, status.paraCount
                ) else ""
                val chapStatus = if (status.narrating && status.chapterCount > 0) stringResource(
                    R.string.reader_status_chapter,
                    status.chapterIndex + 1, status.chapterCount
                ) else ""
                val progStatus = if (status.narrating) stringResource(
                    R.string.reader_status_progress,
                    String.format(Locale.getDefault(), "%.1f", status.percent),
                    fmtDuration(context, status.listenedMs)
                ) else ""
                Text(
                    text = paraStatus + chapStatus + progStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFollow, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.reader_follow),
                        modifier = Modifier.size(18.dp),
                        tint = if (follow) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { toolsOpen = !toolsOpen },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.reader_font_and_voice),
                        modifier = Modifier.size(18.dp),
                        tint = if (toolsOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- betű, sebesség, hangmagasság
            if (toolsOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.reader_font),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(64.dp)
                    )
                    IconButton(
                        onClick = { onFontChange((fontSp - 1f).coerceAtLeast(12f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.TextDecrease,
                            contentDescription = stringResource(R.string.reader_smaller_font)
                        )
                    }
                    IconButton(
                        onClick = { onFontChange((fontSp + 1f).coerceAtMost(30f)) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.TextIncrease,
                            contentDescription = stringResource(R.string.reader_larger_font)
                        )
                    }
                    Text(
                        text = "${fontSp.roundToInt()} sp",
                        style = MaterialTheme.typography.labelSmall
                    )
                    // A bionic szedés tipográfia, ezért a betűméret mellé
                    // került: itt látszik rögtön, mit csinál a szöveggel.
                    IconButton(onClick = onToggleBionic, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.FormatBold,
                            contentDescription = stringResource(R.string.reader_bionic),
                            tint = if (bionic) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SliderRow(
                    label = stringResource(R.string.reader_speed),
                    value = speed,
                    range = 0.5f..3.0f,
                    format = "%.2fx",
                    onChange = onSpeedChange,
                    onDone = onSpeedDone
                )
                SliderRow(
                    label = stringResource(R.string.reader_pitch),
                    value = pitch,
                    range = 0.5f..2.0f,
                    format = "%.2f",
                    onChange = onPitchChange,
                    onDone = onPitchDone
                )
            }

            // ---- pozíció a könyvben
            if (sliderFraction != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = sliderFraction.coerceIn(0f, 1f),
                        onValueChange = onSeekDrag,
                        onValueChangeFinished = onSeekDone,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(
                            Locale.getDefault(), "%.0f%%", sliderFraction * 100f
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.Right
                    )
                }
            }

            // ---- léptetés és lejátszás
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavButton(
                    Icons.Filled.FirstPage,
                    stringResource(R.string.nav_chapter),
                    stringResource(R.string.nav_prev_chapter)
                ) { onNav(TtsService.ACTION_PREV_CHAPTER) }
                NavButton(
                    Icons.Filled.SkipPrevious,
                    stringResource(R.string.nav_paragraph),
                    stringResource(R.string.nav_prev_paragraph)
                ) { onNav(TtsService.ACTION_PREV_PARA) }
                NavButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.nav_sentence),
                    stringResource(R.string.nav_prev_sentence)
                ) { onNav(TtsService.ACTION_PREV) }
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(50.dp)) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.common_pause else R.string.common_play
                        ),
                        modifier = Modifier.size(30.dp)
                    )
                }
                NavButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.nav_sentence),
                    stringResource(R.string.nav_next_sentence)
                ) { onNav(TtsService.ACTION_NEXT) }
                NavButton(
                    Icons.Filled.SkipNext,
                    stringResource(R.string.nav_paragraph),
                    stringResource(R.string.nav_next_paragraph)
                ) { onNav(TtsService.ACTION_NEXT_PARA) }
                NavButton(
                    Icons.AutoMirrored.Filled.LastPage,
                    stringResource(R.string.nav_chapter),
                    stringResource(R.string.nav_next_chapter)
                ) { onNav(TtsService.ACTION_NEXT_CHAPTER) }
            }
        }
    }
}

@Composable
private fun NavButton(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .pointerInput(description) { detectTapGestures(onTap = { onClick() }) }
            .padding(vertical = 2.dp)
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 8.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format(Locale.getDefault(), format, value),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Right
        )
    }
}
