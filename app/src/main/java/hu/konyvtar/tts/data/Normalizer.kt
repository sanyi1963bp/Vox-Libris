package hu.konyvtar.tts.data

/** Szöveg-normalizálás: kisbetű, ékezetek és írásjelek nélkül, szóközökkel. */
object Normalizer {

    private val zeroWidth = Regex("[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]")
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    /** Láthatatlan karakterek eltávolítása (egyes címekben U+200B is előfordul). */
    fun stripInvisible(s: String): String = zeroWidth.replace(s, "")

    fun norm(s: String): String {
        var t = stripInvisible(s).lowercase()
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
        t = marks.replace(t, "")
        t = nonAlnum.replace(t, " ")
        return t.trim()
    }

    fun tokens(s: String): Set<String> =
        norm(s).split(' ').filter { it.length > 1 }.toSet()

    /** Token-halmazok hasonlósága (0..1) — a névsorrendre érzéketlen. */
    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        return inter.toDouble() / union.toDouble()
    }

    /**
     * Gyors, hossztartó magyar ékezet-összevonás kereséshez (á→a, ő→o…).
     * A hossz nem változik, ezért a találat pozíciója az eredeti szövegben is
     * pontos — kiemeléshez használható.
     */
    fun foldHu(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = ch.lowercaseChar()
            sb.append(
                when (c) {
                    'á' -> 'a'
                    'é' -> 'e'
                    'í' -> 'i'
                    'ó', 'ö', 'ő' -> 'o'
                    'ú', 'ü', 'ű' -> 'u'
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}

/** A könyvfájlok kiterjesztései, amiket a könyvtárban keresünk. */
object BookFormats {
    val ALL = setOf(
        "epub", "pdf", "txt", "fb2", "mobi", "prc", "azw", "azw3",
        "rtf", "doc", "docx", "htm", "html", "djvu"
    )
}
