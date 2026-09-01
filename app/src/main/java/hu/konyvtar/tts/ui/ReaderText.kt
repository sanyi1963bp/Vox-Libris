package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.reader.Bionic
import hu.konyvtar.tts.reader.Sentences
import hu.konyvtar.tts.ui.theme.ChapterBandColor

/** Melyik mondatot mondja éppen a felolvasó ebben a könyvben. */
data class NarratedSentence(
    val paraIndex: Int,
    val start: Int,
    val end: Int
) {
    companion object {
        /** Nem ez a könyv szól — nincs mit kiemelni. */
        val NONE = NarratedSentence(-1, -1, -1)
    }
}

/**
 * A könyv szövege: bekezdéslista fejezetsávokkal, kiemelésekkel.
 *
 * Gesztusok: a dupla koppintás a megérintett mondattól indítja a felolvasást.
 * A hosszú nyomás alapból a **műveletmenüt** nyitja meg (könyvjelző, kiejtés,
 * Wikipédia, idézetkártya, másolás); ha a beállításokban átkapcsoltad, akkor
 * azonnal könyvjelzőt tesz, a menü pedig egyszeri koppintásra jön elő. Így
 * mindkettő elérhető marad, csak cserélődik a két gesztus.
 */
@Composable
fun ReaderText(
    paragraphs: List<String>,
    listState: LazyListState,
    chapters: Set<Int>,
    bookmarked: Set<Int>,
    fontSp: Float,
    query: String,
    /** A keresés éppen kiemelt találata; -1, ha nincs. */
    currentMatch: Int,
    narrated: NarratedSentence,
    /** Bionic Reading: a szavak elejének félkövér szedése. */
    bionic: Boolean,
    /** Igaz esetén a hosszú nyomás könyvjelzőz, a menü koppintásra jön. */
    longPressBookmark: Boolean,
    onPlayFrom: (paraIndex: Int, startChar: Int) -> Unit,
    onBookmark: (paraIndex: Int) -> Unit,
    onActions: (paraIndex: Int, charOffset: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val sentenceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val paraColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    val matchColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(paragraphs) { idx, text ->
                val isNarrated = narrated.paraIndex == idx
                val isMatch = currentMatch == idx
                val isChapter = idx in chapters
                val isBookmarked = idx in bookmarked
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val prefixLen = if (isBookmarked) BOOKMARK_PREFIX.length else 0

                /** A koppintás helyéből a bekezdésen belüli karakterindex. */
                fun charAt(pos: Offset): Int {
                    val raw = layout?.getOffsetForPosition(pos) ?: 0
                    return (raw - prefixLen).coerceIn(0, text.length)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isNarrated -> paraColor
                                isMatch -> matchColor
                                else -> Color.Transparent
                            }
                        )
                ) {
                    if (isChapter) {
                        // Feltűnő, vérvörös sáv a fejezetek között
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .background(ChapterBandColor)
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = paraText(
                            text = text,
                            query = query,
                            bookmarked = isBookmarked,
                            searchColor = searchColor,
                            ttsStart = if (isNarrated) narrated.start else -1,
                            ttsEnd = if (isNarrated) narrated.end else -1,
                            ttsColor = sentenceColor,
                            bionic = bionic
                        ),
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.45f).sp,
                        fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                        onTextLayout = { layout = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .pointerInput(idx, longPressBookmark) {
                                detectTapGestures(
                                    // Az egyszeri koppintást csak akkor figyeljük, ha a
                                    // hosszú nyomást elvitte a könyvjelző — különben a
                                    // menü görgetés közben is felugrálna.
                                    onTap = if (longPressBookmark) {
                                        { pos -> onActions(idx, charAt(pos)) }
                                    } else null,
                                    onDoubleTap = { pos ->
                                        onPlayFrom(idx, Sentences.startAt(text, charAt(pos)))
                                    },
                                    onLongPress = { pos ->
                                        if (longPressBookmark) onBookmark(idx)
                                        else onActions(idx, charAt(pos))
                                    }
                                )
                            }
                    )
                }
            }
        }
        FastScrollbar(
            listState = listState,
            itemCount = paragraphs.size,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

private const val BOOKMARK_PREFIX = "🔖 "

/** Bekezdés szövege: könyvjelző-jel, keresési találat, felolvasott mondat. */
private fun paraText(
    text: String,
    query: String,
    bookmarked: Boolean,
    searchColor: Color,
    ttsStart: Int = -1,
    ttsEnd: Int = -1,
    ttsColor: Color = Color.Transparent,
    bionic: Boolean = false
): AnnotatedString {
    val prefix = if (bookmarked) BOOKMARK_PREFIX else ""
    return buildAnnotatedString {
        append(prefix)
        append(text)
        if (ttsStart in 0 until ttsEnd) {
            val s0 = ttsStart.coerceIn(0, text.length)
            val e0 = ttsEnd.coerceIn(s0, text.length)
            if (e0 > s0) {
                addStyle(
                    SpanStyle(background = ttsColor, fontWeight = FontWeight.Medium),
                    prefix.length + s0,
                    prefix.length + e0
                )
            }
        }
        if (query.length >= 2) {
            val ft = Normalizer.foldHu(text)
            val fq = Normalizer.foldHu(query)
            var i = ft.indexOf(fq)
            while (i >= 0) {
                addStyle(
                    SpanStyle(background = searchColor, fontWeight = FontWeight.Bold),
                    prefix.length + i,
                    prefix.length + i + fq.length
                )
                i = ft.indexOf(fq, i + fq.length)
            }
        }
        // A szókezdetek kiemelése MINDIG utoljára megy fel: a háttérszínt nem
        // bántja (az más tulajdonság), a félkövérséget viszont megtartja a
        // felolvasott mondaton belül is.
        if (bionic) {
            for ((s, e) in Bionic.boldRanges(text)) {
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    prefix.length + s,
                    prefix.length + e
                )
            }
        }
    }
}
