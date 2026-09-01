package hu.konyvtar.tts.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import hu.konyvtar.tts.model.CatalogBook
import hu.konyvtar.tts.model.FileMeta
import hu.konyvtar.tts.model.ShelfBook
import java.io.File

/**
 * Az app EGYETLEN katalógusa: a telefonon lévő könyvekből, a fájlok saját
 * metaadataiból épül. Szándékosan látható fájl a Letöltések mappában, hogy
 * túlélje az app újratelepítését, és PC-n is megnyitható legyen.
 *
 * Séma: `konyvek` (egy mű) + `fizikai_fajlok` (a művhöz tartozó fájlok).
 * Ugyanaz a séma, mint korábban, így a már meglévő katalógusok változatlanul
 * használhatók és bővíthetők.
 */
object Catalog {

    const val FILE_NAME = "sajat_katalogus.db"

    @Volatile
    private var db: SQLiteDatabase? = null

    /** A katalógusfájl helye. */
    fun file(): File {
        val downloads = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "KonyvtarTTS")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    /** Létezik-e már katalógus, van-e benne bármi. */
    fun exists(): Boolean = file().let { it.exists() && it.length() > 0 }

    /** Megnyitja (szükség esetén létrehozza) a katalógust. */
    @Synchronized
    fun open(): SQLiteDatabase? {
        db?.let { if (it.isOpen) return it }
        return try {
            val f = file()
            f.parentFile?.mkdirs()
            val opened = SQLiteDatabase.openOrCreateDatabase(f, null)
            createSchema(opened)
            db = opened
            opened
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {
        }
        db = null
    }

    // ---------------------------------------------------------------- séma

    private fun createSchema(d: SQLiteDatabase) {
        d.execSQL(
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
        d.execSQL(
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
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_szerzo ON konyvek(szerzo)")
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_cim ON konyvek(cim)")
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_ff_konyv ON fizikai_fajlok(konyv_id)")
    }

    // ---------------------------------------------------------------- lekérdezések

    data class Counts(val books: Int, val files: Int)

    fun counts(): Counts {
        val d = open() ?: return Counts(0, 0)
        return try {
            val b = d.rawQuery("SELECT COUNT(*) FROM konyvek", null).use {
                if (it.moveToNext()) it.getInt(0) else 0
            }
            val f = d.rawQuery("SELECT COUNT(*) FROM fizikai_fajlok", null).use {
                if (it.moveToNext()) it.getInt(0) else 0
            }
            Counts(b, f)
        } catch (e: Exception) {
            Counts(0, 0)
        }
    }

    private const val BOOK_COLS =
        "id, ncore_id, szerzo, cim, kiado, kiadas_eve, isbn, sorozat, sorozat_szama, " +
            "cimkek, leiras, formatum, meret, feltoltve_datum"

    private fun bookFrom(c: android.database.Cursor) = CatalogBook(
        id = c.getLong(0),
        ncoreId = c.getString(1),
        szerzo = c.getString(2),
        cim = c.getString(3),
        kiado = c.getString(4),
        kiadasEve = c.getString(5),
        isbn = c.getString(6),
        sorozat = c.getString(7),
        sorozatSzama = c.getString(8),
        cimkek = c.getString(9),
        leiras = c.getString(10),
        formatum = c.getString(11),
        meret = c.getString(12),
        feltoltveDatum = c.getString(13)
    )

    fun bookById(id: Long): CatalogBook? {
        val d = open() ?: return null
        return try {
            d.rawQuery("SELECT $BOOK_COLS FROM konyvek WHERE id = ?", arrayOf(id.toString()))
                .use { if (it.moveToNext()) bookFrom(it) else null }
        } catch (e: Exception) {
            null
        }
    }

    /** Egy fájlhoz tartozó könyv teljes adata. */
    fun bookForPath(path: String): CatalogBook? {
        val d = open() ?: return null
        return try {
            d.rawQuery(
                "SELECT $BOOK_COLS FROM konyvek WHERE id = " +
                    "(SELECT konyv_id FROM fizikai_fajlok WHERE fajl_utvonal = ?)",
                arrayOf(path)
            ).use { if (it.moveToNext()) bookFrom(it) else null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Több fájl rövid adata egyszerre (útvonal -> cím/szerző), a listákhoz.
     * Kötegelt lekérdezés, hogy nagy mappáknál se legyen lassú.
     */
    fun metaForPaths(paths: Collection<String>): Map<String, FileMeta> {
        val out = HashMap<String, FileMeta>(paths.size * 2)
        val d = open() ?: return out
        if (paths.isEmpty()) return out
        try {
            val list = paths.toList()
            var i = 0
            while (i < list.size) {
                val chunk = list.subList(i, minOf(i + 400, list.size))
                val q = chunk.joinToString(",") { "?" }
                d.rawQuery(
                    "SELECT ff.fajl_utvonal, ff.konyv_id, k.cim, k.szerzo " +
                        "FROM fizikai_fajlok ff LEFT JOIN konyvek k ON k.id = ff.konyv_id " +
                        "WHERE ff.fajl_utvonal IN ($q)",
                    chunk.toTypedArray()
                ).use { c ->
                    while (c.moveToNext()) {
                        out[c.getString(0)] = FileMeta(
                            konyvId = if (c.isNull(1)) null else c.getLong(1),
                            cim = c.getString(2)?.let { Normalizer.stripInvisible(it) },
                            szerzo = c.getString(3)?.let { Normalizer.stripInvisible(it) }
                        )
                    }
                }
                i += 400
            }
        } catch (_: Exception) {
        }
        return out
    }

    /**
     * A teljes könyvtár: minden mű, egy-egy hozzá tartozó fájllal, cím
     * szerint rendezve. Szűrés nélkül adja vissza az egészet — a lista
     * képernyő a memóriában szűr tovább, így a gépelés több ezer könyvnél is
     * azonnali marad, és nem terheljük az adatbázist minden leütésnél.
     */
    fun allBooks(): List<ShelfBook> {
        val d = open() ?: return emptyList()
        val out = ArrayList<ShelfBook>(1024)
        try {
            d.rawQuery(
                """
                SELECT k.id, k.cim, k.szerzo, k.formatum,
                       (SELECT ff.fajl_utvonal FROM fizikai_fajlok ff
                        WHERE ff.konyv_id = k.id ORDER BY ff.id LIMIT 1)
                FROM konyvek k
                ORDER BY (k.cim IS NULL OR k.cim = ''), k.cim COLLATE NOCASE
                """.trimIndent(),
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val path = c.getString(4) ?: continue
                    val fileName = path.substringAfterLast('/')
                    val title = c.getString(1)
                        ?.let { Normalizer.stripInvisible(it).trim() }
                        ?.takeIf { it.isNotEmpty() }
                        ?: fileName.substringBeforeLast('.')
                    val author = c.getString(2)
                        ?.let { Normalizer.stripInvisible(it).trim() } ?: ""
                    out.add(
                        ShelfBook(
                            id = c.getLong(0),
                            title = title,
                            author = author,
                            format = c.getString(3) ?: "",
                            path = path,
                            ext = fileName.substringAfterLast('.', "").lowercase(),
                            keyTitle = Normalizer.foldAll(title),
                            keyAuthor = Normalizer.foldAll(author),
                            keyFile = Normalizer.foldAll(fileName),
                            letterTitle = Normalizer.letterOf(title),
                            letterAuthor = Normalizer.letterOf(author)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    // ---------------------------------------------------------------- karbantartás

    /** A katalógusban lévő összes fájlútvonal (a beolvasás kihagyja őket). */
    fun knownPaths(): HashSet<String> {
        val out = HashSet<String>(4096)
        val d = open() ?: return out
        try {
            d.rawQuery("SELECT fajl_utvonal FROM fizikai_fajlok", null).use { c ->
                while (c.moveToNext()) out.add(c.getString(0))
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Normalizált cím+szerző kulcs -> könyv id, a duplikátumok elkerüléséhez. */
    fun bookKeyIndex(): HashMap<String, Long> {
        val out = HashMap<String, Long>(4096)
        val d = open() ?: return out
        try {
            d.rawQuery("SELECT id, cim, szerzo FROM konyvek", null).use { c ->
                while (c.moveToNext()) {
                    val key = bookKey(c.getString(1), c.getString(2))
                    if (key != "|") out[key] = c.getLong(0)
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun bookKey(title: String?, author: String?): String {
        val t = Normalizer.norm(title ?: "")
        val a = Normalizer.norm(author ?: "")
            .split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")
        return "$a|$t"
    }

    /** Új könyv felvétele; a beszúrt sor azonosítójával tér vissza. */
    fun insertBook(values: ContentValues): Long {
        val d = open() ?: return -1
        return try {
            d.insert("konyvek", null, values)
        } catch (e: Exception) {
            -1
        }
    }

    fun insertFile(values: ContentValues): Long {
        val d = open() ?: return -1
        return try {
            d.insert("fizikai_fajlok", null, values)
        } catch (e: Exception) {
            -1
        }
    }

    fun beginTransaction() {
        try {
            open()?.beginTransaction()
        } catch (_: Exception) {
        }
    }

    fun endTransaction(successful: Boolean) {
        try {
            open()?.let {
                if (successful) it.setTransactionSuccessful()
                it.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    /** A fájl új helyre került: a katalógus is kövesse. */
    fun updatePath(from: String, to: String): Boolean {
        val d = open() ?: return false
        return try {
            val v = ContentValues().apply {
                put("fajl_utvonal", to)
                put("fajl_nev", to.substringAfterLast('/'))
                put("formatum", to.substringAfterLast('.', "").uppercase())
            }
            d.update("fizikai_fajlok", v, "fajl_utvonal = ?", arrayOf(from)) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * A másolat felvétele a katalógusba, ugyanahhoz a műhöz. A séma eleve
     * megengedi, hogy egy könyvhöz több fájl tartozzon, így a másolat sem
     * lesz külön mű.
     */
    fun addCopy(from: String, to: String): Boolean {
        val d = open() ?: return false
        return try {
            val bookId = d.rawQuery(
                "SELECT konyv_id FROM fizikai_fajlok WHERE fajl_utvonal = ?", arrayOf(from)
            ).use { if (it.moveToNext() && !it.isNull(0)) it.getLong(0) else null }
            val v = ContentValues().apply {
                put("fajl_utvonal", to)
                put("fajl_nev", to.substringAfterLast('/'))
                put("formatum", to.substringAfterLast('.', "").uppercase())
                if (bookId != null) put("konyv_id", bookId)
                put("egyezes_szint", "masolat")
            }
            d.insertWithOnConflict(
                "fizikai_fajlok", null, v, SQLiteDatabase.CONFLICT_REPLACE
            ) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * A fájl bejegyzésének törlése. Ha a művöz nem marad fájl, a mű is
     * megszűnik — különben árván maradna a katalógusban.
     */
    fun deleteFile(path: String): Boolean {
        val d = open() ?: return false
        return try {
            d.delete("fizikai_fajlok", "fajl_utvonal = ?", arrayOf(path))
            d.execSQL(
                "DELETE FROM konyvek WHERE id NOT IN " +
                    "(SELECT konyv_id FROM fizikai_fajlok WHERE konyv_id IS NOT NULL)"
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Kitörli a katalógusból azokat a bejegyzéseket, amelyek fájlja már nem
     * létezik. Csak kézzel indítva fut — magától soha nem töröl semmit.
     */
    fun removeMissing(): Int {
        val d = open() ?: return 0
        val gone = ArrayList<String>()
        try {
            d.rawQuery("SELECT fajl_utvonal FROM fizikai_fajlok", null).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!File(p).exists()) gone.add(p)
                }
            }
            if (gone.isEmpty()) return 0
            d.beginTransaction()
            try {
                for (p in gone) {
                    d.delete("fizikai_fajlok", "fajl_utvonal = ?", arrayOf(p))
                }
                // Könyvek fájl nélkül: már senkihez sem tartoznak
                d.execSQL(
                    "DELETE FROM konyvek WHERE id NOT IN " +
                        "(SELECT konyv_id FROM fizikai_fajlok WHERE konyv_id IS NOT NULL)"
                )
                d.setTransactionSuccessful()
            } finally {
                d.endTransaction()
            }
        } catch (_: Exception) {
        }
        return gone.size
    }

    /** A teljes katalógus törlése (a fájl is). */
    fun deleteAll(): Boolean {
        close()
        return try {
            file().delete()
        } catch (e: Exception) {
            false
        }
    }
}
