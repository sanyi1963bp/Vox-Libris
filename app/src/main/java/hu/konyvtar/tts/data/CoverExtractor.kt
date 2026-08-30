package hu.konyvtar.tts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import hu.konyvtar.tts.reader.EpubParser
import hu.konyvtar.tts.reader.XmlReader
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/**
 * Borítókép kinyerése a könyvfájlból.
 *
 * Minden formátumnak megvan a maga rejtekhelye: az EPUB az OPF-ben jelöli
 * meg a borítót, a MOBI egy EXTH-rekordban tárolja a kép rekordszámát, az
 * FB2 base64-ben ágyazza be, a PDF-nél pedig az első oldalt rajzoljuk ki.
 * Ahol nincs borító, ott `null` jön vissza — olyankor marad a címből és a
 * szerzőből rajzolt helyettesítő.
 */
object CoverExtractor {

    /** Ezek a formátumok hordozhatnak borítót. */
    val SUPPORTED = setOf("epub", "mobi", "prc", "azw", "azw3", "fb2", "pdf")

    fun canHaveCover(ext: String): Boolean = ext.lowercase() in SUPPORTED

    /** A kinyert borító, már kicsinyítve. Hiba esetén null, kivétel nélkül. */
    fun extract(context: Context, file: File, maxW: Int, maxH: Int): Bitmap? {
        return try {
            when (file.extension.lowercase()) {
                "epub" -> fromEpub(file)?.let { decode(it, maxW, maxH) }
                "mobi", "prc", "azw", "azw3" -> fromMobi(file)?.let { decode(it, maxW, maxH) }
                "fb2" -> fromFb2(file)?.let { decode(it, maxW, maxH) }
                "pdf" -> fromPdf(file, maxW, maxH)
                else -> null
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Serult vagy csonka fajlnal ne kivetel legyen, hanem „nincs borito". */
    private inline fun safely(block: () -> ByteArray?): ByteArray? = try {
        block()
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    // ---------------------------------------------------------------- EPUB

    /**
     * Az OPF háromféleképpen jelölheti a borítót; mindhármat megnézzük,
     * mert a valódi könyvek mindhárom szokást követik:
     *  - `<meta name="cover" content="azonosító">` (EPUB 2),
     *  - `properties="cover-image"` a manifest tételén (EPUB 3),
     *  - végső esetben egy „cover" nevű képfájl a manifestben.
     */
    internal fun fromEpub(file: File): ByteArray? = safely {
        ZipFile(file).use { zip ->
            val opfPath = EpubParser.findOpfPath(zip) ?: return@safely null
            val opf = zip.getEntry(opfPath) ?: return@safely null
            val opfDir = opfPath.substringBeforeLast('/', "")

            var metaCoverId: String? = null
            var propsHref: String? = null
            var guessHref: String? = null
            val hrefById = HashMap<String, String>()

            zip.getInputStream(opf).use { input ->
                val parser = XmlReader.newParser()
                parser.setInput(input, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name.substringAfterLast(':')) {
                            "meta" -> {
                                var name: String? = null
                                var content: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    when (parser.getAttributeName(i).substringAfterLast(':')) {
                                        "name" -> name = parser.getAttributeValue(i)
                                        "content" -> content = parser.getAttributeValue(i)
                                    }
                                }
                                if (name.equals("cover", true) && !content.isNullOrBlank()) {
                                    metaCoverId = content
                                }
                            }
                            "item" -> {
                                var id: String? = null
                                var href: String? = null
                                var media: String? = null
                                var props: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    when (parser.getAttributeName(i).substringAfterLast(':')) {
                                        "id" -> id = parser.getAttributeValue(i)
                                        "href" -> href = parser.getAttributeValue(i)
                                        "media-type" -> media = parser.getAttributeValue(i)
                                        "properties" -> props = parser.getAttributeValue(i)
                                    }
                                }
                                if (id != null && href != null) hrefById[id] = href
                                val isImage = media?.startsWith("image/") == true
                                if (isImage && props?.contains("cover-image") == true) {
                                    propsHref = href
                                }
                                if (isImage && guessHref == null && href != null &&
                                    href.substringAfterLast('/').contains("cover", true)
                                ) {
                                    guessHref = href
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            val href = propsHref
                ?: metaCoverId?.let { hrefById[it] }
                ?: guessHref
                ?: return@safely null
            val entryName = resolve(opfDir, href)
            val entry = zip.getEntry(entryName) ?: zip.getEntry(href) ?: return@safely null
            zip.getInputStream(entry).use { it.readBytes() }
        }
    }

    /** Zipen belüli relatív hivatkozás feloldása, a `../` lépésekkel együtt. */
    internal fun resolve(dir: String, href: String): String {
        val clean = href.substringBefore('#').replace("%20", " ")
        if (dir.isEmpty()) return clean
        val parts = ArrayList<String>(dir.split('/'))
        for (piece in clean.split('/')) {
            when (piece) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(piece)
            }
        }
        return parts.joinToString("/")
    }

    // ---------------------------------------------------------------- MOBI

    /**
     * A MOBI a képeket külön PDB-rekordokban tárolja. Az EXTH 201-es rekord
     * mondja meg, hányadik képrekord a borító — a képek pedig a fejlécben
     * megadott „első kép" indextől kezdődnek. Ha nincs 201-es, a 202-es
     * (bélyegkép) is megteszi.
     */
    internal fun fromMobi(file: File): ByteArray? = safely {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 132) return@safely null
            val head = ByteArray(78)
            raf.readFully(head)
            if (String(head, 60, 8, Charsets.US_ASCII) != "BOOKMOBI") return@safely null
            val numRecords = u16(head, 76)
            if (numRecords < 2) return@safely null

            val table = ByteArray(numRecords * 8)
            raf.readFully(table)
            val offsets = IntArray(numRecords) { u32(table, it * 8).toInt() }
            val fileLen = raf.length().toInt()

            val r0 = readRange(raf, offsets[0], offsets[1], fileLen) ?: return@safely null
            if (r0.size < 132 || String(r0, 16, 4, Charsets.US_ASCII) != "MOBI") return@safely null

            val firstImage = u32(r0, 108).toInt()
            if (firstImage <= 0 || firstImage >= numRecords) return@safely null

            val coverIdx = exthInt(r0, 201) ?: exthInt(r0, 202) ?: return@safely null
            val rec = firstImage + coverIdx
            if (rec < 0 || rec >= numRecords) return@safely null

            val end = if (rec + 1 < numRecords) offsets[rec + 1] else fileLen
            readRange(raf, offsets[rec], end, fileLen)
        }
    }

    private fun readRange(raf: RandomAccessFile, from: Int, to: Int, fileLen: Int): ByteArray? {
        if (from < 0 || to > fileLen || from >= to || to - from > 16 * 1024 * 1024) return null
        val bytes = ByteArray(to - from)
        raf.seek(from.toLong())
        raf.readFully(bytes)
        return bytes
    }

    /** Egy 4 bájtos EXTH-rekord értéke, ha van ilyen típusú. */
    private fun exthInt(r0: ByteArray, type: Int): Int? {
        val headerLen = u32(r0, 20).toInt()
        val exthStart = 16 + headerLen
        if ((u32(r0, 128).toInt() and 0x40) == 0) return null
        if (exthStart + 12 > r0.size) return null
        if (String(r0, exthStart, 4, Charsets.US_ASCII) != "EXTH") return null
        val count = u32(r0, exthStart + 8).toInt()
        var pos = exthStart + 12
        var i = 0
        while (i < count && pos + 8 <= r0.size) {
            val recType = u32(r0, pos).toInt()
            val recLen = u32(r0, pos + 4).toInt()
            if (recLen < 8 || pos + recLen > r0.size) return null
            if (recType == type && recLen == 12) {
                val v = u32(r0, pos + 8).toInt()
                // 0xFFFFFFFF: kifejezetten azt jelenti, hogy nincs borító
                return if (v == -1) null else v
            }
            pos += recLen
            i++
        }
        return null
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    // ---------------------------------------------------------------- FB2

    /**
     * Az FB2 base64-ben ágyazza be a képeket. A `<coverpage>` hivatkozik az
     * azonosítójukra; ha nincs coverpage, az első beágyazott kép is jó.
     */
    internal fun fromFb2(file: File): ByteArray? = safely {
        if (file.length() > 64L * 1024 * 1024) return@safely null
        file.inputStream().use { input ->
            val parser = XmlReader.newParser()
            parser.setInput(input, null)
            var coverId: String? = null
            var inCoverPage = false
            var event = parser.eventType
            var firstImage: ByteArray? = null

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.substringAfterLast(':')) {
                            "coverpage" -> inCoverPage = true
                            "image" -> if (inCoverPage && coverId == null) {
                                for (i in 0 until parser.attributeCount) {
                                    if (parser.getAttributeName(i).substringAfterLast(':') == "href") {
                                        coverId = parser.getAttributeValue(i).removePrefix("#")
                                    }
                                }
                            }
                            "binary" -> {
                                var id: String? = null
                                var type: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    when (parser.getAttributeName(i).substringAfterLast(':')) {
                                        "id" -> id = parser.getAttributeValue(i)
                                        "content-type" -> type = parser.getAttributeValue(i)
                                    }
                                }
                                if (type?.startsWith("image/") == true) {
                                    val data = parser.nextText()
                                    val bytes = try {
                                        java.util.Base64.getMimeDecoder().decode(data)
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (bytes != null) {
                                        if (id != null && id == coverId) return@safely bytes
                                        if (firstImage == null) firstImage = bytes
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG ->
                        if (parser.name.substringAfterLast(':') == "coverpage") inCoverPage = false
                }
                event = parser.next()
            }
            firstImage
        }
    }

    // ---------------------------------------------------------------- PDF

    /** A PDF-nél nincs külön borító: az első oldalt rajzoljuk ki képként. */
    private fun fromPdf(file: File, maxW: Int, maxH: Int): Bitmap? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount < 1) return null
                renderer.openPage(0).use { page ->
                    val scale = minOf(
                        maxW.toFloat() / page.width, maxH.toFloat() / page.height
                    ).coerceAtMost(4f)
                    val w = (page.width * scale).toInt().coerceIn(1, maxW)
                    val h = (page.height * scale).toInt().coerceIn(1, maxH)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    // A PDF oldalak átlátszó háttérrel jönnek: fehérre kell rajzolni
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            }
        }
    }

    // ---------------------------------------------------------------- kép

    /** Dekódolás kicsinyítve, hogy a nagy borítók se egyék meg a memóriát. */
    private fun decode(bytes: ByteArray, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

        if (bmp.width <= maxW && bmp.height <= maxH) return bmp
        val scale = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
        val w = (bmp.width * scale).toInt().coerceAtLeast(1)
        val h = (bmp.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }
}
