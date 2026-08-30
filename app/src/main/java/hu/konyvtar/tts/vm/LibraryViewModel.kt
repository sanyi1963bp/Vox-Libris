package hu.konyvtar.tts.vm

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.BookFormats
import hu.konyvtar.tts.data.Catalog
import hu.konyvtar.tts.data.LibraryScanner
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.FileRow
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
 * A könyvtár állapota: a polc, a fájlböngésző, a beolvasás és az olvasási
 * számlálók. Egyetlen katalógussal dolgozik, amit az app maga épít a
 * telefonon lévő könyvekből.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    /** Az almappás keresés legfeljebb ennyi találatot ad vissza. */
    private val maxSearchHits = 3000

    /** Mit kell még beállítani induláskor. */
    enum class Setup { NONE, PICK_ROOT, OFFER_SCAN }

    /** Egy elérhető tárolókötet (belső tároló, SD-kártya, USB…). */
    data class StorageVol(val name: String, val path: String)

    /** Egy fájlformátum és a hozzá tartozó könyvek száma. */
    data class FormatCount(val ext: String, val count: Int)

    data class UiState(
        val currentDir: String = "",
        val entries: List<FileRow> = emptyList(),
        /** Útvonal -> olvasottság százalék. */
        val progress: Map<String, Double> = emptyMap(),
        val loading: Boolean = false,
        val query: String = "",
        val sortKey: SortKey = SortKey.NAME,
        val sortAsc: Boolean = true,
        val recursiveSearch: Boolean = false,
        val searchingDeep: Boolean = false,
        val scan: LibraryScanner.Progress = LibraryScanner.Progress(),
        val volumes: List<StorageVol> = emptyList(),
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
            currentDir = Prefs.rootPath(ctx),
            sortKey = runCatching { SortKey.valueOf(Prefs.sortKey(ctx)) }.getOrDefault(SortKey.NAME),
            sortAsc = Prefs.sortAsc(ctx),
            recursiveSearch = Prefs.searchRecursive(ctx),
            libSort = runCatching { SortKey.valueOf(Prefs.libSortKey(ctx)) }
                .getOrDefault(SortKey.TITLE),
            libAsc = Prefs.libSortAsc(ctx),
            volumes = listVolumes()
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

    /** Az összes csatolt tárolókötet (belső + SD-kártya + USB). */
    private fun listVolumes(): List<StorageVol> {
        return try {
            val ctx = getApplication<Application>()
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                if (v.state != Environment.MEDIA_MOUNTED &&
                    v.state != Environment.MEDIA_MOUNTED_READ_ONLY
                ) return@mapNotNull null
                val label = if (v.isPrimary) ctx.getString(R.string.storage_internal)
                else (v.getDescription(ctx) ?: ctx.getString(R.string.storage_sd))
                StorageVol(label, dir.absolutePath)
            }
        } catch (e: Exception) {
            emptyList()
        }
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
            val prog = withContext(Dispatchers.IO) { progressMap() }
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
            val prog = progressMap()
            _ui.value = _ui.value.copy(progress = prog)
        }
    }

    private fun progressMap(): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (p in AppDb.allProgress()) out[p.path] = p.displayPercent()
        return out
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    // ---------------------------------------------------------------- navigáció, lista

    fun navigateTo(dir: String) {
        _ui.value = _ui.value.copy(currentDir = dir)
        refresh()
    }

    fun up() {
        val cur = File(_ui.value.currentDir)
        val parent = cur.parentFile ?: return
        if (!parent.absolutePath.startsWith("/storage")) return
        navigateTo(parent.absolutePath)
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
        refresh()
    }

    fun setSort(key: SortKey) {
        val cur = _ui.value
        val asc = if (cur.sortKey == key) !cur.sortAsc else true
        Prefs.setSortKey(getApplication(), key.name)
        Prefs.setSortAsc(getApplication(), asc)
        _ui.value = cur.copy(sortKey = key, sortAsc = asc)
        refresh()
    }

    fun setRecursiveSearch(v: Boolean) {
        Prefs.setSearchRecursive(getApplication(), v)
        _ui.value = _ui.value.copy(recursiveSearch = v)
        refresh()
    }

    fun setRoot(path: String) {
        Prefs.setRootPath(getApplication(), path)
        val offerScan = _ui.value.catalogBooks == 0
        _ui.value = _ui.value.copy(
            currentDir = path,
            setup = if (offerScan) Setup.OFFER_SCAN else Setup.NONE
        )
        // Van már katalógus (pl. korábbi telepítésből): mutassuk is meg
        if (!offerScan && _ui.value.books.isEmpty()) loadLibrary()
        refresh()
    }

    /** A varázsló elhalasztása — a meglévő katalógus ilyenkor is jelenjen meg. */
    fun dismissSetup() {
        _ui.value = _ui.value.copy(setup = Setup.NONE)
        if (_ui.value.books.isEmpty() && _ui.value.catalogBooks > 0) loadLibrary()
    }

    fun refresh() {
        val state = _ui.value
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val deep = state.recursiveSearch && state.query.trim().length >= 2
            _ui.value = _ui.value.copy(searchingDeep = deep)
            if (deep) delay(350) // gépelés közben ne induljon minden betűre
            val rows = withContext(Dispatchers.IO) {
                if (deep) searchRecursive(state.currentDir, state.query, state.sortKey, state.sortAsc)
                else listDirectory(state.currentDir, state.query, state.sortKey, state.sortAsc)
            }
            val prog = withContext(Dispatchers.IO) { progressMap() }
            _ui.value = _ui.value.copy(entries = rows, progress = prog, loading = false)
        }
    }

    /** Egy mappa tartalma, a katalógusból kiegészített cím/szerző adatokkal. */
    private fun listDirectory(
        dirPath: String,
        query: String,
        sortKey: SortKey,
        asc: Boolean
    ): List<FileRow> {
        val dir = File(dirPath)
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val dirs = ArrayList<FileRow>()
        val files = ArrayList<FileRow>()
        for (f in children) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                dirs.add(
                    FileRow(
                        path = f.absolutePath.replace('\\', '/'),
                        name = name, ext = "", isDir = true,
                        size = 0, mtime = f.lastModified()
                    )
                )
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in BookFormats.ALL) continue
                files.add(
                    FileRow(
                        path = f.absolutePath.replace('\\', '/'),
                        name = name, ext = ext, isDir = false,
                        size = f.length(), mtime = f.lastModified()
                    )
                )
            }
        }

        val enriched = enrich(files)
        val q = query.trim().lowercase()
        val fd = if (q.isEmpty()) dirs else dirs.filter { it.name.lowercase().contains(q) }
        val ff = if (q.isEmpty()) enriched else enriched.filter { matches(it, q) }
        return fd.sortedBy { it.name.lowercase() } + sortRows(ff, sortKey, asc)
    }

    private fun matches(r: FileRow, q: String): Boolean =
        r.name.lowercase().contains(q) ||
            (r.cim?.lowercase()?.contains(q) ?: false) ||
            (r.szerzo?.lowercase()?.contains(q) ?: false)

    /** Cím és szerző hozzáadása a katalógusból. */
    private fun enrich(files: List<FileRow>): List<FileRow> {
        if (files.isEmpty()) return files
        val meta = Catalog.metaForPaths(files.map { it.path })
        if (meta.isEmpty()) return files
        return files.map { r ->
            val m = meta[r.path] ?: return@map r
            r.copy(konyvId = m.konyvId, cim = m.cim, szerzo = m.szerzo)
        }
    }

    private fun sortRows(rows: List<FileRow>, sortKey: SortKey, asc: Boolean): List<FileRow> {
        val cmp: Comparator<FileRow> = when (sortKey) {
            SortKey.NAME -> compareBy { it.name.lowercase() }
            SortKey.SIZE -> compareBy { it.size }
            SortKey.DATE -> compareBy { it.mtime }
            SortKey.TITLE -> compareBy(nullsLast()) { it.cim?.lowercase() }
            SortKey.AUTHOR -> compareBy(nullsLast()) { it.szerzo?.lowercase() }
            SortKey.FORMAT -> compareBy { it.ext }
        }
        val sorted = rows.sortedWith(cmp)
        return if (asc) sorted else sorted.reversed()
    }

    /**
     * Keresés az aktuális mappában ÉS minden almappájában: bejárja a
     * fájlrendszert, majd a találatokat kiegészíti a katalógusból, így cím és
     * szerző szerint is illeszkedhetnek.
     */
    private fun searchRecursive(
        rootPath: String,
        query: String,
        sortKey: SortKey,
        asc: Boolean
    ): List<FileRow> {
        val q = query.trim().lowercase()
        val found = LinkedHashMap<String, FileRow>()
        val stack = ArrayDeque<File>()
        stack.addLast(File(rootPath))
        while (stack.isNotEmpty() && found.size < maxSearchHits) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: continue
            for (f in children) {
                if (found.size >= maxSearchHits) break
                val name = f.name
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (dir.name == "Android" && (name == "data" || name == "obb")) continue
                    stack.addLast(f)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in BookFormats.ALL) continue
                    val path = f.absolutePath.replace('\\', '/')
                    if (found.containsKey(path)) continue
                    found[path] = FileRow(
                        path = path, name = name, ext = ext, isDir = false,
                        size = f.length(), mtime = f.lastModified()
                    )
                }
            }
        }
        val enriched = enrich(found.values.toList())
        return sortRows(enriched.filter { matches(it, q) }, sortKey, asc)
    }

    // ---------------------------------------------------------------- beolvasás

    /** A könyvtár beolvasása: metaadat-kinyerés és katalógusba írás. */
    fun startScan(includePdf: Boolean = true) {
        if (_ui.value.scan.running) return
        val ctx = getApplication<Application>()
        val root = File(_ui.value.currentDir)
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
            withContext(Dispatchers.Main) {
                loadLibrary()
                refresh()
            }
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
            refresh()
            onDone(removed)
        }
    }
}
