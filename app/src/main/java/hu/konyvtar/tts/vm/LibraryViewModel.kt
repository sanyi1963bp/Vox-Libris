package hu.konyvtar.tts.vm

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.CatalogBuilder
import hu.konyvtar.tts.data.CatalogHolder
import hu.konyvtar.tts.data.FileScanner
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A böngésző/katalógus képernyő állapota és műveletei.
 * A 70 ezres nagyságrendű listát SQL-ből töltjük, a rendezés/szűrés is ott fut.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    data class DbStatus(
        val path: String? = null,
        val opened: Boolean = false,
        val bookCount: Int = 0
    )

    /** Egy elérhető tárolókötet (belső tároló, SD-kártya, USB…). */
    data class StorageVol(val name: String, val path: String)

    data class UiState(
        val currentDir: String = "",
        val entries: List<FileRow> = emptyList(),
        val loading: Boolean = false,
        val flatMode: Boolean = false,
        val onlyMatched: Boolean = false,
        val query: String = "",
        val sortKey: SortKey = SortKey.NAME,
        val sortAsc: Boolean = true,
        val scan: FileScanner.ScanProgress = FileScanner.ScanProgress(),
        val build: CatalogBuilder.Progress = CatalogBuilder.Progress(),
        val db: DbStatus = DbStatus(),
        val volumes: List<StorageVol> = emptyList(),
        val cachedTotal: Int = 0,
        val cachedMatched: Int = 0,
        val message: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** Az egyszeri (single-tap) megnyitáshoz kiválasztott fájl. */
    var selectedFile: FileRow? = null

    /** A képernyős olvasó célja (útvonal + megjelenítendő cím/szerző). */
    data class ReaderTarget(val path: String, val title: String, val author: String)

    var readerTarget: ReaderTarget? = null

    private var scanJob: Job? = null
    private var listJob: Job? = null

    init {
        val ctx = app
        _ui.value = _ui.value.copy(
            currentDir = Prefs.rootPath(ctx),
            flatMode = Prefs.flatMode(ctx),
            sortKey = runCatching { SortKey.valueOf(Prefs.sortKey(ctx)) }.getOrDefault(SortKey.NAME),
            sortAsc = Prefs.sortAsc(ctx),
            volumes = listVolumes()
        )
        viewModelScope.launch { openDbFromPrefsOrDetect() }
        refresh()
    }

    /** Az összes csatolt tárolókötet (belső + SD-kártya + USB). */
    private fun listVolumes(): List<StorageVol> {
        return try {
            val sm = getApplication<Application>()
                .getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                if (v.state != Environment.MEDIA_MOUNTED &&
                    v.state != Environment.MEDIA_MOUNTED_READ_ONLY
                ) return@mapNotNull null
                val label = if (v.isPrimary) "Belső tároló"
                else (v.getDescription(getApplication()) ?: "SD-kártya")
                StorageVol(label, dir.absolutePath)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------------------------------------------------------------- adatbázis

    private suspend fun openDbFromPrefsOrDetect() {
        val ctx = getApplication<Application>()
        withContext(Dispatchers.IO) {
            var path = Prefs.dbPath(ctx)
            if (path == null || !File(path).exists()) {
                path = autoDetectDbPath()
                if (path != null) Prefs.setDbPath(ctx, path)
            }
            val cat = if (path != null) CatalogHolder.reopen(ctx, path) else null
            val status = DbStatus(
                path = path,
                opened = cat != null,
                bookCount = cat?.bookCount() ?: 0
            )
            _ui.value = _ui.value.copy(
                db = status,
                cachedTotal = AppDb.scanCacheCount(),
                cachedMatched = AppDb.scanCacheMatchedCount()
            )
        }
    }

    private fun autoDetectDbPath(): String? {
        val roots = LinkedHashSet<String>()
        roots.add(Environment.getExternalStorageDirectory().absolutePath)
        listVolumes().forEach { roots.add(it.path) }
        val candidates = ArrayList<File>()
        for (r in roots) {
            candidates.add(File(r, "ncore_konyvtar.db"))
            candidates.add(File(r, "Download/ncore_konyvtar.db"))
            candidates.add(File(r, "Documents/ncore_konyvtar.db"))
            candidates.add(File(r, "Books/ncore_konyvtar.db"))
        }
        return candidates.firstOrNull { it.exists() && it.length() > 0 }?.absolutePath
    }

    fun openDb(path: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val cat = CatalogHolder.reopen(ctx, path)
            _ui.value = _ui.value.copy(
                db = DbStatus(path = path, opened = cat != null, bookCount = cat?.bookCount() ?: 0),
                message = if (cat != null) "Adatbázis megnyitva: ${cat.bookCount()} könyv" else "Nem sikerült megnyitni az adatbázist. Biztosan a ncore_konyvtar.db-t választottad?"
            )
            refresh()
        }
    }

    // ---------------------------------------------------------------- navigáció, lista

    fun navigateTo(dir: String) {
        _ui.value = _ui.value.copy(currentDir = dir, flatMode = false)
        Prefs.setFlatMode(getApplication(), false)
        refresh()
    }

    fun up() {
        val cur = File(_ui.value.currentDir)
        val parent = cur.parentFile ?: return
        // A /storage fölé nem megyünk
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

    fun setFlatMode(flat: Boolean) {
        Prefs.setFlatMode(getApplication(), flat)
        _ui.value = _ui.value.copy(flatMode = flat)
        refresh()
    }

    fun setOnlyMatched(v: Boolean) {
        _ui.value = _ui.value.copy(onlyMatched = v)
        refresh()
    }

    fun setRoot(path: String) {
        Prefs.setRootPath(getApplication(), path)
        navigateTo(path)
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        val state = _ui.value
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, volumes = listVolumes())
            val rows = withContext(Dispatchers.IO) {
                if (state.flatMode) {
                    AppDb.allScanned(state.query, state.sortKey, state.sortAsc, state.onlyMatched)
                } else {
                    listDirectory(ctx, state.currentDir, state.query, state.sortKey, state.sortAsc)
                }
            }
            _ui.value = _ui.value.copy(entries = rows, loading = false)
        }
    }

    private fun listDirectory(
        ctx: Application,
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

        val cached = AppDb.cachedForDir(dirPath.replace('\\', '/'))
        val dirs = ArrayList<FileRow>()
        val files = ArrayList<FileRow>()
        for (f in children) {
            val name = f.name
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                dirs.add(
                    FileRow(
                        path = f.absolutePath.replace('\\', '/'),
                        name = name,
                        ext = "",
                        isDir = true,
                        size = 0,
                        mtime = f.lastModified()
                    )
                )
            } else {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in FileScanner.EBOOK_EXTS) continue
                val path = f.absolutePath.replace('\\', '/')
                val c = cached[path]
                files.add(
                    if (c != null && c.size == f.length() && c.mtime == f.lastModified()) {
                        c
                    } else {
                        FileRow(
                            path = path,
                            name = name,
                            ext = ext,
                            isDir = false,
                            size = f.length(),
                            mtime = f.lastModified()
                        )
                    }
                )
            }
        }

        // Gyors párosítás a még ismeretlen fájlokra (csak fájlnév-index, olcsó)
        val enrichedFiles = if (files.any { it.konyvId == null }) {
            FileScanner.quickEnrichDir(ctx, files)
        } else {
            files
        }

        val q = query.trim().lowercase()
        val filteredDirs = if (q.isEmpty()) dirs else dirs.filter { it.name.lowercase().contains(q) }
        val filteredFiles = if (q.isEmpty()) enrichedFiles else enrichedFiles.filter {
            it.name.lowercase().contains(q) ||
                (it.cim?.lowercase()?.contains(q) ?: false) ||
                (it.szerzo?.lowercase()?.contains(q) ?: false)
        }

        val cmp: Comparator<FileRow> = when (sortKey) {
            SortKey.NAME -> compareBy { it.name.lowercase() }
            SortKey.SIZE -> compareBy { it.size }
            SortKey.DATE -> compareBy { it.mtime }
            SortKey.TITLE -> compareBy(nullsLast()) { it.cim?.lowercase() }
            SortKey.AUTHOR -> compareBy(nullsLast()) { it.szerzo?.lowercase() }
        }
        val sortedDirs = filteredDirs.sortedBy { it.name.lowercase() }
        var sortedFiles = filteredFiles.sortedWith(cmp)
        if (!asc) sortedFiles = sortedFiles.reversed()
        return sortedDirs + sortedFiles
    }

    // ---------------------------------------------------------------- szkennelés

    fun startScan() {
        if (_ui.value.scan.running) return
        val ctx = getApplication<Application>()
        val root = File(_ui.value.currentDir)
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            FileScanner.scanRecursive(ctx, root) { p ->
                _ui.value = _ui.value.copy(scan = p)
            }
            _ui.value = _ui.value.copy(
                cachedTotal = AppDb.scanCacheCount(),
                cachedMatched = AppDb.scanCacheMatchedCount()
            )
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun cancelScan() {
        FileScanner.cancelFlag.set(true)
    }

    // ---------------------------------------------------------------- katalógus építése

    /**
     * Katalógus építése/frissítése a könyvfájlok saját metaadataiból.
     * A már bejegyzett fájlokat érintetlenül hagyja.
     */
    fun buildCatalog(includePdf: Boolean) {
        if (_ui.value.build.running) return
        val ctx = getApplication<Application>()
        val root = File(_ui.value.currentDir)
        val target = CatalogBuilder.defaultDbFile()
        _ui.value = _ui.value.copy(build = CatalogBuilder.Progress(running = true))
        viewModelScope.launch(Dispatchers.IO) {
            CatalogBuilder.build(ctx, root, target, includePdf) { p ->
                _ui.value = _ui.value.copy(build = p)
            }
        }
    }

    fun cancelBuild() {
        CatalogBuilder.cancelFlag.set(true)
    }

    fun clearBuildResult() {
        _ui.value = _ui.value.copy(build = CatalogBuilder.Progress())
    }
}
