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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import hu.konyvtar.tts.data.CatalogHolder
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.Bookmark
import hu.konyvtar.tts.model.CatalogBook
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
 * A könyv EGYETLEN képernyője: itt látszik a szöveg, és itt van minden
 * vezérlő is. Nincs külön lejátszó- és részletező-ablak.
 *
 * Felül: vissza, cím, keresés, beállítások, továbbiak menü.
 * Alul: fejezet / bekezdés / mondat léptetés mindkét irányba + lejátszás.
 *
 * Gesztusok a szövegen:
 *  - dupla koppintás: felolvasás pontosan a megérintett mondattól
 *  - hosszú nyomás: könyvjelző a bekezdéshez
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    path: String,
    title: String,
    author: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
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
    var follow by remember { mutableStateOf(Prefs.readerFollow(context)) }
    var toolsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchPos by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }

    var bookmarks by remember(path) { mutableStateOf<List<Bookmark>>(emptyList()) }
    var bookmarksOpen by remember { mutableStateOf(false) }

    var infoOpen by remember { mutableStateOf(false) }
    var bookInfo by remember(path) { mutableStateOf<CatalogBook?>(null) }

    var sliderDrag by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableFloatStateOf(Prefs.speed(context)) }
    var pitch by remember { mutableFloatStateOf(Prefs.pitch(context)) }

    val sameBook = tts.path == path
    val playingHere = sameBook && tts.playing

    LaunchedEffect(tts.speed, sameBook) { if (sameBook) speed = tts.speed }
    LaunchedEffect(tts.pitch, sameBook) { if (sameBook) pitch = tts.pitch }

    // Képernyő ébren tartása, ha a beállításokban kérted
    val view = LocalView.current
    DisposableEffect(Unit) {
        if (Prefs.keepScreenOn(context)) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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
            sameBook && tts.totalParas > 0 -> tts.paraIndex
            prog == null -> 0
            prog.readPara > 0 -> prog.readPara
            else -> prog.paraIndex
        }
        listState.scrollToItem(start.coerceIn(0, loaded.first.paragraphs.size - 1))
    }

    // ---------------------------------------------------------------- követés
    LaunchedEffect(tts.paraIndex, sameBook, follow) {
        if (follow && sameBook && tts.totalParas > 0 && paragraphs != null &&
            !listState.isScrollInProgress
        ) {
            listState.animateScrollToItem(tts.paraIndex.coerceAtLeast(0))
        }
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
            matches = emptyList(); matchPos = -1
            return@LaunchedEffect
        }
        searching = true
        delay(400)
        val foldedQuery = Normalizer.foldHu(q)
        val result = withContext(Dispatchers.Default) {
            val f = folded ?: paras.map { Normalizer.foldHu(it) }.also { folded = it }
            val out = ArrayList<Int>()
            for (i in f.indices) if (f[i].contains(foldedQuery)) out.add(i)
            out
        }
        matches = result
        matchPos = if (result.isEmpty()) -1 else 0
        searching = false
        if (result.isNotEmpty()) listState.scrollToItem(result[0])
    }

    fun jumpMatch(dir: Int) {
        if (matches.isEmpty()) return
        matchPos = (matchPos + dir).mod(matches.size)
        scope.launch { listState.scrollToItem(matches[matchPos]) }
    }

    fun addBookmarkAt(idx: Int) {
        val paras = paragraphs ?: return
        if (idx !in paras.indices) return
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDb.addBookmark(path, idx, paras[idx].take(140), title, author)
            }
            bookmarks = withContext(Dispatchers.IO) { AppDb.bookmarksFor(path) }
            Toast.makeText(context, "Könyvjelző: ${idx + 1}. bekezdés", Toast.LENGTH_SHORT).show()
        }
    }

    /** Felolvasás indítása/szüneteltetése; ha még nem ez a könyv szól, innen indul. */
    fun playPause() {
        if (sameBook) {
            TtsService.send(context, TtsService.ACTION_TOGGLE)
        } else {
            TtsService.playFile(
                context = context, path = path, title = title, author = author,
                konyvId = null, startIndex = listState.firstVisibleItemIndex, startChar = 0
            )
        }
    }

    /** Léptetőgomb: ha nem ez a könyv szól, előbb ide töltjük be. */
    fun nav(action: String) {
        if (!sameBook) {
            TtsService.playFile(
                context = context, path = path, title = title, author = author,
                konyvId = null, startIndex = listState.firstVisibleItemIndex, startChar = 0
            )
            return
        }
        TtsService.send(context, action)
    }

    val bookmarkedIdx = remember(bookmarks) { bookmarks.map { it.paraIndex }.toHashSet() }
    val chapterSet = remember(chapters) { chapters.toHashSet() }
    val searchColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
    val sentenceColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    val paraColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)

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
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Beállítások")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "További műveletek")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Könyvjelző ide") },
                                    leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) },
                                    onClick = {
                                        menuOpen = false
                                        val idx = if (sameBook && tts.totalParas > 0) tts.paraIndex
                                        else listState.firstVisibleItemIndex
                                        addBookmarkAt(idx)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Könyvjelzők (${bookmarks.size})") },
                                    leadingIcon = { Icon(Icons.Filled.Bookmarks, null) },
                                    onClick = { menuOpen = false; bookmarksOpen = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("A könyv adatai") },
                                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                                    onClick = { menuOpen = false; infoOpen = true }
                                )
                                if (sameBook) {
                                    DropdownMenuItem(
                                        text = { Text("Felolvasás leállítása") },
                                        leadingIcon = { Icon(Icons.Filled.Stop, null) },
                                        onClick = {
                                            menuOpen = false
                                            TtsService.send(context, TtsService.ACTION_STOP)
                                        }
                                    )
                                }
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
                        Text(
                            text = buildString {
                                if (paras != null) {
                                    append("Bek. ${listState.firstVisibleItemIndex + 1}/${paras.size}")
                                }
                                if (sameBook && tts.totalChapters > 0) {
                                    append("  •  Fej. ${tts.chapterIndex + 1}/${tts.totalChapters}")
                                }
                                if (sameBook && tts.totalParas > 0) {
                                    append(
                                        String.format(
                                            Locale.getDefault(), "  •  %.1f%%  •  %s",
                                            tts.percent, fmtDuration(tts.listenedMs)
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "Felolvasás követése",
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
                                contentDescription = "Betűméret és hang",
                                modifier = Modifier.size(18.dp),
                                tint = if (toolsOpen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ---- eszközök
                    if (toolsOpen) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Betű",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(64.dp)
                            )
                            IconButton(
                                onClick = {
                                    fontSp = (fontSp - 1f).coerceAtLeast(12f)
                                    Prefs.setReaderFont(context, fontSp)
                                },
                                modifier = Modifier.size(34.dp)
                            ) { Icon(Icons.Filled.TextDecrease, contentDescription = "Kisebb betű") }
                            IconButton(
                                onClick = {
                                    fontSp = (fontSp + 1f).coerceAtMost(30f)
                                    Prefs.setReaderFont(context, fontSp)
                                },
                                modifier = Modifier.size(34.dp)
                            ) { Icon(Icons.Filled.TextIncrease, contentDescription = "Nagyobb betű") }
                            Text(
                                text = "${fontSp.roundToInt()} sp",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        SliderRow(
                            label = "Sebesség",
                            value = speed,
                            range = 0.5f..3.0f,
                            format = "%.2fx",
                            onChange = { speed = it },
                            onDone = {
                                if (sameBook) {
                                    TtsService.send(context, TtsService.ACTION_SET_SPEED) {
                                        putExtra(TtsService.EXTRA_VALUE, speed)
                                    }
                                } else {
                                    Prefs.setSpeed(context, speed)
                                }
                            }
                        )
                        SliderRow(
                            label = "Hangmag.",
                            value = pitch,
                            range = 0.5f..2.0f,
                            format = "%.2f",
                            onChange = { pitch = it },
                            onDone = {
                                if (sameBook) {
                                    TtsService.send(context, TtsService.ACTION_SET_PITCH) {
                                        putExtra(TtsService.EXTRA_VALUE, pitch)
                                    }
                                } else {
                                    Prefs.setPitch(context, pitch)
                                }
                            }
                        )
                    }

                    // ---- pozíció csúszka
                    if (paras != null && paras.size > 1) {
                        val fraction = sliderDrag
                            ?: (listState.firstVisibleItemIndex.toFloat() / (paras.size - 1))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = fraction.coerceIn(0f, 1f),
                                onValueChange = { sliderDrag = it },
                                onValueChangeFinished = {
                                    val f = sliderDrag ?: return@Slider
                                    scope.launch {
                                        listState.scrollToItem(
                                            (f * (paras.size - 1)).roundToInt()
                                                .coerceIn(0, paras.size - 1)
                                        )
                                        sliderDrag = null
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "%.0f%%", fraction * 100f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(38.dp),
                                textAlign = TextAlign.Right
                            )
                        }
                    }

                    // ---- vezérlőgombok
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavButton(Icons.Filled.FirstPage, "fejezet", "Előző fejezet") {
                            nav(TtsService.ACTION_PREV_CHAPTER)
                        }
                        NavButton(Icons.Filled.SkipPrevious, "bekezd.", "Előző bekezdés") {
                            nav(TtsService.ACTION_PREV_PARA)
                        }
                        NavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "mondat", "Előző mondat") {
                            nav(TtsService.ACTION_PREV)
                        }
                        FilledIconButton(
                            onClick = { playPause() },
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(
                                if (playingHere) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playingHere) "Szünet" else "Felolvasás",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        NavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "mondat", "Következő mondat") {
                            nav(TtsService.ACTION_NEXT)
                        }
                        NavButton(Icons.Filled.SkipNext, "bekezd.", "Következő bekezdés") {
                            nav(TtsService.ACTION_NEXT_PARA)
                        }
                        NavButton(Icons.AutoMirrored.Filled.LastPage, "fejezet", "Következő fejezet") {
                            nav(TtsService.ACTION_NEXT_CHAPTER)
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
                            val isTtsHere = sameBook && tts.paraIndex == idx && tts.totalParas > 0
                            val isMatch = matchPos >= 0 && matches.getOrNull(matchPos) == idx
                            val isChapter = idx in chapterSet
                            val isBm = idx in bookmarkedIdx
                            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val prefixLen = if (isBm) BOOKMARK_PREFIX.length else 0

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            isTtsHere -> paraColor
                                            isMatch -> MaterialTheme.colorScheme.tertiaryContainer
                                                .copy(alpha = 0.45f)
                                            else -> Color.Transparent
                                        }
                                    )
                            ) {
                                if (isChapter) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                                Text(
                                    text = paraText(
                                        text = text,
                                        query = query.trim(),
                                        bookmarked = isBm,
                                        searchColor = searchColor,
                                        ttsStart = if (isTtsHere) tts.sentStart else -1,
                                        ttsEnd = if (isTtsHere) tts.sentEnd else -1,
                                        ttsColor = sentenceColor
                                    ),
                                    fontSize = fontSp.sp,
                                    lineHeight = (fontSp * 1.45f).sp,
                                    fontWeight = if (isChapter) FontWeight.Bold else FontWeight.Normal,
                                    onTextLayout = { layout = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                        .pointerInput(idx) {
                                            detectTapGestures(
                                                onDoubleTap = { pos ->
                                                    val raw = layout?.getOffsetForPosition(pos) ?: 0
                                                    val inText = (raw - prefixLen)
                                                        .coerceIn(0, text.length)
                                                    TtsService.playFile(
                                                        context = context, path = path,
                                                        title = title, author = author,
                                                        konyvId = null, startIndex = idx,
                                                        startChar = Sentences.startAt(text, inText)
                                                    )
                                                },
                                                onLongPress = { addBookmarkAt(idx) }
                                            )
                                        }
                                )
                            }
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

    // ---------------------------------------------------------------- könyvjelzők
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

    // ---------------------------------------------------------------- könyv adatai
    if (infoOpen) {
        LaunchedEffect(Unit) {
            if (bookInfo == null) {
                bookInfo = withContext(Dispatchers.IO) {
                    val row = AppDb.cachedForPath(path)
                    row?.konyvId?.let { CatalogHolder.get(context)?.bookById(it) }
                }
            }
        }
        val f = File(path)
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text("A könyv adatai", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val b = bookInfo
                    InfoLine("Cím", b?.cim ?: title)
                    InfoLine("Szerző", b?.szerzo ?: author)
                    InfoLine("Kiadó", b?.kiado)
                    InfoLine("Kiadás éve", b?.kiadasEve)
                    InfoLine("ISBN", b?.isbn)
                    InfoLine(
                        "Sorozat",
                        listOfNotNull(
                            b?.sorozat?.takeIf { it.isNotBlank() && it != "N/A" },
                            b?.sorozatSzama?.takeIf { it.isNotBlank() && it != "N/A" }
                        ).joinToString(" #").ifBlank { null }
                    )
                    InfoLine("Címkék", b?.cimkek)
                    InfoLine("Fájl", f.name)
                    InfoLine("Méret", fmtSize(f.length()))
                    InfoLine("Bekezdések", paragraphs?.size?.toString())
                    InfoLine("Fejezetek", if (chapters.isEmpty()) null else chapters.size.toString())
                    val desc = b?.leiras?.let { Normalizer.stripInvisible(it).trim() }
                    if (!desc.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Leírás",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoOpen = false }) { Text("Bezárás") }
            }
        )
    }
}

// ---------------------------------------------------------------- kis elemek

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

@Composable
private fun InfoLine(label: String, value: String?) {
    val v = value?.trim()
    if (v.isNullOrBlank() || v == "N/A") return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = Normalizer.stripInvisible(v), style = MaterialTheme.typography.bodySmall)
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
    ttsColor: Color = Color.Transparent
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
    }
}
