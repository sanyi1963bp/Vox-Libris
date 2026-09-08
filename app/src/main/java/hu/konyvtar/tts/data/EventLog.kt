package hu.konyvtar.tts.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kis eseménynapló a kívülről érkező vezérléshez.
 *
 * Azért van, mert a Bluetooth-gombokat nem lehet a szobában kipróbálni: a hiba
 * a kocsiban jelentkezik, ahol nincs se fejlesztői kábel, se konzol. Enélkül
 * csak találgatni tudnánk, hogy a gombnyomás egyáltalán megérkezik-e hozzánk.
 *
 * Szándékosan buta: egy sima szövegfájl az app saját mappájában. Nem megy
 * sehova, nem kér engedélyt, és a felhasználó bármikor kiürítheti.
 */
object EventLog {

    /** E fölött vágunk vissza, hogy a fájl ne híveljen fel a végtelenségig. */
    private const val MAX_LINES = 400
    private const val TRIM_AT = 600

    private const val FILE_NAME = "esemenynaplo.txt"

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val lock = Any()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /**
     * Egy sor a naplóba. Csendben elnyel minden hibát: a naplózás soha nem
     * ronthatja el azt, amit naplóz.
     */
    fun add(context: Context, event: String, detail: String = "") {
        try {
            synchronized(lock) {
                val f = file(context)
                val line = buildString {
                    append(stamp.format(Date()))
                    append("  ")
                    append(event)
                    if (detail.isNotEmpty()) {
                        append("  —  ")
                        append(detail)
                    }
                    append('\n')
                }
                f.appendText(line)
                trimIfNeeded(f)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Csak akkor írjuk újra a fájlt, ha jóval a határ fölé nőtt. Így a gyakori
     * esetben (néhány sor hozzáfűzése) nem olvassuk be az egészet.
     */
    private fun trimIfNeeded(f: File) {
        val lines = f.readLines()
        if (lines.size <= TRIM_AT) return
        f.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }

    /** A napló tartalma, legfrissebb elöl — így nem kell görgetni a végére. */
    fun read(context: Context): String = try {
        val f = file(context)
        if (!f.exists()) "" else f.readLines().asReversed().joinToString("\n")
    } catch (e: Exception) {
        ""
    }

    fun lineCount(context: Context): Int = try {
        val f = file(context)
        if (!f.exists()) 0 else f.readLines().count { it.isNotBlank() }
    } catch (e: Exception) {
        0
    }

    fun clear(context: Context) {
        try {
            synchronized(lock) { file(context).delete() }
        } catch (_: Exception) {
        }
    }

    /**
     * Kimásolás a Letöltések mappába, hogy kábelen át el lehessen hozni a
     * gépre. Ugyanoda megy, ahova az adatexport.
     */
    fun exportTo(context: Context, dir: File): File? = try {
        val src = file(context)
        if (!src.exists()) null else {
            val out = File(dir, "esemenynaplo_" + fileStamp.format(Date()) + ".txt")
            src.copyTo(out, overwrite = true)
            out
        }
    } catch (e: Exception) {
        null
    }

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
}
