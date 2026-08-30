package hu.konyvtar.tts.model

import hu.konyvtar.tts.R
import java.io.File

/** Egy sor a fájlböngészőben: fájl vagy mappa + a hozzá párosított katalógus-metaadat. */
data class FileRow(
    val path: String,
    val name: String,
    val ext: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val konyvId: Long? = null,
    val cim: String? = null,
    val szerzo: String? = null
)

/** Teljes könyvrekord a ncore_konyvtar.db `konyvek` táblájából. */
data class CatalogBook(
    val id: Long,
    val ncoreId: String?,
    val szerzo: String?,
    val cim: String?,
    val kiado: String?,
    val kiadasEve: String?,
    val isbn: String?,
    val sorozat: String?,
    val sorozatSzama: String?,
    val cimkek: String?,
    val leiras: String?,
    val formatum: String?,
    val meret: String?,
    val feltoltveDatum: String?
)

/** Egy fájlhoz tartozó rövid katalógus-adat (lista-megjelenítéshez). */
data class FileMeta(
    val konyvId: Long?,
    val cim: String?,
    val szerzo: String?
)

/**
 * Egy könyv a könyvtárban: egy mű és egy hozzá tartozó fájl.
 *
 * A `key*` mezők a kereséshez és a rendezéshez előre elkészített, kisbetűs,
 * ékezet nélküli változatok. Egyszer, a betöltéskor készülnek el, így a
 * gépelés közbeni szűrés több ezer könyvnél is azonnali marad.
 */
data class ShelfBook(
    val id: Long,
    val title: String,
    val author: String,
    val format: String,
    val path: String,
    /** A fájl kiterjesztése kisbetűvel (epub, pdf…). */
    val ext: String = "",
    val keyTitle: String = "",
    val keyAuthor: String = "",
    val keyFile: String = "",
    /** A cím és a szerző kezdőbetűje a betűsávhoz (ékezet nélkül). */
    val letterTitle: String = "#",
    val letterAuthor: String = "#"
) {
    /** A rendezéshez tartozó kezdőbetű. */
    fun letterFor(byAuthor: Boolean): String = if (byAuthor) letterAuthor else letterTitle

    /** A fájl neve — a listában és az adatlapon látszik. */
    val fileName: String get() = path.substringAfterLast('/')

    /** Illeszkedik-e a (már normalizált) keresőkifejezésre. */
    fun matches(q: String): Boolean =
        keyTitle.contains(q) || keyAuthor.contains(q) || keyFile.contains(q)
}

/** A könyv megnyitásához használt sor. */
fun ShelfBook.toFileRow(): FileRow {
    val f = File(path)
    return FileRow(
        path = path,
        name = f.name,
        ext = if (ext.isNotEmpty()) ext else f.extension.lowercase(),
        isDir = false,
        size = f.length(),
        mtime = f.lastModified(),
        konyvId = id,
        cim = title,
        szerzo = author
    )
}

/** Olvasási/hallgatási állapot egy fájlhoz. */
data class ProgressRow(
    val path: String,
    val konyvId: Long?,
    val paraIndex: Int,
    val totalParas: Int,
    val percent: Double,
    val listenedMs: Long,
    val lastAccess: Long,
    val title: String,
    val author: String,
    /** A képernyős olvasó pozíciója — külön a TTS pozíciótól. */
    val readPara: Int = 0,
    /** A TTS aktuális mondatának kezdő karaktere a bekezdésen belül. */
    val paraChar: Int = 0
)

/** E fölött számít a könyv elolvasottnak. */
const val FINISHED_PERCENT = 98.0

/** A könyv tényleges haladása: a felolvasás és a képernyős olvasás közül a nagyobb. */
fun ProgressRow.displayPercent(): Double {
    val readPct = if (totalParas > 0) readPara * 100.0 / totalParas else 0.0
    return maxOf(percent, readPct)
}

/** Az olvasottsági állapot a felhasználó nyelvén. */
fun ProgressRow.statusText(context: android.content.Context): String = context.getString(
    if (displayPercent() >= FINISHED_PERCENT) R.string.status_finished
    else R.string.status_in_progress
)

/** Könyvjelző egy adott bekezdésnél. */
data class Bookmark(
    val id: Long,
    val path: String,
    val paraIndex: Int,
    val snippet: String,
    val title: String,
    val author: String,
    val created: Long
)

enum class SortKey { NAME, SIZE, DATE, TITLE, AUTHOR, FORMAT }
