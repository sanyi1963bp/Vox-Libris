package hu.konyvtar.tts.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import hu.konyvtar.tts.R

/** DOCX olvasó: word/document.xml bekezdései. (A régi bináris .doc nem támogatott.) */
object DocxParser {

    fun parse(file: File): List<String> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw ExtractException(R.string.err_docx_invalid)
            val out = ArrayList<String>(512)
            val sb = StringBuilder()
            zip.getInputStream(entry).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                var event = parser.eventType
                var inText = false

                fun localName(q: String) = q.substringAfterLast(':')

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (localName(parser.name)) {
                            "t" -> inText = true
                            "tab" -> sb.append(' ')
                            "br", "cr" -> sb.append(' ')
                        }
                        XmlPullParser.TEXT -> if (inText) sb.append(parser.text)
                        XmlPullParser.END_TAG -> when (localName(parser.name)) {
                            "t" -> inText = false
                            "p" -> {
                                val t = sb.toString().replace(Regex("\\s+"), " ").trim()
                                if (t.isNotEmpty()) out.add(t)
                                sb.setLength(0)
                            }
                        }
                    }
                    event = parser.next()
                }
            }
            if (sb.isNotBlank()) out.add(sb.toString().trim())
            if (out.isEmpty()) throw ExtractException(R.string.err_docx_no_text)
            return out
        }
    }
}
