package hu.konyvtar.tts.model

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
    val szerzo: String? = null,
    val matchMode: String? = null
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

/** Rövidített könyvadat a párosításhoz (memóriatakarékos). */
data class BookBrief(
    val id: Long,
    val szerzo: String,
    val cim: String
)

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

/** "Elolvasott" vagy "Folyamatban". */
fun ProgressRow.statusText(): String =
    if (displayPercent() >= FINISHED_PERCENT) "Elolvasott" else "Folyamatban"

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

/** Párosítási találat. */
data class MatchResult(
    val konyvId: Long,
    val mode: String
)

enum class SortKey { NAME, SIZE, DATE, TITLE, AUTHOR }
