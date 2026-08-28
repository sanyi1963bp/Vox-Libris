package hu.konyvtar.tts.reader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream

/** FB2 (FictionBook 2) olvasó: a <body> bekezdéseit gyűjti, <binary> blokkokat kihagyja. */
object Fb2Parser {

    private val paraTags = setOf("p", "v", "subtitle", "text-author")

    fun parse(file: File): List<String> {
        val out = ArrayList<String>(1024)
        FileInputStream(file).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null) // kódolás automatikus az XML prológból
            var event = parser.eventType
            var bodyDepth = 0
            var binaryDepth = 0
            var paraDepth = 0
            var titleDepth = 0
            val sb = StringBuilder()

            fun localName(q: String) = q.substringAfterLast(':')

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (localName(parser.name)) {
                            "body" -> bodyDepth++
                            "binary" -> binaryDepth++
                            "title" -> titleDepth++
                            "empty-line" -> {
                                if (bodyDepth > 0 && paraDepth == 0 && sb.isNotEmpty()) {
                                    out.add(sb.toString().trim())
                                    sb.setLength(0)
                                }
                            }
                            in paraTags -> if (bodyDepth > 0) paraDepth++
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (bodyDepth > 0 && binaryDepth == 0 && paraDepth > 0) {
                            sb.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (localName(parser.name)) {
                            "body" -> bodyDepth--
                            "binary" -> binaryDepth--
                            "title" -> titleDepth--
                            in paraTags -> {
                                if (bodyDepth > 0 && paraDepth > 0) {
                                    paraDepth--
                                    if (paraDepth == 0) {
                                        val t = sb.toString().replace(Regex("\\s+"), " ").trim()
                                        // A <title> szekciócímek fejezethatárok
                                        if (t.isNotEmpty()) out.add(if (titleDepth > 0) "\u0001$t" else t)
                                        sb.setLength(0)
                                    }
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }
        if (out.isEmpty()) {
            throw ExtractException("Az FB2 fájlból nem sikerült szöveget kinyerni.")
        }
        return out
    }
}
