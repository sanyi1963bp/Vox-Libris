package hu.konyvtar.tts.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import hu.konyvtar.tts.model.Bookmark
import hu.konyvtar.tts.model.ProgressRow
import hu.konyvtar.tts.model.displayPercent

/**
 * Az app saját, kicsi adatbázisa: szkennelési gyorsítótár + olvasási pozíciók.
 * Szándékosan KÜLÖN van a 152 MB-os katalógustól, így a katalógus frissítése
 * (újramásolása a PC-ről) soha nem törli a felolvasási pozíciókat.
 */
object AppDb {

    private const val DB_NAME = "app_local.db"
    private const val DB_VERSION = 6

    @Volatile
    private var helper: Helper? = null

    fun init(context: Context) {
        if (helper == null) {
            synchronized(this) {
                if (helper == null) {
                    helper = Helper(context.applicationContext)
                }
            }
        }
    }

    private fun db(): SQLiteDatabase = helper!!.writableDatabase

    private class Helper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE progress (
                    path        TEXT PRIMARY KEY,
                    konyv_id    INTEGER,
                    para_index  INTEGER NOT NULL DEFAULT 0,
                    total_paras INTEGER NOT NULL DEFAULT 0,
                    percent     REAL NOT NULL DEFAULT 0,
                    listened_ms INTEGER NOT NULL DEFAULT 0,
                    last_access INTEGER NOT NULL DEFAULT 0,
                    title       TEXT NOT NULL DEFAULT '',
                    author      TEXT NOT NULL DEFAULT '',
                    read_para   INTEGER NOT NULL DEFAULT 0,
                    para_char   INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            createBookmarks(db)
            createNotes(db)
            createPronounce(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE progress ADD COLUMN read_para INTEGER NOT NULL DEFAULT 0")
                createBookmarks(db)
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE progress ADD COLUMN para_char INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 4) {
                // A szkennelési gyorsítótár megszűnt: a katalógus az egyetlen forrás
                db.execSQL("DROP TABLE IF EXISTS scan_cache")
            }
            if (oldVersion < 5) {
                createNotes(db)
            }
            if (oldVersion < 6) {
                createPronounce(db)
            }
        }

        /**
         * Kiejtési szótár. Szándékosan globális, nem könyvhöz kötött: a
         * félremondott nevek többnyire sorozaton át és több fájlban is
         * visszatérnek, így egyszer kell megadni őket.
         */
        private fun createPronounce(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pronounce (
                    id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    pattern TEXT NOT NULL,
                    say_as  TEXT NOT NULL,
                    created INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_pron_pattern " +
                    "ON pronounce(pattern COLLATE NOCASE)"
            )
        }

        /** Saját jegyzet a könyvhöz — a fájl útvonalához kötve. */
        private fun createNotes(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    path    TEXT PRIMARY KEY,
                    note    TEXT NOT NULL DEFAULT '',
                    updated INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        private fun createBookmarks(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    path       TEXT NOT NULL,
                    para_index INTEGER NOT NULL,
                    snippet    TEXT NOT NULL DEFAULT '',
                    title      TEXT NOT NULL DEFAULT '',
                    author     TEXT NOT NULL DEFAULT '',
                    created    INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bm_path ON bookmarks(path)")
        }
    }

    // ---------------------------------------------------------------- progress

    private fun rowFromCursor(c: android.database.Cursor): ProgressRow = ProgressRow(
        path = c.getString(0),
        konyvId = if (c.isNull(1)) null else c.getLong(1),
        paraIndex = c.getInt(2),
        totalParas = c.getInt(3),
        percent = c.getDouble(4),
        listenedMs = c.getLong(5),
        lastAccess = c.getLong(6),
        title = c.getString(7),
        author = c.getString(8),
        readPara = c.getInt(9),
        paraChar = c.getInt(10)
    )

    private const val PROGRESS_COLS =
        "path, konyv_id, para_index, total_paras, percent, listened_ms, last_access, title, author, read_para, para_char"

    fun progressFor(path: String): ProgressRow? {
        db().rawQuery(
            "SELECT $PROGRESS_COLS FROM progress WHERE path = ?",
            arrayOf(path)
        ).use { c ->
            if (c.moveToNext()) return rowFromCursor(c)
        }
        return null
    }

