package hu.konyvtar.tts.data

/**
 * Kiejtési szótár: átírások a felolvasó motornak.
 *
 * A magyar hang a kitalált és az idegen neveket rendre elrontja („Bree” →
 * „bree” helyett „brí”), és ezen a beszédsebesség állítgatása nem segít.
 * Itt megadhatod, minek *mondja* a motor az adott szót.
 *
 * A csere közvetlenül a felolvasás előtt történik, azon a szövegen, amit a
 * motornak átadunk — a könyv szövegéhez nem nyúlunk. Ez fontos: a képernyőn
 * kiemelt mondat karakterpozíciói így nem csúsznak el, és a keresés is az
 * eredeti szövegben keres tovább.
 *
 * A csere **szókezdethez van kötve, de a végződést nem bántja**: a `Bree`
 * szabály a „Breet” és a „Bree-nek” alakot is eltalálja, mert csak az elejét
 * cseréli, a magyar rag pedig a helyén marad. Ennek az ára, hogy egy rövid
 * minta belelóghat egy hosszabb szóba; ilyenkor írj hosszabb mintát.
 */
object Pronounce {

    /** Egy szabály: a [pattern] helyett a motor a [sayAs] szöveget mondja. */
    data class Rule(val id: Long, val pattern: String, val sayAs: String)

    /**
     * A szabályok alkalmazása a felolvasandó szövegre.
     *
     * Egyetlen balról jobbra haladó menetben dolgozik, ezért **nem
     * láncolódik**: amit egy szabály beírt, azt egy másik már nem alakítja
     * tovább. A hosszabb minta előnyt élvez, így egy `Bre` szabály nem lopja
     * el a `Brego` elől a találatot.
     */
    fun apply(text: String, rules: List<Rule>): String {
        if (text.isEmpty()) return text
        val sorted = rules
            .filter { it.pattern.isNotBlank() }
            .sortedByDescending { it.pattern.length }
        if (sorted.isEmpty()) return text

        val out = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            // Csak szó elején cserélünk, hogy a minta ne kapjon bele egy
            // hosszabb szó közepébe.
            val atWordStart = i == 0 || !text[i - 1].isLetterOrDigit()
            var hit: Rule? = null
            if (atWordStart) {
                for (r in sorted) {
                    if (text.regionMatches(i, r.pattern, 0, r.pattern.length, ignoreCase = true)) {
                        hit = r
                        break
                    }
                }
            }
            if (hit != null) {
                out.append(hit.sayAs)
                i += hit.pattern.length
            } else {
                out.append(text[i])
                i++
            }
        }
        return out.toString()
    }

    // ------------------------------------------------------------------ tár

    /**
     * A szabályok gyorsítótára. A felolvasó minden mondatnál elkérné őket, és
     * mondatonként egy adatbázis-lekérdezés fölösleges teher; a szótár ritkán
     * változik, olyankor a szerkesztő [invalidate]-et hív.
     */
    @Volatile
    private var cache: List<Rule>? = null

    /** Az érvényes szabályok — első hívásra az adatbázisból. */
    fun rules(): List<Rule> = cache ?: AppDb.pronounceRules().also { cache = it }

    /** A szótár változott: a következő felolvasás már az újat használja. */
    fun invalidate() {
        cache = null
    }
}
