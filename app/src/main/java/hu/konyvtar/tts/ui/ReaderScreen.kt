package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.Bookmark
import hu.konyvtar.tts.reader.ExtractException
import hu.konyvtar.tts.reader.Sentences
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * A könyv EGYETLEN képernyője: itt látszik a szöveg, és itt van minden
 * vezérlő is. Nincs külön lejátszó- és részletező-ablak.
 *
 * Ez a fájl az állapotot és a huzalozást tartja: mit töltünk be, hol
 * tartunk, mi történjen egy gombra. A megjelenítés négy külön fájlban van:
 * [ReaderTopBar], [ReaderControls], [ReaderText] és [BookmarksDialog].
 *
 * Gesztusok a szövegen:
 *  - dupla koppintás: felolvasás pontosan a megérintett mondattól
 *  - hosszú nyomás: műveletmenü a megérintett mondatra — vagy azonnali
 *    könyvjelző, ha a beállításokban úgy kérted; olyankor a menü egyszeri
 *    koppintásra jön elő
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    path: String,
    title: String,
    author: String,
    /** A minden nézetben jelen lévő alsó sáv adatai. */
    mainNav: MainNav,
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
    var bionic by remember { mutableStateOf(Prefs.bionic(context)) }
    val longPressBookmark = remember { Prefs.longPressBookmark(context) }

    /** Melyik bekezdés melyik karakterére nyomtál — ebből lesz a mondat. */
    var actionsAt by remember(path) { mutableStateOf<Pair<Int, Int>?>(null) }

    /**
     * Ideérkezéskor az aktuális mondat pár másodpercig erősebben világít,
     * aztán visszahalványul a szokásos kiemelésre.
     *
     * Az alsó sávból bármikor ide lehet ugrani, és ilyenkor a legelső kérdés
     * mindig ugyanaz: „hol is tartunk?". Ez a felvillanás egy pillantással
     * megválaszolja, anélkül hogy a szemnek végig kellene pásztáznia az
     * oldalt.
     */
    var spotlight by remember(path) { mutableStateOf(true) }
    LaunchedEffect(path) {
        spotlight = true
        delay(2500)
        spotlight = false
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchPos by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }

    var bookmarks by remember(path) { mutableStateOf<List<Bookmark>>(emptyList()) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var recapOpen by remember { mutableStateOf(false) }
    var charactersOpen by remember { mutableStateOf(false) }

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
                error = (e as? ExtractException)?.localized(context)
                    ?: context.getString(R.string.reader_load_error)
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
            Toast.makeText(
                context,
                context.getString(R.string.reader_bookmark_added, idx + 1),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** Felolvasás indítása ettől a ponttól — ide fut be a dupla koppintás is. */
    fun playFrom(paraIndex: Int, startChar: Int) {
        TtsService.playFile(
            context = context, path = path, title = title, author = author,
            konyvId = null, startIndex = paraIndex, startChar = startChar
        )
    }

    /** Felolvasás indítása/szüneteltetése; ha még nem ez a könyv szól, innen indul. */
    fun playPause() {
        if (sameBook) {
            TtsService.send(context, TtsService.ACTION_TOGGLE)
        } else {
            playFrom(listState.firstVisibleItemIndex, 0)
        }
    }

    /** Léptetőgomb: ha nem ez a könyv szól, előbb ide töltjük be. */
    fun nav(action: String) {
        if (!sameBook) {
            playFrom(listState.firstVisibleItemIndex, 0)
            return
        }
        TtsService.send(context, action)
    }

    val bookmarkedIdx = remember(bookmarks) { bookmarks.map { it.paraIndex }.toHashSet() }
    val chapterSet = remember(chapters) { chapters.toHashSet() }

    Scaffold(
        topBar = {
            ReaderTopBar(
                title = title,
                author = author,
                bookmarkCount = bookmarks.size,
                narrating = sameBook,
                search = ReaderSearch(
                    open = searchOpen,
                    query = query,
                    running = searching,
                    hitCount = matches.size,
                    hitPos = matchPos
                ),
                onBack = onBack,
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onQueryChange = { query = it },
                onJumpMatch = { jumpMatch(it) },
                onOpenSettings = onOpenSettings,
                onBookmarkHere = {
                    val idx = if (sameBook && tts.totalParas > 0) tts.paraIndex
                    else listState.firstVisibleItemIndex
                    addBookmarkAt(idx)
                },
                onOpenBookmarks = { bookmarksOpen = true },
                onOpenRecap = { recapOpen = true },
                onOpenCharacters = { charactersOpen = true },
                onOpenInfo = { infoOpen = true },
                onStopNarration = { TtsService.send(context, TtsService.ACTION_STOP) }
            )
        },
        bottomBar = {
            val paras = paragraphs
            // A vezérlősáv alatt ott a navigáció is: az olvasóból is egy
            // koppintással át lehessen menni a másik két nézetre.
            Column {
                ReaderControls(
                    status = ReaderStatus(
                        paraIndex = listState.firstVisibleItemIndex,
                        paraCount = paras?.size ?: 0,
                        chapterIndex = tts.chapterIndex,
                        chapterCount = tts.totalChapters,
                        percent = tts.percent,
                        listenedMs = tts.listenedMs,
                        narrating = sameBook && tts.totalParas > 0
                    ),
                    playing = playingHere,
                    follow = follow,
                    onToggleFollow = {
                        follow = !follow
                        Prefs.setReaderFollow(context, follow)
                        if (follow && sameBook && tts.totalParas > 0) {
                            scope.launch { listState.animateScrollToItem(tts.paraIndex) }
                        }
                    },
                    fontSp = fontSp,
                    onFontChange = {
                        fontSp = it
                        Prefs.setReaderFont(context, it)
                    },
                    bionic = bionic,
                    onToggleBionic = {
                        bionic = !bionic
                        Prefs.setBionic(context, bionic)
                    },
                    speed = speed,
                    onSpeedChange = { speed = it },
                    onSpeedDone = {
                        if (sameBook) {
                            TtsService.send(context, TtsService.ACTION_SET_SPEED) {
                                putExtra(TtsService.EXTRA_VALUE, speed)
                            }
                        } else {
                            Prefs.setSpeed(context, speed)
                        }
                    },
                    pitch = pitch,
                    onPitchChange = { pitch = it },
                    onPitchDone = {
                        if (sameBook) {
                            TtsService.send(context, TtsService.ACTION_SET_PITCH) {
                                putExtra(TtsService.EXTRA_VALUE, pitch)
                            }
                        } else {
                            Prefs.setPitch(context, pitch)
                        }
                    },
                    sliderFraction = if (paras != null && paras.size > 1) {
                        sliderDrag ?: (listState.firstVisibleItemIndex.toFloat() / (paras.size - 1))
                    } else null,
                    onSeekDrag = { sliderDrag = it },
                    onSeekDone = {
                        val f = sliderDrag
                        val size = paras?.size ?: 0
                        if (f != null && size > 1) {
                            scope.launch {
                                listState.scrollToItem(
                                    (f * (size - 1)).roundToInt().coerceIn(0, size - 1)
                                )
                                sliderDrag = null
                            }
                        }
                    },
                    onPlayPause = { playPause() },
                    onNav = { nav(it) }
                )
                MainNavBar(mainNav)
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
                    Text(
                        stringResource(R.string.reader_loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> ReaderText(
                    paragraphs = paras,
                    listState = listState,
                    chapters = chapterSet,
                    bookmarked = bookmarkedIdx,
                    fontSp = fontSp,
                    query = query.trim(),
                    currentMatch = matches.getOrNull(matchPos) ?: -1,
                    narrated = if (sameBook && tts.totalParas > 0) {
                        NarratedSentence(tts.paraIndex, tts.sentStart, tts.sentEnd)
                    } else NarratedSentence.NONE,
                    bionic = bionic,
                    spotlight = spotlight && sameBook,
                    longPressBookmark = longPressBookmark,
                    onPlayFrom = { idx, char -> playFrom(idx, char) },
                    onBookmark = { addBookmarkAt(it) },
                    onActions = { idx, char -> actionsAt = idx to char }
                )
            }
        }
    }

    // A műveletmenü a megérintett MONDATTAL dolgozik: ugyanazzal a darabbal,
    // amit a felolvasó is egy egységként mond ki.
    val at = actionsAt
    val atPara = at?.let { paragraphs?.getOrNull(it.first) }
    if (at != null && atPara != null) {
        val (s, e) = Sentences.boundsAt(atPara, at.second)
        ReaderActionsDialog(
            sentence = atPara.substring(s, e),
            title = title,
            author = author,
            onBookmark = { addBookmarkAt(at.first) },
            onDismiss = { actionsAt = null }
        )
    }

    // Az olvasási pozíció, ameddig a két „tudás a könyvről" funkció nézhet.
    // Ha ez a könyv szól, a most elhangzott mondat VÉGÉIG — ennél tovább
    // nézni spoiler volna. Ha csak nézelődsz benne, a látható bekezdésig.
    val paras = paragraphs
    val knowPara = if (sameBook && tts.totalParas > 0) tts.paraIndex
    else listState.firstVisibleItemIndex
    val knowChar = if (sameBook && tts.totalParas > 0) tts.sentEnd
    else (paras?.getOrNull(knowPara)?.length ?: 0)

    if (recapOpen && paras != null) {
        RecapDialog(
            paragraphs = paras,
            toPara = knowPara,
            toChar = knowChar,
            onDismiss = { recapOpen = false }
        )
    }

    if (charactersOpen && paras != null) {
        CharactersDialog(
            paragraphs = paras,
            bookPath = path,
            toPara = knowPara,
            toChar = knowChar,
            onGoTo = { idx ->
                charactersOpen = false
                scope.launch { listState.scrollToItem(idx) }
            },
            onDismiss = { charactersOpen = false }
        )
    }

    if (bookmarksOpen) {
        BookmarksDialog(
            bookmarks = bookmarks,
            onGoTo = { idx ->
                bookmarksOpen = false
                scope.launch { listState.scrollToItem(idx) }
            },
            onDelete = { id ->
                scope.launch {
                    withContext(Dispatchers.IO) { AppDb.deleteBookmark(id) }
                    bookmarks = withContext(Dispatchers.IO) { AppDb.bookmarksFor(path) }
                }
            },
            onDismiss = { bookmarksOpen = false }
        )
    }

    if (infoOpen) {
        BookDetailsDialog(
            book = BookRef(path, title, author),
            onDismiss = { infoOpen = false },
            // Az épp olvasott könyvhöz jegyzetet írhatsz, de átnevezni vagy
            // törölni magad alól nem engedjük
            onFileChanged = { },
            allowFileOps = false,
            // Csak az olvasó tudja, hány bekezdésre és fejezetre bomlott a könyv
            extraLines = listOf(
                stringResource(R.string.info_paragraphs) to paragraphs?.size?.toString(),
                stringResource(R.string.info_chapters) to
                    chapters.size.takeIf { it > 0 }?.toString()
            )
        )
    }
}
