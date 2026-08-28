package hu.konyvtar.tts.data

import android.content.Context
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import hu.konyvtar.tts.reader.EpubParser
import hu.konyvtar.tts.reader.HtmlText
import hu.konyvtar.tts.reader.TxtParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

/** Egy könyvfájlból kinyert metaadat. */
data class BookMeta(
    var title: String? = null,
    var author: String? = null,
    var publisher: String? = null,
    var year: String? = null,
    var isbn: String? = null,
    var series: String? = null,
    var seriesIndex: String? = null,
    var tags: String? = null,
    var description: String? = null,
    var language: String? = null,
    /** Honnan jött: epub, fb2, mobi, docx, rtf, pdf vagy fajlnev. */
    var source: String = "fajlnev"
) {
    /** Van-e benne bármi értékelhető a fájlnéven túl. */
    fun hasRealData(): Boolean = !title.isNullOrBlank() && source != "fajlnev"
}

/**
 * Metaadat kinyerése a könyvfájlok belsejéből — a teljes szöveg beolvasása
 * nélkül. Csak a fejlécet / a metaadat-blokkot olvassa, ezért gyors.
 *
 * Ha a fájlban nincs használható metaadat, a fájlnévből próbál címet és
 * szerzőt kinyerni.
 */
object MetadataExtractor {

    private const val MAX_DESC = 8000
    private const val MAX_FIELD = 500

    @Volatile
    private var pdfBoxReady = false

    /** A metaadat-kinyerésnél támogatott formátumok. */
    val SUPPORTED = setOf(
        "epub", "fb2", "mobi", "prc", "azw", "azw3", "docx", "rtf", "pdf",
        "txt", "htm", "html", "doc", "djvu"
    )

    fun extract(context: Context, file: File, includePdf: Boolean): BookMeta {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val meta = try {
            when (ext) {
                "epub" -> fromEpub(file)
                "fb2" -> fromFb2(file)
                "mobi", "prc", "azw", "azw3" -> fromMobi(file)
                "docx" -> fromDocx(file)
                "rtf" -> fromRtf(file)
                "pdf" -> if (includePdf) fromPdf(context, file) else null
                else -> null
            }
        } catch (e: Exception) {
            null
        } ?: BookMeta()

        // Ami hiányzik, azt a fájlnévből pótoljuk
        val fallback = fromFileName(file.name)
        if (meta.title.isNullOrBlank()) meta.title = fallback.first
        if (meta.author.isNullOrBlank()) meta.author = fallback.second
        if (meta.title.isNullOrBlank()) {
            meta.title = file.name.substringBeforeLast('.')
        }
        return meta
    }

    // ---------------------------------------------------------------- közös segédek

    private fun clean(s: String?, limit: Int = MAX_FIELD): String? {
        if (s == null) return null
        var t = Normalizer.stripInvisible(s).replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        if (t.length > limit) t = t.substring(0, limit).trim()
        return t
    }

    private fun cleanDescription(s: String?): String? {
        if (s.isNullOrBlank()) return null
        // A leírás gyakran HTML-t tartalmaz
        val text = if (s.contains('<')) {
            HtmlText.toParagraphs(s).joinToString("\n\n")
        } else {
            HtmlText.decodeEntities(s)
        }
        var t = Normalizer.stripInvisible(text)
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (t.isEmpty()) return null
        if (t.length > MAX_DESC) t = t.substring(0, MAX_DESC).trim() + "…"
        return t
    }

    private fun yearOf(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val m = Regex("(1[5-9]\\d{2}|20\\d{2})").find(s) ?: return null
        return m.value
    }

    private val uuidLike = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /**
     * ISBN felismerése. Az EPUB-ok azonosítói között UUID-k és belső kódok is
     * vannak, ezért csak a valódi ISBN-alakot fogadjuk el.
     */
    private fun isbnOf(s: String?): String? {
        if (s.isNullOrBlank()) return null
        val raw = s.trim()
        if (uuidLike.containsMatchIn(raw)) return null
        // Betűket tartalmazó azonosító (pl. calibre slug) nem ISBN
        val body = raw.substringAfterLast(':').trim()
        if (body.any { it.isLetter() && it != 'X' && it != 'x' }) return null
        val digits = body.replace(Regex("[^0-9Xx]"), "")
        if (digits.length == 13) {
            return if (digits.startsWith("978") || digits.startsWith("979")) digits else null
        }
        return if (digits.length == 10) digits.uppercase() else null
    }

    private fun localName(q: String): String = q.substringAfterLast(':')

    // ---------------------------------------------------------------- EPUB

