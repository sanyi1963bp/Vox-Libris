package hu.konyvtar.tts.data

import android.content.ContentValues
import android.content.Context
import hu.konyvtar.tts.R
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A könyvtár beolvasása: végigjárja a gyökérmappát, a könyvfájlokból kinyeri a
 * metaadatot, és beírja őket a katalógusba.
 *
 * **Inkrementális**: a már bejegyzett fájlokat (útvonal szerint) érintetlenül
 * hagyja, csak az újakat dolgozza fel. Így új könyvek bemásolása után elég
 * újra lefuttatni, és soha nem veszik el, amit korábban gyűjtöttünk.
 */
object LibraryScanner {

    data class Progress(
        val running: Boolean = false,
        val scanned: Int = 0,
        val added: Int = 0,
        val skipped: Int = 0,
        val newBooks: Int = 0,
        val currentFile: String = "",
        val done: Boolean = false,
        val cancelled: Boolean = false,
        val error: String? = null
    )

    val cancelFlag = AtomicBoolean(false)

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1) String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else String.format(Locale.US, "%.1f MB", mb)
    }

    /**
     * Beolvasás a [root] alatt. A [onProgress] a hívó szálon fut — a hívó
     * dolga, hogy a felületre továbbítsa.
     */
    fun scan(
        context: Context,
        root: File,
        includePdf: Boolean,
        onProgress: (Progress) -> Unit
    ): Progress {
        cancelFlag.set(false)
        var scanned = 0
        var added = 0
        var skipped = 0
        var newBooks = 0

        if (Catalog.open() == null) {
            val p = Progress(error = context.getString(R.string.build_error_generic))
            onProgress(p)
            return p
        }

        try {
            onProgress(
                Progress(
                    running = true,
                    currentFile = context.getString(R.string.scan_loading_catalogue)
                )
            )
            val known = Catalog.knownPaths()
            val bookIds = Catalog.bookKeyIndex()

            val files = ArrayList<File>(2048)
            collectFiles(root, files)

            Catalog.beginTransaction()
            var committed = false
            try {
                for (f in files) {
                    if (cancelFlag.get()) break
                    scanned++
                    val path = f.absolutePath.replace('\\', '/')
                    if (path in known) {
                        skipped++
                    } else {
                        val ext = f.name.substringAfterLast('.', "").lowercase()
                        val meta = MetadataExtractor.extract(context, f, includePdf)
                        val key = Catalog.bookKey(meta.title, meta.author)

                        var bookId: Long = (if (key != "|") bookIds[key] else null) ?: -1L
                        if (bookId <= 0) {
                            val cv = ContentValues().apply {
                                put(
                                    "ncore_id",
                                    "local-" + java.util.UUID.randomUUID().toString().take(18)
                                )
                                put("szerzo", meta.author)
                                put("cim", meta.title)
                                put("leiras", meta.description)
                                put("kiado", meta.publisher)
                                put("kiadas_eve", meta.year)
                                put("isbn", meta.isbn)
                                put("sorozat", meta.series)
                                put("sorozat_szama", meta.seriesIndex)
                                put("cimkek", meta.tags)
                                put("formatum", ext)
                                put("meret", humanSize(f.length()))
                            }
                            bookId = Catalog.insertBook(cv)
                            if (bookId > 0) {
                                newBooks++
                                if (key != "|") bookIds[key] = bookId
                            }
                        }

                        val ff = ContentValues().apply {
                            put("fajl_utvonal", path)
                            put("fajl_nev", f.name)
                            put("formatum", ext)
                            if (bookId > 0) put("konyv_id", bookId) else putNull("konyv_id")
                            put("talalat_szerzo", meta.author)
                            put("talalat_cim", meta.title)
                            put("egyezes_szint", if (meta.hasRealData()) "sajat_meta" else "fajlnev")
                            put("minta", meta.source)
                            put("szerzo_szazalek", if (meta.author != null) 100.0 else 0.0)
                            put("cim_szazalek", if (meta.hasRealData()) 100.0 else 50.0)
                        }
                        if (Catalog.insertFile(ff) > 0) {
                            added++
                            known.add(path)
                        }
                    }

                    // Időnként lezárjuk a tranzakciót, hogy a haladás látszódjon
                    if (scanned % 25 == 0) {
                        Catalog.endTransaction(true)
                        onProgress(
                            Progress(
                                running = true, scanned = scanned, added = added,
                                skipped = skipped, newBooks = newBooks, currentFile = f.name
                            )
                        )
                        Catalog.beginTransaction()
                    }
                }
                Catalog.endTransaction(true)
                committed = true
            } finally {
                if (!committed) Catalog.endTransaction(false)
            }

            val final = Progress(
                running = false,
                scanned = scanned,
                added = added,
                skipped = skipped,
                newBooks = newBooks,
                done = !cancelFlag.get(),
                cancelled = cancelFlag.get()
            )
            onProgress(final)
            return final
        } catch (e: Exception) {
            val p = Progress(
                scanned = scanned, added = added, skipped = skipped, newBooks = newBooks,
                error = e.message ?: context.getString(R.string.scan_unknown_error)
            )
            onProgress(p)
            return p
        }
    }

    /** Könyvfájlok összegyűjtése rekurzívan. */
    private fun collectFiles(dir: File, out: ArrayList<File>) {
        if (cancelFlag.get()) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return
        for (f in children) {
            if (cancelFlag.get()) return
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (dir.name == "Android" && (f.name == "data" || f.name == "obb")) continue
                collectFiles(f, out)
            } else {
                val ext = f.name.substringAfterLast('.', "").lowercase()
                if (ext in BookFormats.ALL && ext in MetadataExtractor.SUPPORTED) {
                    out.add(f)
                }
            }
        }
    }
}
