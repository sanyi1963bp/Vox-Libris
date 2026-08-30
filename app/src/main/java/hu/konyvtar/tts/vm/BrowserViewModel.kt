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
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.SortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A fájlböngésző állapota: hol járunk a fájlrendszerben, mit mutatunk, és
 * hogyan keresünk benne.
 *
 * Szándékosan külön a [LibraryViewModel]-től. Korábban egyetlen osztály
 * vitte a böngészést és a katalógust is, két párhuzamos szűrő- és rendező
 * logikával; ráadásul a böngészés „aktuális mappája" számított a könyvtár
 * gyökerének is, így egy almappába lépve a Beállítások rossz gyökeret
 * mutatott, és a beolvasás is csak azt a mappát járta be.
 */
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    /** Az almappás keresés legfeljebb ennyi találatot ad vissza. */
    private val maxSearchHits = 3000

    /** Egy elérhető tárolókötet (belső tároló, SD-kártya, USB…). */
    data class StorageVol(val name: String, val path: String)

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
        val volumes: List<StorageVol> = emptyList(),
        val message: String? = null
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var listJob: Job? = null

    init {
        val ctx = app
        _ui.value = _ui.value.copy(
            currentDir = Prefs.rootPath(ctx),
            sortKey = runCatching { SortKey.valueOf(Prefs.sortKey(ctx)) }.getOrDefault(SortKey.NAME),
            sortAsc = Prefs.sortAsc(ctx),
            recursiveSearch = Prefs.searchRecursive(ctx),
            volumes = listVolumes()
        )
        refresh()
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

    // ---------------------------------------------------------------- navigáció

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

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
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
            val prog = withContext(Dispatchers.IO) { AppDb.progressByPath() }
            _ui.value = _ui.value.copy(entries = rows, progress = prog, loading = false)
        }
    }

    // ---------------------------------------------------------------- listázás

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
}
