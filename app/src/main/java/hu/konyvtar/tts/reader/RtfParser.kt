package hu.konyvtar.tts.reader

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * RTF -> sima szöveg. Kezeli a \ansicpgN kódlapot (magyarnál tipikusan 1250),
 * a \uN unicode-ot, a \'hh hexát, és átugorja a képeket/metaadat-csoportokat.
 */
object RtfParser {

    private val skipDestinations = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object",
        "header", "footer", "headerl", "headerr", "headerf",
        "footerl", "footerr", "footerf", "footnote", "annotation",
        "generator", "themedata", "colorschememapping", "latentstyles",
        "datastore", "listtable", "listoverridetable", "rsidtbl", "filetbl",
        "revtbl", "xmlnstbl", "mmathPr", "wgrffmtfilter", "operator"
    )

    fun parse(bytes: ByteArray): List<String> {
        if (bytes.size < 5 || String(bytes, 0, 5, Charsets.US_ASCII) != "{\\rtf") {
            throw ExtractException("Nem érvényes RTF fájl.")
        }

        var charset: Charset = Charset.forName("windows-1252")
        val out = StringBuilder()
        val pending = ByteArrayOutputStream()

        fun flushPending() {
            if (pending.size() > 0) {
                out.append(String(pending.toByteArray(), charset))
                pending.reset()
            }
        }

        var i = 0
        var depth = 0
        var skipDepth = -1 // ha >= 0: eddig a mélységig minden kimarad
        var ucSkip = 1
        val ucStack = ArrayDeque<Int>()
        var pendingSkip = 0

        val n = bytes.size
        while (i < n) {
            val b = bytes[i].toInt() and 0xFF
            when (b.toChar()) {
                '{' -> {
                    ucStack.addLast(ucSkip)
                    depth++
                    i++
                }
                '}' -> {
                    depth--
                    if (ucStack.isNotEmpty()) ucSkip = ucStack.removeLast()
                    if (skipDepth >= 0 && depth < skipDepth) skipDepth = -1
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= n) break
                    val c = bytes[i].toInt() and 0xFF
                    val ch = c.toChar()
                    when {
                        ch.isLetter() -> {
                            // vezérlőszó beolvasása
                            val wordStart = i
                            while (i < n && (bytes[i].toInt() and 0xFF).toChar().isLetter()) i++
                            val word = String(bytes, wordStart, i - wordStart, Charsets.US_ASCII)
                            var param: Int? = null
                            var negative = false
                            if (i < n && (bytes[i].toInt() and 0xFF).toChar() == '-') {
                                negative = true
                                i++
                            }
                            var num = 0
                            var hasNum = false
                            while (i < n && (bytes[i].toInt() and 0xFF).toChar().isDigit()) {
                                num = num * 10 + ((bytes[i].toInt() and 0xFF) - '0'.code)
                                hasNum = true
                                i++
                            }
                            if (hasNum) param = if (negative) -num else num
                            // a vezérlőszót záró egyetlen szóköz elnyelése
                            if (i < n && (bytes[i].toInt() and 0xFF).toChar() == ' ') i++

                            val skipping = skipDepth >= 0
                            when (word) {
                                "ansicpg" -> if (param != null) {
                                    charset = try {
                                        Charset.forName("windows-$param")
                                    } catch (e: Exception) {
                                        charset
                                    }
                                }
                                "uc" -> ucSkip = param ?: 1
                                "u" -> if (!skipping && param != null) {
                                    flushPending()
                                    var code = param
                                    if (code < 0) code += 65536
                                    if (code in 1..0x10FFFF) out.append(String(Character.toChars(code)))
                                    pendingSkip = ucSkip
                                }
                                "par", "line", "sect", "page" -> if (!skipping) {
                                    flushPending()
                                    out.append('\n')
                                }
                                "tab" -> if (!skipping) {
                                    flushPending()
                                    out.append(' ')
                                }
                                "emdash" -> if (!skipping) { flushPending(); out.append('—') }
                                "endash" -> if (!skipping) { flushPending(); out.append('–') }
                                "lquote" -> if (!skipping) { flushPending(); out.append('‘') }
                                "rquote" -> if (!skipping) { flushPending(); out.append('’') }
                                "ldblquote" -> if (!skipping) { flushPending(); out.append('“') }
                                "rdblquote" -> if (!skipping) { flushPending(); out.append('”') }
                                "bullet" -> if (!skipping) { flushPending(); out.append('•') }
                                "bin" -> {
                                    // nyers bináris blokk: param bájt átugrása
                                    val len = param ?: 0
                                    i = minOf(n, i + len)
                                }
                                in skipDestinations -> if (skipDepth < 0) skipDepth = depth
                                else -> {} // formázó vezérlőszavak: nincs teendő
                            }
                        }
                        ch == '\'' -> {
                            // \'hh — egy bájt az aktuális kódlapon
                            if (i + 2 < n) {
                                val h1 = Character.digit((bytes[i + 1].toInt() and 0xFF).toChar(), 16)
                                val h2 = Character.digit((bytes[i + 2].toInt() and 0xFF).toChar(), 16)
                                i += 3
                                if (h1 >= 0 && h2 >= 0 && skipDepth < 0) {
                                    if (pendingSkip > 0) {
                                        pendingSkip--
                                    } else {
                                        pending.write((h1 shl 4) or h2)
                                    }
                                }
                            } else {
                                i = n
                            }
                        }
                        ch == '*' -> {
                            // \* — ignorálható cél: az egész csoport kimarad
                            if (skipDepth < 0) skipDepth = depth
                            i++
                        }
                        ch == '\\' || ch == '{' || ch == '}' -> {
                            if (skipDepth < 0) {
                                if (pendingSkip > 0) pendingSkip-- else pending.write(c)
                            }
                            i++
                        }
                        ch == '~' -> {
                            if (skipDepth < 0) { flushPending(); out.append(' ') }
                            i++
                        }
                        ch == '-' || ch == '_' -> {
                            i++ // opcionális kötőjel — kihagyjuk
                        }
                        ch == '\r' || ch == '\n' -> {
                            if (skipDepth < 0) { flushPending(); out.append('\n') }
                            i++
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++ // nyers sortörés az RTF-ben nem szöveg
                else -> {
                    if (skipDepth < 0) {
                        if (pendingSkip > 0) pendingSkip-- else pending.write(b)
                    }
                    i++
                }
            }
        }
        flushPending()

        val text = out.toString()
            .replace("​", "")
            .replace("﻿", "")
        val paras = text.split('\n')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
        if (paras.isEmpty()) throw ExtractException("Az RTF fájlból nem sikerült szöveget kinyerni.")
        return paras
    }
}
