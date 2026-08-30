package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tipográfiai könyvborító: ha a fájlból nincs kinyert borítókép, a címből és
 * a szerzőből rajzolunk egyet. A szín a címből származik, tehát ugyanaz a
 * könyv mindig ugyanolyan színű lesz.
 */
@Composable
fun BookCover(
    title: String,
    author: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val pair = coverColors(title + "|" + author)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (compact) 3.dp else 8.dp))
            .background(Brush.verticalGradient(listOf(pair.first, pair.second)))
    ) {
        // Könyvgerinc: világosabb csík a bal szélen
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (compact) 3.dp else 10.dp)
                .background(Color.White.copy(alpha = 0.16f))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 8.dp else 22.dp,
                    end = if (compact) 4.dp else 14.dp,
                    top = if (compact) 4.dp else 20.dp,
                    bottom = if (compact) 4.dp else 16.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = if (compact) 9.sp else 21.sp,
                lineHeight = if (compact) 11.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 3 else 6,
                overflow = TextOverflow.Ellipsis
            )
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = if (compact) 7.sp else 14.sp,
                    lineHeight = if (compact) 9.sp else 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Left
                )
            }
        }
    }
}

/** Kellemes, sötét borítószínek — a szövegből származtatva, hogy állandó legyen. */
private fun coverColors(seed: String): Pair<Color, Color> {
    var h = 0
    for (c in seed) h = h * 31 + c.code
    val palette = listOf(
        Color(0xFF1B4332) to Color(0xFF2D6A4F),
        Color(0xFF14213D) to Color(0xFF29406B),
        Color(0xFF5A189A) to Color(0xFF7B2CBF),
        Color(0xFF6A040F) to Color(0xFF9D0208),
        Color(0xFF7F4F24) to Color(0xFFA68A64),
        Color(0xFF003049) to Color(0xFF00587A),
        Color(0xFF344E41) to Color(0xFF588157),
        Color(0xFF4A4E69) to Color(0xFF6C6F93),
        Color(0xFF7A2E2E) to Color(0xFFA34141),
        Color(0xFF283618) to Color(0xFF4F6D2E)
    )
    val idx = ((h % palette.size) + palette.size) % palette.size
    return palette[idx]
}

/** A polc alján látszó, olvasottságot mutató sáv színe. */
@Composable
fun progressBarColor(done: Boolean): Color =
    if (done) MaterialTheme.colorScheme.primary else Color(0xFF43A047)
