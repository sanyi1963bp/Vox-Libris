package hu.konyvtar.tts.data

import android.content.Context
import hu.konyvtar.tts.model.FileRow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rekurzív könyvtárszkennelés + párosítás a katalógussal.
 * Az eredmény a scan_cache táblába kerül, így az újraszkennelés inkrementális:
 * a változatlan (méret+dátum azonos) fájlokat nem párosítjuk újra.
 */
object FileScanner {

    /** Könyvformátumok, amiket listázunk. */
    val EBOOK_EXTS = setOf(
        "epub", "pdf", "txt", "fb2", "mobi", "prc", "azw", "azw3",
        "rtf", "doc", "docx", "htm", "html", "djvu"
    )

    data class ScanProgress(
        val running: Boolean = false,
        val filesFound: Int = 0,
        val matched: Int = 0,
        val currentPath: String = "",
        val done: Boolean = false,
        val error: String? = null
    )

    val cancelFlag = AtomicBoolean(false)

    /**
     * Teljes rekurzív szkennelés a [root] alatt.
     * A [onProgress] a fő szálra nem vált — a hívó dolga (ViewModel StateFlow-t frissít).
     */
    fun scanRecursive(
        context: Context,
        root: File,
        onProgress: (ScanProgress) -> Unit
    ): ScanProgress {
        cancelFlag.set(false)
        var found = 0
        var matched = 0

        val catalog = CatalogHolder.get(context)
        val matcher: Matcher? = if (catalog != null) {
            onProgress(ScanProgress(running = true, currentPath = "Katalógus betöltése…"))
            val exact = CatalogHolder.nameIndex(context) ?: HashMap()
            val norm = CatalogHolder.normIndex(context) ?: HashMap()
            val briefs = catalog.loadAllBriefs()
            Matcher(exact, norm, briefs)
        } else {
            null
        }

        val batch = ArrayList<FileRow>(512)
        val unresolvedIds = HashSet<Long>()

        fun flush() {
            if (batch.isEmpty()) return
            // A konyv_id-hez tartozó szerző/cím kitöltése kötegelve
            if (catalog != null && unresolvedIds.isNotEmpty()) {
                val briefs = catalog.briefsByIds(unresolvedIds)
                for (i in batch.indices) {
                    val r = batch[i]
                    if (r.konyvId != null && r.cim == null) {
                        val b = briefs[r.konyvId]
                        if (b != null) {
                            batch[i] = r.copy(
                                szerzo = Normalizer.stripInvisible(b.first),
                                cim = Normalizer.stripInvisible(b.second)
                            )
                        }
                    }
                }
                unresolvedIds.clear()
            }
            AppDb.upsertScanRows(batch)
            batch.clear()
        }

        fun walk(dir: File) {
            if (cancelFlag.get()) return
            val children = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: return
            for (f in children) {
                if (cancelFlag.get()) return
                val name = f.name
                if (name.startsWith(".")) continue
                if (f.isDirectory) {
                    // Android/data és társai kihagyása — úgysem olvasható, és lassítana
                    if (dir.name == "Android" && (name == "data" || name == "obb")) continue
                    walk(f)
                } else {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in EBOOK_EXTS) continue
                    found++
                    val size = f.length()
                    val mtime = f.lastModified()
                    val cached = AppDb.cachedForPath(f.absolutePath)
                    if (cached != null && cached.size == size && cached.mtime == mtime &&
                        (cached.konyvId != null || matcher == null)
                    ) {
                        if (cached.konyvId != null) matched++
                    } else {
                        val m = matcher?.match(name)
                        if (m != null) {
                            matched++
                            unresolvedIds.add(m.konyvId)
                        }
                        batch.add(
                            FileRow(
                                path = f.absolutePath.replace('\\', '/'),
                                name = name,
                                ext = ext,
                                isDir = false,
                                size = size,
                                mtime = mtime,
                                konyvId = m?.konyvId,
                                cim = null,
                                szerzo = null,
                                matchMode = m?.mode
                            )
                        )
                        if (batch.size >= 400) flush()
                    }
                    if (found % 200 == 0) {
                        onProgress(
                            ScanProgress(
                                running = true,
                                filesFound = found,
                                matched = matched,
                                currentPath = f.parent ?: ""
                            )
                        )
                    }
                }
            }
        }

        return try {
            walk(root)
            flush()
            val final = ScanProgress(
                running = false,
                filesFound = found,
                matched = matched,
                done = !cancelFlag.get()
            )
            onProgress(final)
            final
        } catch (e: Exception) {
            val final = ScanProgress(
                running = false,
                filesFound = found,
                matched = matched,
                error = e.message ?: "Ismeretlen hiba a szkennelés közben"
            )
            onProgress(final)
            final
        }
    }

    /**
     * Gyors, mappa-szintű párosítás böngészés közben: csak a fizikai_fajlok
     * fájlnév-indexét használja (olcsó), a teljes cím-egyeztetést nem.
     */
    fun quickEnrichDir(context: Context, files: List<FileRow>): List<FileRow> {
        val catalog = CatalogHolder.get(context) ?: return files
        val exact = CatalogHolder.nameIndex(context) ?: return files
        val norm = CatalogHolder.normIndex(context) ?: HashMap()

        val toResolve = HashSet<Long>()
        val enriched = files.map { r ->
            if (r.isDir || r.konyvId != null) return@map r
            val id = exact[r.name.lowercase()]
                ?: norm[Normalizer.norm(r.name.substringBeforeLast('.'))]
            if (id != null) {
                toResolve.add(id)
                r.copy(konyvId = id, matchMode = "fajlnev")
            } else {
                r
            }
        }
        if (toResolve.isEmpty()) return enriched
        val briefs = catalog.briefsByIds(toResolve)
        val out = enriched.map { r ->
            if (r.konyvId != null && r.cim == null) {
                val b = briefs[r.konyvId]
                if (b != null) {
                    r.copy(
                        szerzo = Normalizer.stripInvisible(b.first),
                        cim = Normalizer.stripInvisible(b.second)
                    )
                } else r
            } else r
        }
        // Az újonnan párosítottakat elmentjük a gyorsítótárba is
        AppDb.upsertScanRows(out.filter { !it.isDir && it.konyvId != null })
        return out
    }
}
