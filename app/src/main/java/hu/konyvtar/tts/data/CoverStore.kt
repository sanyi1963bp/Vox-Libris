package hu.konyvtar.tts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * A kinyert borítók tára.
 *
 * A képek kicsinyítve, WebP-ben kerülnek az app saját mappájába (kb. 20 KB
 * darabja), így háromezer könyv borítója is elfér néhány tíz megában. A
 * mappát az app törlésekor a rendszer takarítja el — a katalógustól
 * eltérően ez nem érték, bármikor újra kinyerhető a könyvekből.
 *
 * A már betöltött képeket memóriában is tartjuk, hogy a görgetés ne
 * akadjon meg minden soron a lemezolvasáson.
 */
object CoverStore {

    /** Ekkorára kicsinyítünk: elég egy nagy borítónak is, de kicsi marad. */
    const val MAX_W = 320
    const val MAX_H = 480

    private const val QUALITY = 80

    /** Kb. ennyi memóriát adunk a bélyegképeknek. */
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Amiről tudjuk, hogy nincs borítója — ne próbáljuk minden görgetésnél. */
    private val known = HashSet<String>()

    fun dir(context: Context): File {
        val d = File(context.filesDir, "covers")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** A fájl útvonalából képzett, stabil név — ékezet és hossz nem számít. */
    fun keyOf(path: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun fileFor(context: Context, path: String): File = File(dir(context), keyOf(path) + ".webp")

    fun has(context: Context, path: String): Boolean = fileFor(context, path).exists()

    /** Elmentett borító betöltése; memóriából, ha már láttuk. */
    fun load(context: Context, path: String): Bitmap? {
        val key = keyOf(path)
        memory.get(key)?.let { return it }
        if (key in known) return null
        val f = File(dir(context), "$key.webp")
        if (!f.exists()) {
            synchronized(known) { known.add(key) }
            return null
        }
        val bmp = try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
        if (bmp != null) memory.put(key, bmp)
        return bmp
    }

    /** Kinyert borító mentése. Csak akkor ír, ha tényleg sikerült a kép. */
    fun save(context: Context, path: String, bitmap: Bitmap): Boolean {
        return try {
            val key = keyOf(path)
            val f = File(dir(context), "$key.webp")
            FileOutputStream(f).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
            }
            synchronized(known) { known.remove(key) }
            memory.put(key, bitmap)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun count(context: Context): Int = dir(context).listFiles()?.size ?: 0

    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        memory.evictAll()
        synchronized(known) { known.clear() }
    }
}
