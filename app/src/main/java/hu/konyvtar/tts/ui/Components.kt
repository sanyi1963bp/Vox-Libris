package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import hu.konyvtar.tts.R

/**
 * Gyorsgörgető sáv nagy listákhoz: húzásra a lista arányosan ugrik.
 * A szülő Box jobb szélére kell illeszteni (fillMaxHeight mellett).
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount < 50) return
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }

    val thumbHeightDp = 48.dp
    val fraction = if (itemCount <= 1) 0f else {
        listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(26.dp)
            .onSizeChanged { heightPx = it.height }
            .pointerInput(itemCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val f = (offset.y / heightPx).coerceIn(0f, 1f)
                        scope.launch {
                            listState.scrollToItem((f * (itemCount - 1)).roundToInt())
                        }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, _ ->
                        val f = (change.position.y / heightPx).coerceIn(0f, 1f)
                        scope.launch {
                            listState.scrollToItem((f * (itemCount - 1)).roundToInt())
                        }
                    }
                )
            }
            .pointerInput(itemCount) {
                detectTapGestures { offset ->
                    val f = (offset.y / heightPx).coerceIn(0f, 1f)
                    scope.launch {
                        listState.scrollToItem((f * (itemCount - 1)).roundToInt())
                    }
                }
            }
    ) {
        val trackPx = heightPx.toFloat()
        val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeightDp.toPx() }
        val y = ((trackPx - thumbPx).coerceAtLeast(0f) * fraction).roundToInt()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y) }
                .width(if (dragging) 10.dp else 6.dp)
                .height(thumbHeightDp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 0.95f else 0.55f),
                    RoundedCornerShape(3.dp)
                )
        )
    }
}

// ---------------------------------------------------------------- formázók

private val dateFmt = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())

fun fmtDate(millis: Long): String =
    if (millis <= 0) "" else dateFmt.format(Date(millis))

fun fmtSize(bytes: Long): String {
    if (bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.getDefault(), "%.2f GB", gb)
}

fun fmtDuration(context: android.content.Context, ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) context.getString(R.string.duration_hm, h, m)
    else context.getString(R.string.duration_m, m)
}
