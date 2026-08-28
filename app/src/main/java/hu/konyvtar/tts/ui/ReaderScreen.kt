package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.Bookmark
import hu.konyvtar.tts.reader.Sentences
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Képernyős olvasó: teljes szöveg, villámgyors lapozás (gyorsgörgető + csúszka),
 * keresés a szövegben (ékezet-független), könyvjelzők.
 *
 * Gesztusok egy bekezdésen:
 *  - dupla koppintás: felolvasás pontosan innen
 *  - hosszú nyomás: könyvjelző erre a bekezdésre
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    path: String,
    title: String,
    author: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tts by TtsService.state.collectAsState()

    var paragraphs by remember(path) { mutableStateOf<List<String>?>(null) }
    var chapters by remember(path) { mutableStateOf<List<Int>>(emptyList()) }
    var folded by remember(path) { mutableStateOf<List<String>?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }

    var fontSp by remember { mutableFloatStateOf(Prefs.readerFont(context)) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchPos by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }

    var bookmarks by remember(path) { mutableStateOf<List<Bookmark>>(emptyList()) }
    var bookmarksOpen by remember { mutableStateOf(false) }

    var sliderDrag by remember { mutableStateOf<Float?>(null) }

    // Lejátszó-vezérlők (egyesített képernyő)
    var tuneOpen by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(Prefs.readerFollow(context)) }
    var speed by remember { mutableFloatStateOf(Prefs.speed(context)) }
    var pitch by remember { mutableFloatStateOf(Prefs.pitch(context)) }
    LaunchedEffect(tts.speed, tts.path) { if (tts.path != null) speed = tts.speed }
    LaunchedEffect(tts.pitch, tts.path) { if (tts.path != null) pitch = tts.pitch }

    // Követés: a nézet a felolvasott bekezdésre gördül (bekezdésváltáskor)
    LaunchedEffect(tts.paraIndex, tts.path, follow) {
        if (follow && tts.path == path && tts.totalParas > 0 &&
            paragraphs != null && !listState.isScrollInProgress
        ) {
            listState.animateScrollToItem(tts.paraIndex.coerceAtLeast(0))
        }
    }

    // ---------------------------------------------------------------- betöltés
    LaunchedEffect(path) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                val book = TextExtractor.book(context, File(path))
                val prog = AppDb.progressFor(path)
                val bms = AppDb.bookmarksFor(path)
                Triple(book, prog, bms)
            } catch (e: Exception) {
                error = e.message ?: "Hiba a szöveg betöltésekor."
                null
            }
        } ?: return@LaunchedEffect
        paragraphs = loaded.first.paragraphs
        chapters = loaded.first.chapters
        bookmarks = loaded.third
        val prog = loaded.second
        val start = when {
            prog == null -> 0
            prog.readPara > 0 -> prog.readPara
            else -> prog.paraIndex
        }
        listState.scrollToItem(start.coerceIn(0, loaded.first.paragraphs.size - 1))
    }

    // ---------------------------------------------------------------- pozíció mentése
    LaunchedEffect(paragraphs) {
        val paras = paragraphs ?: return@LaunchedEffect
        var lastSaved = -1
        while (true) {
            delay(3000)
            val idx = listState.firstVisibleItemIndex
            if (idx != lastSaved) {
                lastSaved = idx
                withContext(Dispatchers.IO) {
                    AppDb.setReadPara(path, idx, paras.size, title, author)
                }
            }
        }
    }
    DisposableEffect(path) {
        onDispose {
            val paras = paragraphs
            if (paras != null) {
                val idx = listState.firstVisibleItemIndex
                Thread {
                    try {
                        AppDb.setReadPara(path, idx, paras.size, title, author)
                    } catch (_: Exception) {
                    }
                }.start()
            }
        }
    }

    // ---------------------------------------------------------------- keresés
    LaunchedEffect(query, paragraphs) {
        val paras = paragraphs
        val q = query.trim()
        if (paras == null || q.length < 2) {
            matches = emptyList()
            matchPos = -1
            return@LaunchedEffect
        }
        searching = true
        delay(400) // gépelés közbeni fölösleges keresések ellen
        val foldedQuery = Normalizer.foldHu(q)
        val result = withContext(Dispatchers.Default) {
            val f = folded ?: paras.map { Normalizer.foldHu(it) }.also { folded = it }
            val out = ArrayList<Int>()
            for (i in f.indices) {
                if (f[i].contains(foldedQuery)) out.add(i)
            }
            out
        }
        matches = result
        matchPos = if (result.isEmpty()) -1 else 0
        searching = false
        if (result.isNotEmpty()) {
            listState.scrollToItem(result[0])
        }
    }

    fun jumpMatch(dir: Int) {
        if (matches.isEmpty()) return
        matchPos = (matchPos + dir).mod(matches.size)
        scope.launch { listState.scrollToItem(matches[matchPos]) }
    }

    fun addBookmarkAt(idx: Int) {
        val paras = paragraphs ?: return
        if (idx !in paras.indices) return
        val snippet = paras[idx].take(140)
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDb.addBookmark(path, idx, snippet, title, author)
            }
            bookmarks = withContext(Dispatchers.IO) { AppDb.bookmarksFor(path) }
            Toast.makeText(context, "Könyvjelző: ${idx + 1}. bekezdés", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------- navigáció

    fun chapterJump(dir: Int) {
        val paras = paragraphs ?: return
        if (paras.isEmpty()) return
        val cur = listState.firstVisibleItemIndex
        val target = if (chapters.size >= 2) {
            if (dir > 0) {
                chapters.firstOrNull { it > cur } ?: (paras.size - 1)
            } else {
                chapters.lastOrNull { it < cur } ?: 0
            }
        } else {
            // Nincs fejezetadat: ~5%-os ugrás
            val fallback = maxOf(20, paras.size / 20)
            (cur + dir * fallback).coerceIn(0, paras.size - 1)
        }
        scope.launch { listState.scrollToItem(target) }
    }

    fun screenJump(dir: Int) {
        scope.launch {
            val vp = listState.layoutInfo.viewportSize.height
            val amount = (if (vp > 0) vp else 1600).toFloat() * 0.92f * dir
            listState.animateScrollBy(amount)
        }
    }

    val bookmarkedIdx = remember(bookmarks) { bookmarks.map { it.paraIndex }.toHashSet() }
    val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val ttsSentenceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (author.isNotEmpty()) {
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }) {
                            Icon(
                                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Keresés a szövegben"
                            )
                        }
                        IconButton(onClick = {
                            // Ha épp ezt a könyvet olvassa fel, a felolvasott helyre kerül a jelző
                            val idx = if (tts.path == path && tts.totalParas > 0) {
                                tts.paraIndex
                            } else {
                                listState.firstVisibleItemIndex
                            }
                            addBookmarkAt(idx)
                        }) {
                            Icon(Icons.Filled.BookmarkAdd, contentDescription = "Könyvjelző ide")
                        }
                        IconButton(onClick = { bookmarksOpen = true }) {
                            Icon(Icons.Filled.Bookmarks, contentDescription = "Könyvjelzők")
                        }
                        if (tts.path == path) {
                            IconButton(onClick = {
                                TtsService.send(context, TtsService.ACTION_STOP)
                            }) {
                                Icon(Icons.Filled.Stop, contentDescription = "Felolvasás leállítása")
                            }
                        }
                    }
                )
                if (searchOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = {
                                Text("Keresés a szövegben…", style = MaterialTheme.typography.bodyMedium)
                            }
                        )
                        Text(
                            text = when {
                                searching -> "…"
                                matches.isEmpty() && query.trim().length >= 2 -> "0"
                                matches.isNotEmpty() -> "${matchPos + 1}/${matches.size}"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(onClick = { jumpMatch(-1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Előző találat")
                        }
                        IconButton(onClick = { jumpMatch(1) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Következő találat")
                        }
                    }
                }
            }
        },
        bottomBar = {
            val paras = paragraphs
            if (paras != null && paras.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        // Navigáció: fejezet | képernyő | mondat | Play/Pause | mondat | képernyő | fejezet
                        val sameBook = tts.path == path
                        val playingHere = sameBook && tts.playing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { chapterJump(-1) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Filled.KeyboardDoubleArrowUp,
                                    contentDescription = "Előző fejezet"
                                )
                            }
                            IconButton(onClick = { screenJump(-1) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Egy képernyő vissza"
                                )
                            }
                            IconButton(
                                onClick = { TtsService.send(context, TtsService.ACTION_PREV) },
                                enabled = sameBook,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Előző mondat")
                            }
                            FilledIconButton(
                                onClick = {
                                    if (sameBook) {
                                        TtsService.send(context, TtsService.ACTION_TOGGLE)
                                    } else {
                                        TtsService.playFile(
                                            context = context,
                                            path = path,
                                            title = title,
                                            author = author,
                                            konyvId = null,
                                            startIndex = listState.firstVisibleItemIndex,
                                            startChar = 0
                                        )
                                    }
                                },
                                modifier = Modifier.size(46.dp)
                            ) {
                                Icon(
                                    if (playingHere) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playingHere) "Szünet" else "Felolvasás"
                                )
                            }
                            IconButton(
                                onClick = { TtsService.send(context, TtsService.ACTION_NEXT) },
                                enabled = sameBook,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Következő mondat")
                            }
                            IconButton(onClick = { screenJump(1) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Egy képernyő előre"
                                )
                            }
                            IconButton(onClick = { chapterJump(1) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    Icons.Filled.KeyboardDoubleArrowDown,
                                    contentDescription = "Következő fejezet"
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    fontSp = (fontSp - 1f).coerceAtLeast(12f)
                                    Prefs.setReaderFont(context, fontSp)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.TextDecrease, contentDescription = "Kisebb betű")
                            }
                            IconButton(
                                onClick = {
                                    fontSp = (fontSp + 1f).coerceAtMost(30f)
                                    Prefs.setReaderFont(context, fontSp)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.TextIncrease, contentDescription = "Nagyobb betű")
                            }
                            val fraction = sliderDrag
                                ?: if (paras.size <= 1) 0f
                                else listState.firstVisibleItemIndex.toFloat() / (paras.size - 1)
                            Slider(
                                value = fraction.coerceIn(0f, 1f),
                                onValueChange = { sliderDrag = it },
                                onValueChangeFinished = {
                                    val f = sliderDrag ?: return@Slider
                                    scope.launch {
                                        listState.scrollToItem(
                                            (f * (paras.size - 1)).roundToInt().coerceIn(0, paras.size - 1)
                                        )
                                        sliderDrag = null
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            Text(
                                text = String.format(
                                    Locale.getDefault(), "%.1f%%",
                                    ((sliderDrag
                                        ?: if (paras.size <= 1) 0f
                                        else listState.firstVisibleItemIndex.toFloat() / (paras.size - 1)) * 100f)
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(52.dp),
                                textAlign = TextAlign.Right
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildString {
                                    append("${listState.firstVisibleItemIndex + 1}/${paras.size} bek.")
                                    if (sameBook && tts.totalParas > 0) {
                                        append(
                                            String.format(
                                                Locale.getDefault(),
                                                "  •  Felolvasás: %.1f%%  •  %s",
                                                tts.percent,
                                                fmtDuration(tts.listenedMs)
                                            )
                                        )
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    follow = !follow
                                    Prefs.setReaderFollow(context, follow)
                                    if (follow && sameBook && tts.totalParas > 0) {
                                        scope.launch { listState.animateScrollToItem(tts.paraIndex) }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MyLocation,
                                    contentDescription = "Felolvasás követése",
                                    tint = if (follow) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { tuneOpen = !tuneOpen },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Sebesség és hangmagasság",
                                    tint = if (tuneOpen) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (tuneOpen) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Sebesség",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(76.dp)
                                )
                                Slider(
                                    value = speed,
                                    onValueChange = { speed = it },
                                    onValueChangeFinished = {
                                        if (tts.path != null) {
                                            TtsService.send(context, TtsService.ACTION_SET_SPEED) {
                                                putExtra(TtsService.EXTRA_VALUE, speed)
                                            }
                                        } else {
                                            Prefs.setSpeed(context, speed)
                                        }
                                    },
                                    valueRange = 0.5f..3.0f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2fx", speed),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(44.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Hangmag.",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(76.dp)
                                )
                                Slider(
                                    value = pitch,
                                    onValueChange = { pitch = it },
                                    onValueChangeFinished = {
                                        if (tts.path != null) {
                                            TtsService.send(context, TtsService.ACTION_SET_PITCH) {
                                                putExtra(TtsService.EXTRA_VALUE, pitch)
                                            }
                                        } else {
                                            Prefs.setPitch(context, pitch)
                                        }
                                    },
                                    valueRange = 0.5f..2.0f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.2f", pitch),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(44.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val paras = paragraphs
            when {
                error != null -> Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
                paras == null -> Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Szöveg betöltése…", style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(paras) { idx, text ->
                            val isTtsHere = tts.path == path && tts.paraIndex == idx && tts.totalParas > 0
                            val isCurrentMatch = matchPos >= 0 && matches.getOrNull(matchPos) == idx
                            val isBm = idx in bookmarkedIdx
                            val bg = when {
                                isTtsHere -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                isCurrentMatch -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                else -> Color.Transparent
                            }
                            // A koppintás → karakterpozíció leképezéshez kell az elrendezés
                            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val prefixLen = if (isBm) BOOKMARK_PREFIX.length else 0
                            Text(
                                text = paraText(
                                    text = text,
                                    query = query.trim(),
                                    bookmarked = isBm,
                                    highlight = highlightColor,
                                    ttsStart = if (isTtsHere) tts.sentStart else -1,
                                    ttsEnd = if (isTtsHere) tts.sentEnd else -1,
                                    ttsHighlight = ttsSentenceColor
                                ),
                                fontSize = fontSp.sp,
                                lineHeight = (fontSp * 1.45f).sp,
                                onTextLayout = { layout = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .pointerInput(idx) {
                                        detectTapGestures(
                                            onDoubleTap = { pos ->
                                                val raw = layout?.getOffsetForPosition(pos) ?: 0
                                                val inText = (raw - prefixLen).coerceIn(0, text.length)
                                                val sentStart = Sentences.startAt(text, inText)
                                                TtsService.playFile(
                                                    context = context,
                                                    path = path,
                                                    title = title,
                                                    author = author,
                                                    konyvId = null,
                                                    startIndex = idx,
                                                    startChar = sentStart
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Felolvasás a kijelölt mondattól",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onLongPress = { addBookmarkAt(idx) }
                                        )
                                    }
                            )
                        }
                    }
                    FastScrollbar(
                        listState = listState,
                        itemCount = paras.size,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- könyvjelző lista
    if (bookmarksOpen) {
        AlertDialog(
            onDismissRequest = { bookmarksOpen = false },
            title = { Text("Könyvjelzők", style = MaterialTheme.typography.titleMedium) },
            text = {
                if (bookmarks.isEmpty()) {
                    Text(
                        "Még nincs könyvjelző.\nHosszan nyomj meg egy bekezdést a hozzáadáshoz!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(bm.id) {
                                            detectTapGestures(onTap = {
                                                bookmarksOpen = false
                                                scope.launch { listState.scrollToItem(bm.paraIndex) }
                                            })
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${bm.paraIndex + 1}. bekezdés • ${fmtDate(bm.created)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = bm.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { AppDb.deleteBookmark(bm.id) }
                                                bookmarks = withContext(Dispatchers.IO) {
                                                    AppDb.bookmarksFor(path)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Törlés",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bookmarksOpen = false }) { Text("Bezárás") }
            }
        )
    }
}

private const val BOOKMARK_PREFIX = "🔖 "

/**
 * Bekezdés szövege: könyvjelző-jel, keresési találatok kiemelése,
 * és az éppen felolvasott mondat háttérszíne.
 */
private fun paraText(
    text: String,
    query: String,
    bookmarked: Boolean,
    highlight: Color,
    ttsStart: Int = -1,
    ttsEnd: Int = -1,
    ttsHighlight: Color = Color.Transparent
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
                    SpanStyle(background = ttsHighlight),
                    prefix.length + s0,
                    prefix.length + e0
                )
            }
        }
        if (query.length >= 2) {
            val foldedText = Normalizer.foldHu(text)
            val foldedQuery = Normalizer.foldHu(query)
            var i = foldedText.indexOf(foldedQuery)
            while (i >= 0) {
                addStyle(
                    SpanStyle(background = highlight, fontWeight = FontWeight.Bold),
                    prefix.length + i,
                    prefix.length + i + foldedQuery.length
                )
                i = foldedText.indexOf(foldedQuery, i + foldedQuery.length)
            }
        }
    }
}
