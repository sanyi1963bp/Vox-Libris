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
}
