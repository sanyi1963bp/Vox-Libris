package hu.konyvtar.tts.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import hu.konyvtar.tts.model.BookBrief
import hu.konyvtar.tts.model.CatalogBook
import java.io.File

/**
 * A külső, csak olvasható ncore_konyvtar.db elérése.
 * Séma: konyvek(id, ncore_id, szerzo, cim, ..., leiras), fizikai_fajlok(fajl_nev, konyv_id, ...).
 */
class CatalogDb private constructor(val path: String, private val db: SQLiteDatabase) {

    companion object {
        /**
         * Megnyitás csak olvasásra. Ha WAL maradvány miatt nem sikerül,
         * írható módban próbáljuk (az checkpointol), végül null.
         */
        fun open(path: String): CatalogDb? {
            val f = File(path)
            if (!f.exists() || !f.isFile) return null
            val db = try {
                SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                try {
                    SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE)
                } catch (e2: Exception) {
                    null
                }
            } ?: return null
            // Séma-ellenőrzés: tényleg a katalógus-e
            val ok = try {
                db.rawQuery("SELECT id FROM konyvek LIMIT 1", null).use { true }
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                db.close()
                return null
            }
            return CatalogDb(path, db)
        }
    }

    fun close() {
        try {
            db.close()
        } catch (_: Exception) {
        }
    }

    fun bookCount(): Int {
        return try {
            db.rawQuery("SELECT COUNT(*) FROM konyvek", null).use { c ->
                if (c.moveToNext()) c.getInt(0) else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun bookById(id: Long): CatalogBook? {
        try {
            db.rawQuery(
                """
                SELECT id, ncore_id, szerzo, cim, kiado, kiadas_eve, isbn, sorozat, sorozat_szama,
                       cimkek, leiras, formatum, meret, feltoltve_datum
                FROM konyvek WHERE id = ?
                """.trimIndent(),
                arrayOf(id.toString())
            ).use { c ->
                if (c.moveToNext()) {
                    return CatalogBook(
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
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * A PC-n már elvégzett fájl↔könyv párosítás betöltése:
     * fajl_nev (kisbetűs) -> konyv_id. Ez a leggyorsabb és legpontosabb egyeztetés.
     */
    fun loadFileNameIndex(): HashMap<String, Long> {
        val out = HashMap<String, Long>(16384)
        try {
            db.rawQuery(
                "SELECT fajl_nev, konyv_id FROM fizikai_fajlok WHERE konyv_id IS NOT NULL AND fajl_nev IS NOT NULL",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    out[name.lowercase()] = c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Minden könyv rövid adata a cím/szerző alapú tartalék-párosításhoz. */
    fun loadAllBriefs(): ArrayList<BookBrief> {
        val out = ArrayList<BookBrief>(70000)
        try {
            db.rawQuery("SELECT id, szerzo, cim FROM konyvek", null).use { c ->
                while (c.moveToNext()) {
                    out.add(
                        BookBrief(
                            id = c.getLong(0),
                            szerzo = c.getString(1) ?: "",
                            cim = c.getString(2) ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Több könyv (szerző, cím) párja egyszerre — kötegelt IN lekérdezés. */
    fun briefsByIds(ids: Collection<Long>): HashMap<Long, Pair<String, String>> {
        val out = HashMap<Long, Pair<String, String>>(ids.size * 2)
        if (ids.isEmpty()) return out
        try {
            val list = ids.toList()
            var i = 0
            while (i < list.size) {
                val chunk = list.subList(i, minOf(i + 500, list.size))
                val placeholders = chunk.joinToString(",") { "?" }
                db.rawQuery(
                    "SELECT id, szerzo, cim FROM konyvek WHERE id IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                ).use { c ->
                    while (c.moveToNext()) {
                        out[c.getLong(0)] = Pair(c.getString(1) ?: "", c.getString(2) ?: "")
                    }
                }
                i += 500
            }
        } catch (_: Exception) {
        }
        return out
    }
}

/**
 * Folyamat-szintű katalógus-kezelő: egyetlen megnyitott példány + a fájlnév-index
 * gyorsítótára, hogy a mappaböngészés közbeni párosítás olcsó legyen.
 */
object CatalogHolder {

    @Volatile
    private var catalog: CatalogDb? = null

    @Volatile
    private var fileNameIndex: HashMap<String, Long>? = null

    @Volatile
    private var normNameIndex: HashMap<String, Long>? = null

    @Synchronized
    fun get(context: Context): CatalogDb? {
        val current = catalog
        if (current != null) return current
        val path = Prefs.dbPath(context) ?: return null
        val opened = CatalogDb.open(path)
        catalog = opened
        return opened
    }

    @Synchronized
    fun reopen(context: Context, path: String): CatalogDb? {
        catalog?.close()
        catalog = null
        fileNameIndex = null
        normNameIndex = null
        Prefs.setDbPath(context, path)
        val opened = CatalogDb.open(path)
        catalog = opened
        return opened
    }

    @Synchronized
    fun closeCurrent() {
        catalog?.close()
        catalog = null
        fileNameIndex = null
        normNameIndex = null
    }

    /** Kisbetűs fájlnév -> konyv_id index (lustán, egyszer betöltve). */
    fun nameIndex(context: Context): HashMap<String, Long>? {
        fileNameIndex?.let { return it }
        val cat = get(context) ?: return null
        synchronized(this) {
            fileNameIndex?.let { return it }
            val idx = cat.loadFileNameIndex()
            fileNameIndex = idx
            val norm = HashMap<String, Long>(idx.size * 2)
            for ((name, id) in idx) {
                val base = name.substringBeforeLast('.')
                val n = Normalizer.norm(base)
                if (n.isNotEmpty()) norm[n] = id
            }
            normNameIndex = norm
            return idx
        }
    }

    /** Normalizált (ékezet/írásjel nélküli) fájlnév-index. */
    fun normIndex(context: Context): HashMap<String, Long>? {
        normNameIndex?.let { return it }
        nameIndex(context)
        return normNameIndex
    }
}
