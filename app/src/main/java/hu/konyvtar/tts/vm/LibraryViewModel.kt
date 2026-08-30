package hu.konyvtar.tts.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Catalog
import hu.konyvtar.tts.data.LibraryScanner
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.ShelfBook
import hu.konyvtar.tts.model.SortKey
import hu.konyvtar.tts.model.displayPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A könyvtár állapota: a katalógus, a belőle szűrt lista, a beolvasás és az
 * olvasási számlálók. Egyetlen katalógussal dolgozik, amit az app maga épít
 * a telefonon lévő könyvekből.
 *
 * A fájlrendszer böngészése külön modellben van ([BrowserViewModel]) — a
 * kettőnek nincs köze egymáshoz azon túl, hogy ugyanabból a katalógusból
 * egészítik ki az adatokat.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    /** Az almappás keresés legfeljebb ennyi találatot ad vissza. */
    private val maxSearchHits = 3000

    /** Mit kell még beállítani induláskor. */
    enum class Setup { NONE, PICK_ROOT, OFFER_SCAN }

    /** Egy fájlformátum és a hozzá tartozó könyvek száma. */
    data class FormatCount(val ext: String, val count: Int)

    data class UiState(
        /** A könyvek gyökérmappája — ezt olvassa be a szkennelés. */
        val rootPath: String = "",
        /** Útvonal -> olvasottság százalék. */
        val progress: Map<String, Double> = emptyMap(),
        val scan: LibraryScanner.Progress = LibraryScanner.Progress(),
        /** Hány mű és hány fájl van a katalógusban. */
        val catalogBooks: Int = 0,
        val catalogFiles: Int = 0,
        /** Olvasási számlálók a nyitóképernyőre. */
        val finishedCount: Int = 0,
        val inProgressCount: Int = 0,
        /** A teljes könyvtár, ahogy a katalógusban van. */
        val books: List<ShelfBook> = emptyList(),
        /** Amit a szűrők után ténylegesen mutatunk. */
        val libRows: List<ShelfBook> = emptyList(),
        val libLoading: Boolean = false,
        val libQuery: String = "",
        /** Kiválasztott kezdőbetű a betűsávból; üres = mind. */
        val libLetter: String = "",
        /** Kiválasztott formátum; üres = mind. */
        val libFormat: String = "",
        val libSort: SortKey = SortKey.TITLE,
        val libAsc: Boolean = true,
        /** Csak azok a kezdőbetűk, amikhez tényleg van könyv. */
        val libLetters: List<String> = emptyList(),
        /** Formátum -> darabszám: ebből látszik, milyen fájlok vannak. */
        val libFormats: List<FormatCount> = emptyList(),
        val setup: Setup = Setup.NONE,
        val message: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** A képernyős olvasó célja (útvonal + megjelenítendő cím/szerző). */
    data class ReaderTarget(val path: String, val title: String, val author: String)

    var readerTarget: ReaderTarget? = null

    private var listJob: Job? = null
    private var libJob: Job? = null
    private var scanJob: Job? = null

    init {
        val ctx = app
        _ui.value = _ui.value.copy(
            rootPath = Prefs.rootPath(ctx),
            libSort = runCatching { SortKey.valueOf(Prefs.libSortKey(ctx)) }
                .getOrDefault(SortKey.TITLE),
            libAsc = Prefs.libSortAsc(ctx)
        )
        viewModelScope.launch { bootstrap() }
    }

    /** Indulás: mi van már beállítva, és mi hiányzik még. */
    private suspend fun bootstrap() {
        val ctx = getApplication<Application>()
        val counts = withContext(Dispatchers.IO) { Catalog.counts() }
        val setup = when {
            !Prefs.rootChosen(ctx) -> Setup.PICK_ROOT
            counts.books == 0 -> Setup.OFFER_SCAN
            else -> Setup.NONE
        }
        _ui.value = _ui.value.copy(
            catalogBooks = counts.books,
            catalogFiles = counts.files,
            setup = setup
        )
        refreshCounters()
        if (setup == Setup.NONE) loadLibrary()
    }

    // ---------------------------------------------------------------- számlálók, polc

    fun refreshCounters() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = AppDb.allProgress()
            val fin = rows.count { it.displayPercent() >= FINISHED_PERCENT }
            _ui.value = _ui.value.copy(
                finishedCount = fin,
                inProgressCount = rows.size - fin
            )
        }
    }

    /** A teljes katalógus beolvasása a memóriába — egyszer, induláskor. */
    fun loadLibrary() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(libLoading = true)
            val all = withContext(Dispatchers.IO) { Catalog.allBooks() }
            val prog = withContext(Dispatchers.IO) { AppDb.progressByPath() }
            val formats = withContext(Dispatchers.Default) {
                all.groupingBy { it.ext }.eachCount()
                    .map { FormatCount(it.key, it.value) }
                    .sortedWith(compareByDescending<FormatCount> { it.count }.thenBy { it.ext })
            }
            _ui.value = _ui.value.copy(
                books = all,
                progress = prog,
                libFormats = formats,
                libLetters = lettersOf(all, _ui.value.libSort),
                libLoading = false
            )
            recomputeLibrary(debounce = false)
        }
    }

    fun setLibQuery(q: String) {
        _ui.value = _ui.value.copy(libQuery = q)
        recomputeLibrary(debounce = true)
    }

    /** Betűsáv: ugyanarra a betűre újra koppintva a szűrés megszűnik. */
    fun setLibLetter(letter: String) {
        val cur = _ui.value.libLetter
        _ui.value = _ui.value.copy(libLetter = if (cur == letter) "" else letter)
        recomputeLibrary(debounce = false)
    }

    fun setLibFormat(ext: String) {
        val cur = _ui.value.libFormat
        _ui.value = _ui.value.copy(libFormat = if (cur == ext) "" else ext)
        recomputeLibrary(debounce = false)
    }

    fun setLibSort(key: SortKey) {
        val cur = _ui.value
        val asc = if (cur.libSort == key) !cur.libAsc else true
        Prefs.setLibSortKey(getApplication(), key.name)
        Prefs.setLibSortAsc(getApplication(), asc)
        _ui.value = cur.copy(
            libSort = key,
            libAsc = asc,
            libLetters = lettersOf(cur.books, key),
            // a betűszűrés a másik mezőre már nem érvényes
            libLetter = if (cur.libSort != key) "" else cur.libLetter
        )
        recomputeLibrary(debounce = false)
    }

    fun clearLibFilters() {
        _ui.value = _ui.value.copy(libQuery = "", libLetter = "", libFormat = "")
        recomputeLibrary(debounce = false)
    }

    private fun lettersOf(books: List<ShelfBook>, sort: SortKey): List<String> {
        val byAuthor = sort == SortKey.AUTHOR
        return books.map { it.letterFor(byAuthor) }.distinct()
            .sortedWith(compareBy({ it != "#" }, { it }))
    }

    /**
     * A szűrés és a rendezés a memóriában, a fő szálon kívül fut. Gépelésnél
     * rövid késleltetéssel, hogy ne induljon újra minden leütésre.
     */
    private fun recomputeLibrary(debounce: Boolean) {
        libJob?.cancel()
        libJob = viewModelScope.launch {
            if (debounce) delay(160)
            val s = _ui.value
            val rows = withContext(Dispatchers.Default) {
                val q = Normalizer.foldAll(s.libQuery.trim())
                val byAuthor = s.libSort == SortKey.AUTHOR
                var list = s.books.asSequence()
                if (q.isNotEmpty()) list = list.filter { it.matches(q) }
                if (s.libFormat.isNotEmpty()) list = list.filter { it.ext == s.libFormat }
                if (s.libLetter.isNotEmpty()) {
                    list = list.filter { it.letterFor(byAuthor) == s.libLetter }
                }
                sortBooks(list.toList(), s.libSort, s.libAsc)
            }
            _ui.value = _ui.value.copy(libRows = rows)
        }
    }

    /** Az üres mezős könyvek mindig a lista végére kerülnek. */
    private fun sortBooks(rows: List<ShelfBook>, sort: SortKey, asc: Boolean): List<ShelfBook> {
        val base: Comparator<ShelfBook> = when (sort) {
            SortKey.AUTHOR -> compareBy { it.keyAuthor }
            SortKey.FORMAT -> compareBy { it.ext }
            else -> compareBy { it.keyTitle }
        }
        val blank: Comparator<ShelfBook> = when (sort) {
            SortKey.AUTHOR -> compareBy { it.keyAuthor.isEmpty() }
            SortKey.FORMAT -> compareBy { it.ext.isEmpty() }
            else -> compareBy { it.keyTitle.isEmpty() }
        }
        val dir = if (asc) base else base.reversed()
        return rows.sortedWith(blank.then(dir).thenBy { it.keyTitle })
    }

    /**
     * Csak a haladás újratöltése — a felolvasóból visszatérve ettől mozdul
     * a lista zöld csíkja, anélkül hogy az egész katalógust újraolvasnánk.
     */
    fun refreshProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            val prog = AppDb.progressByPath()
            _ui.value = _ui.value.copy(progress = prog)
        }
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun setRoot(path: String) {
        Prefs.setRootPath(getApplication(), path)
        val offerScan = _ui.value.catalogBooks == 0
        _ui.value = _ui.value.copy(
            rootPath = path,
            setup = if (offerScan) Setup.OFFER_SCAN else Setup.NONE
        )
        // Van már katalógus (pl. korábbi telepítésből): mutassuk is meg
        if (!offerScan && _ui.value.books.isEmpty()) loadLibrary()
    }

    /** A varázsló elhalasztása — a meglévő katalógus ilyenkor is jelenjen meg. */
    fun dismissSetup() {
        _ui.value = _ui.value.copy(setup = Setup.NONE)
        if (_ui.value.books.isEmpty() && _ui.value.catalogBooks > 0) loadLibrary()
    }

    // ---------------------------------------------------------------- beolvasás

    /** A könyvtár beolvasása: metaadat-kinyerés és katalógusba írás. */
    fun startScan(includePdf: Boolean = true) {
        if (_ui.value.scan.running) return
        val ctx = getApplication<Application>()
        val root = File(_ui.value.rootPath)
        _ui.value = _ui.value.copy(
            scan = LibraryScanner.Progress(running = true),
            setup = Setup.NONE
        )
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            LibraryScanner.scan(ctx, root, includePdf) { p ->
                _ui.value = _ui.value.copy(scan = p)
            }
            val counts = Catalog.counts()
            _ui.value = _ui.value.copy(catalogBooks = counts.books, catalogFiles = counts.files)
            withContext(Dispatchers.Main) { loadLibrary() }
        }
    }

    fun cancelScan() {
        LibraryScanner.cancelFlag.set(true)
    }

    fun clearScanResult() {
        _ui.value = _ui.value.copy(scan = LibraryScanner.Progress())
    }

    /** Hiányzó fájlok eltávolítása a katalógusból — csak kézzel indítva. */
    fun removeMissing(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { Catalog.removeMissing() }
            val counts = withContext(Dispatchers.IO) { Catalog.counts() }
            _ui.value = _ui.value.copy(catalogBooks = counts.books, catalogFiles = counts.files)
            loadLibrary()
            onDone(removed)
        }
    }
}
