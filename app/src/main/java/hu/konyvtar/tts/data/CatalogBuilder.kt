package hu.konyvtar.tts.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import hu.konyvtar.tts.R

/**
 * Katalógus építése a telefonon lévő könyvfájlokból, internet nélkül:
 * a metaadatokat magukból a fájlokból nyerjük ki.
 *
 * A létrehozott adatbázis sémája **pontosan ugyanaz**, mint a PC-n készült
 * katalógusé (`konyvek` + `fizikai_fajlok`), így az app és a PC-s eszközök is
 * változtatás nélkül kezelik.
 *
 * Az építés **inkrementális**: a már bejegyzett fájlokat (útvonal szerint)
 * érintetlenül hagyja, csak az újakat dolgozza fel. Így új könyvek bemásolása
 * után elég újra lefuttatni.
 */
object CatalogBuilder {

    /** Az épített katalógus alapértelmezett helye. */
    const val DEFAULT_NAME = "sajat_katalogus.db"

    data class Progress(
        val running: Boolean = false,
        val scanned: Int = 0,
        val added: Int = 0,
        val skipped: Int = 0,
        val newBooks: Int = 0,
        val currentFile: String = "",
        val done: Boolean = false,
        val cancelled: Boolean = false,
        val error: String? = null,
        val dbPath: String? = null
    )

    val cancelFlag = AtomicBoolean(false)

    fun defaultDbFile(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "KonyvtarTTS")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, DEFAULT_NAME)
    }

    // ---------------------------------------------------------------- séma

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS konyvek (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ncore_id TEXT UNIQUE NOT NULL,
                szerzo TEXT,
                cim TEXT,
                kep_utvonal TEXT,
                meret TEXT,
                feltoltve_datum TEXT,
                cimkek TEXT,
                leiras TEXT,
                buy_link TEXT,
                teljes_link TEXT,
                formatum TEXT,
                kiado TEXT,
                kiadas_eve TEXT,
                isbn TEXT,
                sorozat TEXT,
                sorozat_szama TEXT,
                letrehozva TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS fizikai_fajlok (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fajl_utvonal TEXT UNIQUE NOT NULL,
                fajl_nev TEXT,
                formatum TEXT,
                konyv_id INTEGER,
                talalat_szerzo TEXT,
                talalat_cim TEXT,
                egyezes_szint TEXT,
                minta TEXT,
                szerzo_szazalek REAL DEFAULT 0,
                cim_szazalek REAL DEFAULT 0,
                letrehozva TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                moly_id TEXT,
                FOREIGN KEY (konyv_id) REFERENCES konyvek(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_szerzo ON konyvek(szerzo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cim ON konyvek(cim)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_formatum ON konyvek(formatum)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_isbn ON konyvek(isbn)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ff_konyv ON fizikai_fajlok(konyv_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ff_szint ON fizikai_fajlok(egyezes_szint)")
    }

    /** Normalizált kulcs a könyv-duplikátumok felismeréséhez. */
    private fun bookKey(title: String?, author: String?): String {
        val t = Normalizer.norm(title ?: "")
        val a = Normalizer.norm(author ?: "")
            .split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")
        return "$a|$t"
    }

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / 1024.0 / 1024.0
        return if (mb < 1) String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else String.format(java.util.Locale.US, "%.1f MB", mb)
    }

    // ---------------------------------------------------------------- építés

    /**
     * Végigjárja a [root] alatti könyvfájlokat, és beírja őket a [dbFile]
     * katalógusba. A már meglévő bejegyzéseket nem bántja.
     */
    fun build(
        context: Context,
        root: File,
        dbFile: File,
        includePdf: Boolean,
        onProgress: (Progress) -> Unit
    ): Progress {
        cancelFlag.set(false)
        var scanned = 0
        var added = 0
        var skipped = 0
        var newBooks = 0

        val db: SQLiteDatabase = try {
            dbFile.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        } catch (e: Exception) {
            val p = Progress(error = context.getString(R.string.build_error_open, e.message ?: "?"))
            onProgress(p)
            return p
        }

        try {
            createSchema(db)

            // Már bejegyzett fájlok (útvonal szerint) — ezeket békén hagyjuk
            val known = HashSet<String>(4096)
            db.rawQuery("SELECT fajl_utvonal FROM fizikai_fajlok", null).use { c ->
                while (c.moveToNext()) known.add(c.getString(0))
            }

            // Meglévő könyvek kulcs -> id, a duplikátumok elkerüléséhez
            val bookIds = HashMap<String, Long>(4096)
            db.rawQuery("SELECT id, cim, szerzo FROM konyvek", null).use { c ->
                while (c.moveToNext()) {
                    val key = bookKey(c.getString(1), c.getString(2))
                    if (key != "|") bookIds[key] = c.getLong(0)
                }
            }

            val files = ArrayList<File>(2048)
            collectFiles(root, files)

            db.beginTransaction()
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
                        val key = bookKey(meta.title, meta.author)

                        var bookId: Long = (if (key != "|") bookIds[key] else null) ?: -1L
                        if (bookId <= 0) {
                            val cv = ContentValues().apply {
                                put("ncore_id", "local-" + java.util.UUID.randomUUID().toString().take(18))
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
                            bookId = db.insert("konyvek", null, cv)
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
                        if (db.insert("fizikai_fajlok", null, ff) > 0) {
                            added++
                            known.add(path)
                        }
                    }

                    if (scanned % 25 == 0) {
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        onProgress(
                            Progress(
                                running = true, scanned = scanned, added = added,
                                skipped = skipped, newBooks = newBooks,
                                currentFile = f.name
                            )
                        )
                        db.beginTransaction()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val final = Progress(
                running = false,
                scanned = scanned,
                added = added,
                skipped = skipped,
                newBooks = newBooks,
                done = !cancelFlag.get(),
                cancelled = cancelFlag.get(),
                dbPath = dbFile.absolutePath
            )
            onProgress(final)
            return final
        } catch (e: Exception) {
            val p = Progress(
                scanned = scanned, added = added, skipped = skipped, newBooks = newBooks,
                error = e.message ?: context.getString(R.string.build_error_generic)
            )
            onProgress(p)
            return p
        } finally {
            try {
                db.close()
            } catch (_: Exception) {
            }
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
                if (ext in FileScanner.EBOOK_EXTS && ext in MetadataExtractor.SUPPORTED) {
                    out.add(f)
                }
            }
        }
    }

    /** Statisztika egy meglévő épített katalógusról. */
    fun stats(dbFile: File): Triple<Int, Int, Int>? {
        if (!dbFile.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val books = db.rawQuery("SELECT COUNT(*) FROM konyvek", null).use { c ->
                    if (c.moveToNext()) c.getInt(0) else 0
                }
                val files = db.rawQuery("SELECT COUNT(*) FROM fizikai_fajlok", null).use { c ->
                    if (c.moveToNext()) c.getInt(0) else 0
                }
                val rich = db.rawQuery(
                    "SELECT COUNT(*) FROM fizikai_fajlok WHERE egyezes_szint = 'sajat_meta'", null
                ).use { c -> if (c.moveToNext()) c.getInt(0) else 0 }
                Triple(books, files, rich)
            }
        } catch (e: Exception) {
            null
        }
    }
}
