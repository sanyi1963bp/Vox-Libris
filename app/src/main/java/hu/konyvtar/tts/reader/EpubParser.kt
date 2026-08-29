package hu.konyvtar.tts.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import hu.konyvtar.tts.R

/**
 * EPUB olvasó: container.xml -> OPF -> spine sorrendben a HTML fejezetek.
 * Képekhez, borítóhoz nem nyúl.
 */
object EpubParser {

    fun parse(file: File): List<String> {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val htmlEntries: List<ZipEntry> = if (opfPath != null) {
                val fromSpine = spineEntries(zip, opfPath)
                fromSpine.ifEmpty { fallbackEntries(zip) }
            } else {
                fallbackEntries(zip)
            }
            if (htmlEntries.isEmpty()) {
                throw ExtractException(R.string.err_epub_no_html)
            }
            val out = ArrayList<String>(1024)
            for (entry in htmlEntries) {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                val html = TxtParser.decode(bytes)
                val docParas = HtmlText.toParagraphs(html)
                if (docParas.isEmpty()) continue
                // Minden spine-dokumentum (tipikusan fejezet) eleje fejezethatár
                val first = docParas[0]
                out.add(if (first.startsWith('\u0001')) first else "\u0001" + first)
                for (i in 1 until docParas.size) {
                    out.add(docParas[i])
                }
            }
            if (out.isEmpty()) {
                throw ExtractException(R.string.err_epub_no_text)
            }
            return out
        }
    }

    /** A csomagoló OPF fájl útvonala a zipen belül (a metaadat-kinyerő is használja). */
    internal fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        return try {
            zip.getInputStream(container).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.endsWith("rootfile")) {
                        for (i in 0 until parser.attributeCount) {
                            if (parser.getAttributeName(i).endsWith("full-path")) {
                                return parser.getAttributeValue(i)
                            }
                        }
                    }
                    event = parser.next()
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun localName(qname: String): String = qname.substringAfterLast(':')

    private fun spineEntries(zip: ZipFile, opfPath: String): List<ZipEntry> {
        val opfEntry = zip.getEntry(opfPath) ?: return emptyList()
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = HashMap<String, Pair<String, String>>() // id -> (href, mediaType)
        val spine = ArrayList<String>()
        try {
            zip.getInputStream(opfEntry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (localName(parser.name)) {
                            "item" -> {
                                var id: String? = null
                                var href: String? = null
                                var media: String? = null
                                for (i in 0 until parser.attributeCount) {
                                    when (localName(parser.getAttributeName(i))) {
                                        "id" -> id = parser.getAttributeValue(i)
                                        "href" -> href = parser.getAttributeValue(i)
                                        "media-type" -> media = parser.getAttributeValue(i)
                                    }
                                }
                                if (id != null && href != null) {
                                    manifest[id] = Pair(href, media ?: "")
                                }
                            }
                            "itemref" -> {
                                for (i in 0 until parser.attributeCount) {
                                    if (localName(parser.getAttributeName(i)) == "idref") {
                                        spine.add(parser.getAttributeValue(i))
                                    }
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        val out = ArrayList<ZipEntry>()
        for (idref in spine) {
            val (href, media) = manifest[idref] ?: continue
            val isHtml = media.contains("html", ignoreCase = true) ||
                href.endsWith(".xhtml", true) || href.endsWith(".html", true) || href.endsWith(".htm", true)
            if (!isHtml) continue
            val decoded = urlDecode(href)
            val full = if (opfDir.isEmpty()) decoded else "$opfDir/$decoded"
            val normalized = normalizePath(full)
            val entry = zip.getEntry(normalized) ?: zip.getEntry(decoded)
            if (entry != null) out.add(entry)
        }
        return out
    }

    private fun fallbackEntries(zip: ZipFile): List<ZipEntry> {
        val list = ArrayList<ZipEntry>()
        val en = zip.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            val n = e.name.lowercase()
            if (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) {
                list.add(e)
            }
        }
        list.sortBy { it.name }
        return list
    }

    private fun urlDecode(s: String): String {
        return try {
            java.net.URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }
    }

    /** "OEBPS/../text/a.xhtml" -> "text/a.xhtml" jellegű normalizálás. */
    private fun normalizePath(path: String): String {
        val parts = ArrayList<String>()
        for (p in path.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }
}
