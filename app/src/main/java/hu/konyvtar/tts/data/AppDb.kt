package hu.konyvtar.tts.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import hu.konyvtar.tts.model.Bookmark
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.ProgressRow
import hu.konyvtar.tts.model.SortKey

/**
 * Az app saját, kicsi adatbázisa: szkennelési gyorsítótár + olvasási pozíciók.
 * Szándékosan KÜLÖN van a 152 MB-os katalógustól, így a katalógus frissítése
 * (újramásolása a PC-ről) soha nem törli a felolvasási pozíciókat.
 */
object AppDb {

    private const val DB_NAME = "app_local.db"
    private const val DB_VERSION = 3

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
                CREATE TABLE scan_cache (
                    path     TEXT PRIMARY KEY,
                    dir      TEXT NOT NULL,
                    name     TEXT NOT NULL,
                    ext      TEXT NOT NULL,
                    size     INTEGER NOT NULL,
                    mtime    INTEGER NOT NULL,
                    konyv_id INTEGER,
                    cim      TEXT,
                    szerzo   TEXT,
                    mode     TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_sc_dir ON scan_cache(dir)")
            db.execSQL("CREATE INDEX idx_sc_name ON scan_cache(name COLLATE NOCASE)")
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
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE progress ADD COLUMN read_para INTEGER NOT NULL DEFAULT 0")
                createBookmarks(db)
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE progress ADD COLUMN para_char INTEGER NOT NULL DEFAULT 0")
            }
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

    // ---------------------------------------------------------------- scan_cache

    /** Egy mappa gyorsítótárazott sorai, útvonal szerint kulcsolva. */
    fun cachedForDir(dir: String): HashMap<String, FileRow> {
        val out = HashMap<String, FileRow>()
        db().rawQuery(
            "SELECT path, dir, name, ext, size, mtime, konyv_id, cim, szerzo, mode FROM scan_cache WHERE dir = ?",
            arrayOf(dir)
        ).use { c ->
            while (c.moveToNext()) {
                val row = FileRow(
                    path = c.getString(0),
                    name = c.getString(2),
                    ext = c.getString(3),
                    isDir = false,
                    size = c.getLong(4),
                    mtime = c.getLong(5),
                    konyvId = if (c.isNull(6)) null else c.getLong(6),
                    cim = c.getString(7),
                    szerzo = c.getString(8),
                    matchMode = c.getString(9)
                )
                out[row.path] = row
            }
        }
        return out
    }

    /** Egyetlen fájl gyorsítótárazott sora. */
    fun cachedForPath(path: String): FileRow? {
        db().rawQuery(
            "SELECT path, dir, name, ext, size, mtime, konyv_id, cim, szerzo, mode FROM scan_cache WHERE path = ?",
            arrayOf(path)
        ).use { c ->
            if (c.moveToNext()) {
                return FileRow(
                    path = c.getString(0),
                    name = c.getString(2),
                    ext = c.getString(3),
                    isDir = false,
                    size = c.getLong(4),
                    mtime = c.getLong(5),
                    konyvId = if (c.isNull(6)) null else c.getLong(6),
                    cim = c.getString(7),
                    szerzo = c.getString(8),
                    matchMode = c.getString(9)
                )
            }
        }
        return null
    }

    fun upsertScanRows(rows: List<FileRow>) {
        if (rows.isEmpty()) return
        val d = db()
        d.beginTransaction()
        try {
            for (r in rows) {
                val cv = ContentValues().apply {
                    put("path", r.path)
                    put("dir", r.path.substringBeforeLast('/', ""))
                    put("name", r.name)
                    put("ext", r.ext)
                    put("size", r.size)
                    put("mtime", r.mtime)
                    if (r.konyvId != null) put("konyv_id", r.konyvId) else putNull("konyv_id")
                    put("cim", r.cim)
                    put("szerzo", r.szerzo)
                    put("mode", r.matchMode)
                }
                d.insertWithOnConflict("scan_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    fun scanCacheCount(): Int {
        db().rawQuery("SELECT COUNT(*) FROM scan_cache", null).use { c ->
            return if (c.moveToNext()) c.getInt(0) else 0
        }
    }

    fun scanCacheMatchedCount(): Int {
        db().rawQuery("SELECT COUNT(*) FROM scan_cache WHERE konyv_id IS NOT NULL", null).use { c ->
            return if (c.moveToNext()) c.getInt(0) else 0
        }
    }

    fun clearScanCache() {
        db().execSQL("DELETE FROM scan_cache")
    }

    /**
     * A teljes szkennelt lista (lapos "Katalógus" nézet), szűréssel és rendezéssel.
     * SQL-ben rendezünk/szűrünk — 50-70 ezer sornál is gyors.
     */
    fun allScanned(query: String, sortKey: SortKey, asc: Boolean, onlyMatched: Boolean): List<FileRow> {
        val dir = if (asc) "ASC" else "DESC"
        val order = when (sortKey) {
            SortKey.NAME -> "name COLLATE NOCASE $dir"
            SortKey.SIZE -> "size $dir"
            SortKey.DATE -> "mtime $dir"
            SortKey.TITLE -> "(cim IS NULL), cim COLLATE NOCASE $dir"
            SortKey.AUTHOR -> "(szerzo IS NULL), szerzo COLLATE NOCASE $dir"
        }
        val where = StringBuilder("1=1")
        val args = ArrayList<String>()
        if (query.isNotBlank()) {
            where.append(" AND (name LIKE ? OR cim LIKE ? OR szerzo LIKE ?)")
            val q = "%${query.trim()}%"
            args.add(q); args.add(q); args.add(q)
        }
        if (onlyMatched) {
            where.append(" AND konyv_id IS NOT NULL")
        }
        val out = ArrayList<FileRow>()
        db().rawQuery(
            "SELECT path, dir, name, ext, size, mtime, konyv_id, cim, szerzo, mode FROM scan_cache WHERE $where ORDER BY $order",
            args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    FileRow(
                        path = c.getString(0),
                        name = c.getString(2),
                        ext = c.getString(3),
                        isDir = false,
                        size = c.getLong(4),
                        mtime = c.getLong(5),
                        konyvId = if (c.isNull(6)) null else c.getLong(6),
                        cim = c.getString(7),
                        szerzo = c.getString(8),
                        matchMode = c.getString(9)
                    )
                )
            }
        }
        return out
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
