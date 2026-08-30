package hu.konyvtar.tts.data

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A borítók betöltése — a beolvasás második menete.
 *
 * Szándékosan külön fut a metaadatoktól: azok pár perc alatt végigfutnak és
 * a könyvtár máris használható, a borítók pedig utána, a háttérben
 * töltődnek. Egy borító kinyerése sokkal drágább (kép dekódolása,
 * PDF-oldal kirajzolása), ezért nem várakoztatjuk vele a listát.
 *
 * Amit egyszer kinyertünk, azt nem próbáljuk újra: a következő futás csak
 * az újonnan bemásolt könyveket nézi meg.
 */
object CoverScanner {

    data class Progress(
        val running: Boolean = false,
        /** Hány könyvön ment végig eddig. */
        val checked: Int = 0,
        /** Összesen ennyi könyvet kell megnézni. */
        val total: Int = 0,
        /** Ennyihez sikerült borítót kinyerni ebben a futásban. */
        val found: Int = 0,
        val currentTitle: String = "",
        val done: Boolean = false,
        val cancelled: Boolean = false
    )

    val cancelFlag = AtomicBoolean(false)

    /**
     * Végigmegy a katalógus könyvein, és amelyiknek még nincs bélyegképe,
     * annak megpróbálja kinyerni. A haladást minden tizedik könyv után
     * jelenti, hogy a felület ne remegjen a folyamatos frissítéstől.
     */
    fun scan(context: Context, onProgress: (Progress) -> Unit): Progress {
        cancelFlag.set(false)
        val books = Catalog.allBooks()
            .filter { CoverExtractor.canHaveCover(it.ext) }
            .filter { !CoverStore.has(context, it.path) }

        var p = Progress(running = true, total = books.size)
        onProgress(p)

        var found = 0
        for ((i, book) in books.withIndex()) {
            if (cancelFlag.get()) {
                p = p.copy(running = false, cancelled = true, checked = i, found = found)
                onProgress(p)
                return p
            }
            val f = File(book.path)
            if (f.exists()) {
                val bmp = CoverExtractor.extract(context, f, CoverStore.MAX_W, CoverStore.MAX_H)
                if (bmp != null && CoverStore.save(context, book.path, bmp)) found++
            }
            if (i % 10 == 0 || i == books.size - 1) {
                p = p.copy(checked = i + 1, found = found, currentTitle = book.title)
                onProgress(p)
            }
        }

        p = Progress(
            running = false, checked = books.size, total = books.size,
            found = found, done = true
        )
        onProgress(p)
        return p
    }
}