    private fun fromEpub(file: File): BookMeta? {
        ZipFile(file).use { zip ->
            val opfPath = EpubParser.findOpfPath(zip) ?: return null
            val entry = zip.getEntry(opfPath) ?: return null
            val meta = BookMeta(source = "epub")
            val authors = ArrayList<String>()
            val subjects = ArrayList<String>()

            zip.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                var inMetadata = false
                var tag: String? = null
                var pendingProperty: String? = null
                var idScheme: String? = null
                val text = StringBuilder()

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val name = localName(parser.name)
                            if (name == "metadata") inMetadata = true
                            if (inMetadata) {
                                tag = name
                                text.setLength(0)
                                if (name == "meta") {
                                    // EPUB2: name/content attribútumpár
                                    var mName: String? = null
                                    var mContent: String? = null
                                    var mProperty: String? = null
                                    for (i in 0 until parser.attributeCount) {
                                        when (localName(parser.getAttributeName(i))) {
                                            "name" -> mName = parser.getAttributeValue(i)
                                            "content" -> mContent = parser.getAttributeValue(i)
                                            "property" -> mProperty = parser.getAttributeValue(i)
                                        }
                                    }
                                    when (mName) {
                                        "calibre:series" -> meta.series = clean(mContent)
                                        "calibre:series_index" -> meta.seriesIndex = clean(mContent)
                                    }
                                    // EPUB3: property + szöveges tartalom
                                    pendingProperty = mProperty
                                }
                                if (name == "identifier") {
                                    idScheme = null
                                    for (i in 0 until parser.attributeCount) {
                                        if (localName(parser.getAttributeName(i)) == "scheme") {
                                            idScheme = parser.getAttributeValue(i)
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> if (inMetadata) text.append(parser.text)
                        XmlPullParser.END_TAG -> {
                            val name = localName(parser.name)
                            if (name == "metadata") {
                                inMetadata = false
                            } else if (inMetadata && name == tag) {
                                val value = text.toString()
                                when (name) {
                                    "title" -> if (meta.title.isNullOrBlank()) meta.title = clean(value)
                                    "creator" -> clean(value)?.let { authors.add(it) }
                                    "publisher" -> if (meta.publisher.isNullOrBlank()) meta.publisher = clean(value)
                                    "date" -> if (meta.year.isNullOrBlank()) meta.year = yearOf(value)
                                    "description" -> if (meta.description.isNullOrBlank()) {
                                        meta.description = cleanDescription(value)
                                    }
                                    "subject" -> clean(value)?.let { subjects.add(it) }
                                    "language" -> if (meta.language.isNullOrBlank()) meta.language = clean(value, 12)
                                    "identifier" -> {
                                        val looksIsbn = idScheme?.contains("isbn", true) == true ||
                                            value.contains("isbn", true)
                                        val found = isbnOf(value)
                                        if (found != null && (looksIsbn || meta.isbn == null)) {
                                            meta.isbn = found
                                        }
                                        idScheme = null
                                    }
                                    "meta" -> {
                                        when (pendingProperty) {
                                            "belongs-to-collection" ->
                                                if (meta.series.isNullOrBlank()) meta.series = clean(value)
                                            "group-position" ->
                                                if (meta.seriesIndex.isNullOrBlank()) meta.seriesIndex = clean(value, 12)
                                            "dcterms:modified" -> {}
                                        }
                                        pendingProperty = null
                                    }
                                }
                                text.setLength(0)
                                tag = null
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            if (authors.isNotEmpty()) meta.author = authors.distinct().joinToString(", ").take(MAX_FIELD)
            if (subjects.isNotEmpty()) meta.tags = subjects.distinct().joinToString(", ").take(MAX_FIELD)
            return if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
        }
    }

    // ---------------------------------------------------------------- FB2

    private fun fromFb2(file: File): BookMeta? {
        val meta = BookMeta(source = "fb2")
        val genres = ArrayList<String>()
        var first: String? = null
        var middle: String? = null
        var last: String? = null
        val authors = ArrayList<String>()

        file.inputStream().use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var event = parser.eventType
            var inDescription = false
            var inTitleInfo = false
            var inPublishInfo = false
            var inAuthor = false
            var inAnnotation = false
            var tag: String? = null
            val text = StringBuilder()
            val annotation = StringBuilder()

            loop@ while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = localName(parser.name)
                        when (name) {
                            "description" -> inDescription = true
                            "title-info" -> if (inDescription) inTitleInfo = true
                            "publish-info" -> if (inDescription) inPublishInfo = true
                            "author" -> if (inTitleInfo) {
                                inAuthor = true
                                first = null; middle = null; last = null
                            }
                            "annotation" -> if (inTitleInfo) inAnnotation = true
                            "sequence" -> if (inTitleInfo) {
                                for (i in 0 until parser.attributeCount) {
                                    when (localName(parser.getAttributeName(i))) {
                                        "name" -> if (meta.series.isNullOrBlank())
                                            meta.series = clean(parser.getAttributeValue(i))
                                        "number" -> if (meta.seriesIndex.isNullOrBlank())
                                            meta.seriesIndex = clean(parser.getAttributeValue(i), 12)
                                    }
                                }
                            }
                        }
                        tag = name
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> if (inDescription) {
                        text.append(parser.text)
                        if (inAnnotation) annotation.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        val name = localName(parser.name)
                        val value = text.toString()
                        when (name) {
                            "description" -> {
                                inDescription = false
                                break@loop // a leíráson túl nem kell olvasnunk
                            }
                            "title-info" -> inTitleInfo = false
                            "publish-info" -> inPublishInfo = false
                            "annotation" -> {
                                if (inAnnotation) {
                                    inAnnotation = false
                                    if (meta.description.isNullOrBlank()) {
                                        meta.description = cleanDescription(annotation.toString())
                                    }
                                }
                            }
                            "author" -> if (inAuthor) {
                                inAuthor = false
                                val full = listOfNotNull(first, middle, last)
                                    .joinToString(" ").trim()
                                if (full.isNotEmpty()) authors.add(full)
                            }
                            "first-name" -> if (inAuthor) first = clean(value)
                            "middle-name" -> if (inAuthor) middle = clean(value)
                            "last-name" -> if (inAuthor) last = clean(value)
                            "book-title" -> if (inTitleInfo && meta.title.isNullOrBlank())
                                meta.title = clean(value)
                            "genre" -> if (inTitleInfo) clean(value)?.let { genres.add(it) }
                            "lang" -> if (inTitleInfo && meta.language.isNullOrBlank())
                                meta.language = clean(value, 12)
                            "date" -> if (meta.year.isNullOrBlank()) meta.year = yearOf(value)
                            "publisher" -> if (inPublishInfo && meta.publisher.isNullOrBlank())
                                meta.publisher = clean(value)
                            "isbn" -> if (meta.isbn.isNullOrBlank()) meta.isbn = isbnOf(value)
                            "year" -> if (inPublishInfo && meta.year.isNullOrBlank())
                                meta.year = yearOf(value)
                        }
                        text.setLength(0)
                        tag = null
                    }
                }
                event = parser.next()
            }
        }
        if (authors.isNotEmpty()) meta.author = authors.distinct().joinToString(", ").take(MAX_FIELD)
        if (genres.isNotEmpty()) meta.tags = genres.distinct().joinToString(", ").take(MAX_FIELD)
        return if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
    }

    // ---------------------------------------------------------------- MOBI (EXTH)

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun fromMobi(file: File): BookMeta? {
        // Elég a fájl elejét beolvasni: a 0. rekordban van minden metaadat
        val head = ByteArray(minOf(file.length(), 96 * 1024L).toInt())
        file.inputStream().use { it.read(head) }
        if (head.size < 100) return null
        val type = String(head, 60, 8, Charsets.US_ASCII)
        if (type != "BOOKMOBI" && type != "TEXtREAd") return null

        val numRecords = u16(head, 76)
        if (numRecords < 1 || 78 + numRecords * 8 > head.size) return null
        val rec0Start = u32(head, 78).toInt()
        val rec0End = if (numRecords > 1) u32(head, 78 + 8).toInt() else head.size
        if (rec0Start < 0 || rec0End > head.size || rec0Start >= rec0End) return null
        val r0 = head.copyOfRange(rec0Start, rec0End)
        if (r0.size < 24 || String(r0, 16, 4, Charsets.US_ASCII) != "MOBI") return null

        val mobiHeaderLen = u32(r0, 20).toInt()
        val encoding = u32(r0, 28).toInt()
        val charset: Charset = if (encoding == 1252) Charset.forName("windows-1252") else Charsets.UTF_8
        val meta = BookMeta(source = "mobi")

        // Teljes cím a MOBI fejléc végén
        if (r0.size >= 92) {
            val nameOff = u32(r0, 84).toInt()
            val nameLen = u32(r0, 88).toInt()
            if (nameOff > 0 && nameLen in 1..1024 && nameOff + nameLen <= r0.size) {
                meta.title = clean(String(r0, nameOff, nameLen, charset))
            }
        }

        // EXTH blokk
        val exthStart = 16 + mobiHeaderLen
        val hasExth = r0.size > 132 && (u32(r0, 128).toInt() and 0x40) != 0
        if (hasExth && exthStart + 12 <= r0.size &&
            String(r0, exthStart, 4, Charsets.US_ASCII) == "EXTH"
        ) {
            val count = u32(r0, exthStart + 8).toInt()
            var pos = exthStart + 12
            val subjects = ArrayList<String>()
            val authors = ArrayList<String>()
            var i = 0
            while (i < count && pos + 8 <= r0.size) {
                val recType = u32(r0, pos).toInt()
                val recLen = u32(r0, pos + 4).toInt()
                if (recLen < 8 || pos + recLen > r0.size) break
                val value = String(r0, pos + 8, recLen - 8, charset)
                when (recType) {
                    100 -> clean(value)?.let { authors.add(it) }
                    101 -> if (meta.publisher.isNullOrBlank()) meta.publisher = clean(value)
                    103 -> if (meta.description.isNullOrBlank()) meta.description = cleanDescription(value)
                    104 -> if (meta.isbn.isNullOrBlank()) meta.isbn = isbnOf(value)
                    105 -> clean(value)?.let { subjects.add(it) }
                    106 -> if (meta.year.isNullOrBlank()) meta.year = yearOf(value)
                    503 -> clean(value)?.let { meta.title = it }
                    524 -> if (meta.language.isNullOrBlank()) meta.language = clean(value, 12)
                }
                pos += recLen
                i++
            }
            if (authors.isNotEmpty()) meta.author = authors.distinct().joinToString(", ").take(MAX_FIELD)
            if (subjects.isNotEmpty()) meta.tags = subjects.distinct().joinToString(", ").take(MAX_FIELD)
        }
        return if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
    }

    // ---------------------------------------------------------------- DOCX

    private fun fromDocx(file: File): BookMeta? {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("docProps/core.xml") ?: return null
            val meta = BookMeta(source = "docx")
            zip.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                var tag: String? = null
                val text = StringBuilder()
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            tag = localName(parser.name); text.setLength(0)
                        }
                        XmlPullParser.TEXT -> text.append(parser.text)
                        XmlPullParser.END_TAG -> {
                            val name = localName(parser.name)
                            if (name == tag) {
                                val value = text.toString()
                                when (name) {
                                    "title" -> meta.title = clean(value)
                                    "creator" -> meta.author = clean(value)
                                    "description" -> meta.description = cleanDescription(value)
                                    "subject", "keywords" -> if (meta.tags.isNullOrBlank())
                                        meta.tags = clean(value)
                                }
                            }
                            text.setLength(0); tag = null
                        }
                    }
                    event = parser.next()
                }
            }
            return if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
        }
    }

    // ---------------------------------------------------------------- RTF (\info blokk)

    private fun fromRtf(file: File): BookMeta? {
        // Az \info blokk a fájl elején van; elég az első pár tíz kilobájt
        val head = ByteArray(minOf(file.length(), 128 * 1024L).toInt())
        file.inputStream().use { it.read(head) }
        val ascii = String(head, Charsets.ISO_8859_1)
        val infoIdx = ascii.indexOf("{\\info")
        if (infoIdx < 0) return null

        var charset: Charset = Charset.forName("windows-1252")
        Regex("\\\\ansicpg(\\d+)").find(ascii.take(2000))?.let {
            charset = try {
                Charset.forName("windows-" + it.groupValues[1])
            } catch (e: Exception) {
                charset
            }
        }

        fun group(keyword: String): String? {
            val start = ascii.indexOf("{\\$keyword", infoIdx)
            if (start < 0) return null
            var depth = 0
            var i = start
            val sb = StringBuilder()
            var contentStart = start + keyword.length + 2
            while (i < ascii.length) {
                val c = ascii[i]
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) break
                }
                i++
            }
            if (i >= ascii.length) return null
            val raw = ascii.substring(contentStart, i)
            // \'hh escape-ek feloldása a megfelelő kódlappal
            val bytes = java.io.ByteArrayOutputStream()
            var j = 0
            while (j < raw.length) {
                val c = raw[j]
                if (c == '\\' && j + 3 < raw.length && raw[j + 1] == '\'') {
                    val h = raw.substring(j + 2, j + 4).toIntOrNull(16)
                    if (h != null) {
                        bytes.write(h); j += 4; continue
                    }
                }
                if (c == '\\') {
                    // egyéb vezérlőszó átugrása
                    j++
                    while (j < raw.length && raw[j].isLetter()) j++
                    while (j < raw.length && (raw[j].isDigit() || raw[j] == '-')) j++
                    if (j < raw.length && raw[j] == ' ') j++
                    continue
                }
                bytes.write(c.code and 0xFF)
                j++
            }
            sb.append(String(bytes.toByteArray(), charset))
            return clean(sb.toString())
        }

        val meta = BookMeta(source = "rtf")
        meta.title = group("title")
        meta.author = group("author")
        meta.tags = group("subject")
        meta.description = cleanDescription(group("doccomm"))
        return if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
    }

    // ---------------------------------------------------------------- PDF

    private fun fromPdf(context: Context, file: File): BookMeta? {
        if (!pdfBoxReady) {
            synchronized(this) {
                if (!pdfBoxReady) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfBoxReady = true
                }
            }
        }
        return try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val info = doc.documentInformation
                val meta = BookMeta(source = "pdf")
                meta.title = clean(info.title)?.takeIf { plausibleTitle(it) }
                meta.author = clean(info.author)?.takeIf { plausibleAuthor(it) }
                meta.tags = clean(info.keywords)
                val subject = clean(info.subject, MAX_DESC)
                if (!subject.isNullOrBlank() && subject.length > 40) {
                    meta.description = cleanDescription(subject)
                }
                if (meta.title.isNullOrBlank() && meta.author.isNullOrBlank()) null else meta
            }
        } catch (e: Exception) {
            null
        }
    }

    /** A PDF-ek „címe” gyakran fájlnév vagy a szerkesztőprogram szemete. */
    private fun plausibleTitle(t: String): Boolean {
        val l = t.lowercase()
        if (t.length < 3 || t.length > 200) return false
        if (l.endsWith(".pdf") || l.endsWith(".doc") || l.endsWith(".indd") ||
            l.endsWith(".qxd") || l.endsWith(".tex")
        ) return false
        val junk = listOf(
            "microsoft word", "untitled", "névtelen", "document1", "print",
            "adobe", "acrobat", "pdfcreator", "scan", "output", "layout"
        )
        if (junk.any { l.contains(it) }) return false
        // csupa szám vagy kód
        if (Regex("^[0-9a-f _.\\-]+$").matches(l)) return false
        return true
    }

    private fun plausibleAuthor(a: String): Boolean {
        val l = a.lowercase()
        if (a.length < 3 || a.length > 120) return false
        val junk = listOf("user", "admin", "windows", "pc", "adobe", "unknown", "acrobat")
        return !junk.any { l == it || l.startsWith("$it ") }
    }

    // ---------------------------------------------------------------- fájlnév

    private val parenRegex = Regex("\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]")
    private val leadingNumber = Regex("^\\s*\\d{1,3}[.\\-_ ]+\\s*")

    private fun isJunkGroup(g: String): Boolean {
        val l = g.lowercase().trim()
        if (l.isBlank()) return true
        if (Regex("^\\d{1,4}$").matches(l)) return true
        val junk = listOf(
            "z-library", "zlibrary", "z-lib", "1lib", "lib.sk", "annas", "anna's",
            "libgen", "ncore", "hunebook", "ebook", "epub", "mobi", "pdf",
            "olvas", "javított", "javitott", "szerk", "vágatlan", "vagatlan",
            "scan", "ocr", "hu", "magyar"
        )
        return junk.any { l.contains(it) }
    }

    /**
     * Cím és szerző kitalálása a fájlnévből.
     * Kezelt minták: "Szerző - Cím", "Cím (Szerző)", vezető sorszám.
     */
    fun fromFileName(fileName: String): Pair<String?, String?> {
        val base = fileName.substringBeforeLast('.')
        val groups = ArrayList<String>()
        parenRegex.findAll(base).forEach { m ->
            val g = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (g.isNotEmpty()) groups.add(g)
        }
        var remainder = parenRegex.replace(base, " ")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        remainder = leadingNumber.replace(remainder, "")

        val authorCandidates = groups.filter { !isJunkGroup(it) }

        // "Szerző - Cím" minta
        val dashIdx = remainder.indexOf(" - ")
        if (dashIdx > 0) {
            val left = remainder.substring(0, dashIdx).trim()
            val right = remainder.substring(dashIdx + 3).trim()
            // A rövidebb, 1-4 szavas oldal valószínűbb, hogy a szerző
            val leftWords = left.split(' ').size
            return if (leftWords in 1..4 && left.length <= 40) {
                Pair(clean(right), clean(left))
            } else {
                Pair(clean(left), clean(right))
            }
        }

        // "Cím (Szerző)" minta
        if (authorCandidates.isNotEmpty() && remainder.isNotBlank()) {
            return Pair(clean(remainder), clean(authorCandidates.first()))
        }
        return Pair(clean(remainder.ifBlank { base }), null)
    }
}
