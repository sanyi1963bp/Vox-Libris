package hu.konyvtar.tts.reader

/**
 * Mondathatár-felismerés magyar szöveghez.
 * Mondatvég: . ! ? … — ha utána (záró idézőjelek átugrásával) szóköz és
 * nagybetű/szám/nyitó idézőjel jön, így a "dr." és a "3. fejezet" jellegű
 * rövidítések ritkán tévesztik meg.
 */
object Sentences {

    private const val CLOSERS = "\"'”’»)]"
    private const val OPENERS = "\"'„“«‒–—-"

    /** A mondatkezdetek offsetjei a szövegben (az első mindig 0). */
    fun starts(text: String): List<Int> {
        val out = ArrayList<Int>(8)
        out.add(0)
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                var k = i + 1
                while (k < n && CLOSERS.indexOf(text[k]) >= 0) k++
                var s = k
                while (s < n && text[s] == ' ') s++
                if (s > k && s < n) {
                    val nc = text[s]
                    if (nc.isUpperCase() || nc.isDigit() || OPENERS.indexOf(nc) >= 0) {
                        out.add(s)
                    }
                }
                i = k
            } else {
                i++
            }
        }
        return out
    }

    /** Annak a mondatnak a kezdő indexe, amelyikbe az [offset] esik. */
    fun startAt(text: String, offset: Int): Int {
        val off = offset.coerceIn(0, text.length)
        var best = 0
        for (s in starts(text)) {
            if (s <= off) best = s else break
        }
        return best
    }

    /**
     * Annak a mondatnak a határai, amelyikbe az [offset] esik: [kezdet, vég).
     * A műveletmenü ezzel tudja megmutatni, melyik mondatra nyomódott az ujj.
     */
    fun boundsAt(text: String, offset: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val off = offset.coerceIn(0, text.length)
        val s = starts(text)
        var start = 0
        var end = text.length
        for (i in s.indices) {
            if (s[i] <= off) {
                start = s[i]
                end = if (i + 1 < s.size) s[i + 1] else text.length
            } else {
                break
            }
        }
        return start to end
    }

    /**
     * A szöveg szavai, előfordulási sorrendben, ismétlés nélkül.
     *
     * A műveletmenü ezekből kínál választható szavakat a kiejtéshez és a
     * Wikipédiához. Az egybetűs darabokat kihagyjuk: névelőre és kötőszóra
     * sem kiejtési szabály, sem szócikk nem kell.
     */
    fun words(text: String): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        val sb = StringBuilder()
        for (c in text) {
            if (c.isLetterOrDigit()) sb.append(c) else take(sb, seen, out)
        }
        take(sb, seen, out)
        return out
    }

    private fun take(sb: StringBuilder, seen: HashSet<String>, out: ArrayList<String>) {
        if (sb.length >= 2) {
            val w = sb.toString()
            if (seen.add(w.lowercase())) out.add(w)
        }
        sb.setLength(0)
    }
}