    /** A TTS haladásának mentése — a képernyős olvasó pozícióját (read_para) NEM bántja. */
    fun upsertProgress(row: ProgressRow) {
        db().execSQL(
            """
            INSERT INTO progress(path, konyv_id, para_index, total_paras, percent, listened_ms, last_access, title, author, read_para, para_char)
            VALUES(?,?,?,?,?,?,?,?,?,0,?)
            ON CONFLICT(path) DO UPDATE SET
                konyv_id=excluded.konyv_id,
                para_index=excluded.para_index,
                total_paras=excluded.total_paras,
                percent=excluded.percent,
                listened_ms=excluded.listened_ms,
                last_access=excluded.last_access,
                title=excluded.title,
                author=excluded.author,
                para_char=excluded.para_char
            """.trimIndent(),
            arrayOf<Any?>(
                row.path, row.konyvId, row.paraIndex, row.totalParas, row.percent,
                row.listenedMs, row.lastAccess, row.title, row.author, row.paraChar
            )
        )
    }

    /** A képernyős olvasó pozíciójának mentése — a TTS mezőit NEM bántja. */
    fun setReadPara(path: String, readPara: Int, totalParas: Int, title: String, author: String) {
        db().execSQL(
            """
            INSERT INTO progress(path, konyv_id, para_index, total_paras, percent, listened_ms, last_access, title, author, read_para, para_char)
            VALUES(?,NULL,0,?,0,0,?,?,?,?,0)
            ON CONFLICT(path) DO UPDATE SET
                read_para=excluded.read_para,
                last_access=excluded.last_access,
                total_paras=CASE WHEN progress.total_paras=0 THEN excluded.total_paras ELSE progress.total_paras END
            """.trimIndent(),
            arrayOf<Any?>(path, totalParas, System.currentTimeMillis(), title, author, readPara)
        )
    }

    // ---------------------------------------------------------------- jegyzetek

    /** A könyvhöz fűzött saját jegyzet, ha van. */
    fun noteFor(path: String): String? = try {
        db().rawQuery("SELECT note FROM notes WHERE path = ?", arrayOf(path)).use {
            if (it.moveToNext()) it.getString(0)?.takeIf { s -> s.isNotBlank() } else null
        }
    } catch (e: Exception) {
        null
    }

    /** Üres szöveg törli a jegyzetet. */
    fun setNote(path: String, text: String) {
        try {
            if (text.isBlank()) {
                db().delete("notes", "path = ?", arrayOf(path))
                return
            }
            db().execSQL(
                "INSERT INTO notes(path, note, updated) VALUES(?, ?, ?) " +
                    "ON CONFLICT(path) DO UPDATE SET note = excluded.note, " +
                    "updated = excluded.updated",
                arrayOf<Any>(path, text.trim(), System.currentTimeMillis())
            )
        } catch (_: Exception) {
        }
    }

