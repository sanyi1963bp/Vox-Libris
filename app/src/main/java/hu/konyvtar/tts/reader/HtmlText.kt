package hu.konyvtar.tts.reader

/**
 * HTML -> sima szöveg (bekezdéslista). Képeket, stílust, szkriptet eldob.
 * A címsorokból (h1-h6) készült bekezdéseket U+0001 előtaggal jelöli —
 * ezekből lesznek a fejezethatárok.
 */
object HtmlText {

    private val headingTag = Regex("(?is)<h[1-6][^>]*>(.*?)</h[1-6]\\s*>")

    private val blockClose = Regex(
        "(?i)</(p|div|h[1-6]|li|tr|td|blockquote|section|article|dt|dd|figcaption|pre|table|ul|ol)>"
    )
    private val blockOpen = Regex(
        "(?i)<(p|div|h[1-6]|li|tr|blockquote|section|article|pre|table)(\\s[^>]*)?>"
    )
    private val brTag = Regex("(?i)<(br|hr)\\s*/?\\s*>")
    private val headBlocks = Regex("(?is)<(script|style|head|title|svg)[^>]*>.*?</\\1>")
    private val comments = Regex("(?s)<!--.*?-->")
    private val anyTag = Regex("<[^>]{0,500}>")
    private val entity = Regex("&(#x?[0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,30});")
    private val hSpace = Regex("[ \\t\\x0B\\f\\r\\u00A0]+")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "shy" to "", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”",
        "ldquo" to "“", "bdquo" to "„", "raquo" to "»", "laquo" to "«",
        "copy" to "©", "reg" to "®", "trade" to "™", "sect" to "§",
        "middot" to "·", "bull" to "•", "dagger" to "†", "prime" to "′",
        "times" to "×", "divide" to "÷", "plusmn" to "±", "deg" to "°",
        "frac12" to "½", "frac14" to "¼", "eacute" to "é", "aacute" to "á",
        "iacute" to "í", "oacute" to "ó", "uacute" to "ú", "ouml" to "ö",
        "uuml" to "ü", "otilde" to "ő", "utilde" to "ű", "Eacute" to "É",
        "Aacute" to "Á", "Ouml" to "Ö", "Uuml" to "Ü"
    )

    fun decodeEntities(s: String): String {
        return entity.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> {
                    val code = body.substring(2).toIntOrNull(16)
                    if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
                }
                body.startsWith("#") -> {
                    val code = body.substring(1).toIntOrNull()
                    if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
                }
                else -> named[body] ?: m.value
            }
        }
    }

    /** Teljes HTML dokumentum bekezdésekre bontása (fejezetcímek jelölve). */
    fun toParagraphs(html: String): List<String> {
        var s = html
        s = comments.replace(s, " ")
        s = headBlocks.replace(s, " ")
        s = headingTag.replace(s) { m -> "\n\u0001" + m.groupValues[1] + "\n" }
        s = brTag.replace(s, "\n")
        s = blockClose.replace(s, "\n")
        s = blockOpen.replace(s, "\n")
        s = anyTag.replace(s, " ")
        s = decodeEntities(s)
        s = s.replace("​", "").replace("﻿", "").replace("­", "")
        val out = ArrayList<String>()
        for (line in s.split('\n')) {
            // A trim() a vezérlőkaraktert is levágná — előbb kimentjük a jelölést
            val isHeading = line.indexOf('\u0001') >= 0
            val t = hSpace.replace(line.replace("\u0001", ""), " ").trim()
            if (t.isNotEmpty()) out.add(if (isHeading) "\u0001$t" else t)
        }
        return out
    }
}