    /** Útvonal -> jegyzet; a listához, ahol csak a jelenlétük számít. */
    fun notesByPath(): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            db().rawQuery("SELECT path, note FROM notes", null).use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1)
                    if (!n.isNullOrBlank()) out[c.getString(0)] = n
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    // ---------------------------------------------------------------- kiejtés

    /** A kiejtési szabályok, mintával betűrendben. */
    fun pronounceRules(): List<Pronounce.Rule> {
        val out = ArrayList<Pronounce.Rule>()
        try {
            db().rawQuery(
                "SELECT id, pattern, say_as FROM pronounce ORDER BY pattern COLLATE NOCASE",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    out.add(Pronounce.Rule(c.getLong(0), c.getString(1), c.getString(2)))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /**
     * Szabály felvétele vagy felülírása. Ugyanarra a mintára két szabály
     * értelmetlen lenne, ezért a mintára egyedi index van: az újabb felülírja
     * a régit.
     */
    fun setPronounce(pattern: String, sayAs: String) {
        val p = pattern.trim()
        val s = sayAs.trim()
        if (p.isEmpty() || s.isEmpty()) return
        try {
            db().execSQL(
                "INSERT INTO pronounce(pattern, say_as, created) VALUES(?, ?, ?) " +
                    "ON CONFLICT(pattern) DO UPDATE SET say_as = excluded.say_as, " +
                    "created = excluded.created",
                arrayOf<Any>(p, s, System.currentTimeMillis())
            )
        } catch (_: Exception) {
        }
    }

    fun deletePronounce(id: Long) {
        try {
            db().delete("pronounce", "id = ?", arrayOf(id.toString()))
        } catch (_: Exception) {
        }
    }

    /** Az adott mintához tartozó kiejtés, ha van (a szerkesztő tölti fel vele). */
    fun pronounceFor(pattern: String): String? = try {
        db().rawQuery(
            "SELECT say_as FROM pronounce WHERE pattern = ? COLLATE NOCASE",
            arrayOf(pattern.trim())
        ).use { if (it.moveToNext()) it.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- útvonalak

    /**
     * A fájl elmozdult vagy nevet kapott: minden hozzá kötött adat kövesse.
     * Enélkül egy átnevezés csendben elvinné az olvasási haladást, a
     * könyvjelzőket és a jegyzetet — ezért nem mindegy, hogy a műveletet az
     * appban vagy egy fájlkezelőben végzed el.
     */
    fun movePath(from: String, to: String) {
        try {
            db().beginTransaction()
            try {
                // A cél már létezhet (pl. korábbi bejegyzés): előbb szabaddá tesszük
                db().delete("progress", "path = ?", arrayOf(to))
                db().delete("notes", "path = ?", arrayOf(to))
                val v = ContentValues().apply { put("path", to) }
                db().update("progress", v, "path = ?", arrayOf(from))
                db().update("bookmarks", v, "path = ?", arrayOf(from))
                db().update("notes", v, "path = ?", arrayOf(from))
                db().setTransactionSuccessful()
            } finally {
                db().endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    /** A fájl megszűnt: minden hozzá kötött adat is menjen vele. */
    fun forgetPath(path: String) {
        try {
            db().delete("progress", "path = ?", arrayOf(path))
            db().delete("bookmarks", "path = ?", arrayOf(path))
            db().delete("notes", "path = ?", arrayOf(path))
        } catch (_: Exception) {
        }
    }

    /**
     * Útvonal -> olvasottság százalék. Mindkét képernyő-modell ezt használja,
     * hogy a haladás-csík mindenhol ugyanazt mutassa.
     */
    fun progressByPath(): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (p in allProgress()) out[p.path] = p.displayPercent()
        return out
    }

    fun allProgress(): List<ProgressRow> {
        val out = ArrayList<ProgressRow>()
        db().rawQuery(
            "SELECT $PROGRESS_COLS FROM progress ORDER BY last_access DESC",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(rowFromCursor(c))
        }
        return out
    }

    fun deleteProgress(path: String) {
        db().delete("progress", "path = ?", arrayOf(path))
    }

    fun lastListened(): ProgressRow? {
        db().rawQuery(
            "SELECT $PROGRESS_COLS FROM progress ORDER BY last_access DESC LIMIT 1",
            null
        ).use { c ->
            if (c.moveToNext()) return rowFromCursor(c)
        }
        return null
    }

    // ---------------------------------------------------------------- bookmarks

    fun addBookmark(path: String, paraIndex: Int, snippet: String, title: String, author: String) {
        val cv = ContentValues().apply {
            put("path", path)
            put("para_index", paraIndex)
            put("snippet", snippet)
            put("title", title)
            put("author", author)
            put("created", System.currentTimeMillis())
        }
        db().insert("bookmarks", null, cv)
    }

    fun bookmarksFor(path: String): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        db().rawQuery(
            "SELECT id, path, para_index, snippet, title, author, created FROM bookmarks WHERE path = ? ORDER BY para_index",
            arrayOf(path)
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Bookmark(
                        id = c.getLong(0),
                        path = c.getString(1),
                        paraIndex = c.getInt(2),
                        snippet = c.getString(3),
                        title = c.getString(4),
                        author = c.getString(5),
                        created = c.getLong(6)
                    )
                )
            }
        }
        return out
    }

    fun deleteBookmark(id: Long) {
        db().delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    /** Minden könyvjelző, könyv szerint csoportosítva (exportáláshoz). */
    fun allBookmarks(): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        db().rawQuery(
            "SELECT id, path, para_index, snippet, title, author, created FROM bookmarks ORDER BY title COLLATE NOCASE, para_index",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Bookmark(
                        id = c.getLong(0),
                        path = c.getString(1),
                        paraIndex = c.getInt(2),
                        snippet = c.getString(3),
                        title = c.getString(4),
                        author = c.getString(5),
                        created = c.getLong(6)
                    )
                )
            }
        }
        return out
    }

    /** Az app saját adatbázisának fájlja (exportáláshoz/mentéshez). */
    fun databaseFile(): java.io.File = java.io.File(helper!!.readableDatabase.path)
}
