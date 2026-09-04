package hu.konyvtar.tts.reader

/**
 * Karakternévtár: kik szerepelnek a könyvben — abból, amit **már olvastál**.
 *
 * Felbukkan egy név, és nem emlékszel, ki az. Ez kigyűjti a szereplőket
 * gyakoriság szerint, mindegyikhez azzal a mondattal, ahol először feltűnt.
 *
 * **Spoilermentes**: csak az olvasási pozícióig néz. Nem árulja el, hogy a
 * későbbi fejezetekben ki bukkan még fel — ez a lényege, nem mellékes
 * körülmény.
 *
 * Hogyan ismer fel egy nevet? Nagybetűvel kezdődik, és **nem csak mondat
 * elején** áll. Ez a kettő együtt elég erős jel: a hétköznapi szavak is
 * nagybetűsek mondatkezdéskor, de mondat közepén már nem azok. Ezért nem
 * kell hozzá névlista, és ezért működik bármelyik nyelven.
 *
 * A magyar ragozást tőre vonással kezeljük: a „Gandalfot", „Gandalfnak",
 * „Gandalffal" alakok a „Gandalf" alá kerülnek, mert az a legrövidebb alak,
 * aminek a többi a folytatása. **Ez nem tökéletes** — a fő szereplőknél
 * megbízhatóan működik, ritkább neveknél elmaradhat egy-egy alak.
 */
object Characters {

    /** Egy szereplő: neve, hányszor fordult elő, és hol tűnt fel először. */
    data class Person(
        val name: String,
        val count: Int,
        val firstParaIndex: Int,
        val firstSentence: String
    )

    /** Ennyi előfordulás alatt nem tekintjük szereplőnek. */
    private const val MIN_HITS = 2

    /** Ekkora ragot még hozzátoldásnak nézünk, nem külön névnek. */
    private const val MAX_SUFFIX = 4

    /** Idézőjelek és gondolatjelek, amiket a mondat elején átugrunk. */
    private const val OPENERS = "\"'„“«»–—-*·•  \t"

    /**
     * A szereplők listája, gyakoriság szerint csökkenő sorrendben.
     *
     * @param toPara az olvasási pozíció bekezdése (ezt még beleszámoljuk)
     * @param toChar a pozíció karaktere ezen a bekezdésen belül
     */
    fun find(
        paragraphs: List<String>,
        toPara: Int,
        toChar: Int,
        limit: Int = 60
    ): List<Person> {
        if (paragraphs.isEmpty()) return emptyList()
        val end = toPara.coerceIn(0, paragraphs.size - 1)

        /** Nagybetűs, mondat közepén álló előfordulások. */
        val midCount = HashMap<String, Int>()
        /** Nagybetűs előfordulások összesen (mondat elején is). */
        val upperCount = HashMap<String, Int>()
        /** Kisbetűs előfordulások — ebből derül ki, hogy köznév-e. */
        val lowerCount = HashMap<String, Int>()
        /** Az első előfordulás helye: bekezdés + a mondat szövege. */
        val firstAt = HashMap<String, Pair<Int, String>>()

        for (i in 0..end) {
            val full = paragraphs[i]
            val text = if (i == end) full.substring(0, toChar.coerceIn(0, full.length)) else full
            if (text.isBlank()) continue

            val starts = Sentences.starts(text)
            val firstWordPos = starts.mapTo(HashSet()) { skipOpeners(text, it) }

            for ((from, to) in wordRanges(text)) {
                val word = text.substring(from, to)
                if (word.length < 3) continue
                val first = word[0]
                if (!first.isLetter()) continue

                if (first.isUpperCase()) {
                    // A csupa nagybetűs szó kiabálás vagy rövidítés, nem név.
                    if (word.all { !it.isLetter() || it.isUpperCase() }) continue
                    upperCount[word] = (upperCount[word] ?: 0) + 1
                    if (from !in firstWordPos) {
                        midCount[word] = (midCount[word] ?: 0) + 1
                    }
                    if (word !in firstAt) {
                        firstAt[word] = i to sentenceAround(text, starts, from)
                    }
                } else {
                    val key = word.replaceFirstChar { it.uppercaseChar() }
                    lowerCount[key] = (lowerCount[key] ?: 0) + 1
                }
            }
        }

        // Jelölt az, ami mondat közepén is nagybetűs, és nem gyakoribb
        // kisbetűvel — utóbbi esetben köznév, ami történetesen mondat elején
        // is előfordult.
        // MINDEN nagybetűs szó jelölt, nem csak a mondat közepén látottak —
        // a „Frodó" gyakran mondatot kezd, és csak a „Frodóval" alakja esik
        // középre. Ha itt szűrnénk a mondat közepére, a név sosem kerülne be,
        // hiába vonnánk össze utána a ragozott alakjait.
        val candidates = upperCount.keys.filter { w ->
            (lowerCount[w] ?: 0) < (upperCount[w] ?: 0)
        }
        if (candidates.isEmpty()) return emptyList()

        // A küszöb a CSOPORTRA vonatkozik, nem az egyes alakokra. Egy magyar
        // szövegben ugyanaz a név öt különböző ragozott alakban fordulhat elő
        // egyszer-egyszer; ha alakonként szűrnénk, a szereplő eltűnne.
        //
        // Két külön kérdést teszünk fel, mert két külön dologról szólnak:
        //  - áll-e mondat közepén nagybetűvel? Ez bizonyítja, hogy NÉV.
        //  - előfordul-e legalább kétszer? Ez bizonyítja, hogy SZÁMÍT.
        // Ha a mondat közepi előfordulásból kérnénk kettőt, kimaradna az a
        // szereplő, aki többnyire mondatot kezd — pedig az gyakori.
        val people = group(candidates).mapNotNull { (name, forms) ->
            if (forms.sumOf { midCount[it] ?: 0 } < 1) return@mapNotNull null
            val total = forms.sumOf { upperCount[it] ?: 0 }
            if (total < MIN_HITS) return@mapNotNull null
            val first = forms.mapNotNull { firstAt[it] }.minByOrNull { it.first }
            Person(
                name = name,
                count = total,
                firstParaIndex = first?.first ?: 0,
                firstSentence = first?.second.orEmpty()
            )
        }
        return people.sortedWith(
            compareByDescending<Person> { it.count }.thenBy { it.name }
        ).take(limit)
    }

    /**
     * Ragozott alakok összevonása: a legrövidebb alak alá, aminek a többi a
     * folytatása. A [MAX_SUFFIX]-nál hosszabb toldalék már gyanús — így nem
     * olvad össze az „Anna" és az „Annamária".
     */
    internal fun group(words: List<String>): Map<String, List<String>> {
        val sorted = words.sortedBy { it.length }
        val out = LinkedHashMap<String, MutableList<String>>()
        for (w in sorted) {
            val stem = out.keys.firstOrNull { base ->
                w.length - base.length <= MAX_SUFFIX && continues(w, base)
            }
            if (stem != null) out.getValue(stem).add(w) else out[w] = mutableListOf(w)
        }
        return out
    }

    /**
     * A [word] a [base] ragozott alakja-e.
     *
     * Nem elég a sima „ezzel kezdődik", mert **a magyar ragozás megnyújtja a
     * tővégi magánhangzót**: Anna → Annát, Kata → Katát, Emese → Emesét.
     * Enélkül minden `-a` és `-e` végű név kimaradna az összevonásból, pedig
     * magyar szövegben abból van a legtöbb.
     */
    private fun continues(word: String, base: String): Boolean {
        if (word.length <= base.length) return false
        for (i in base.indices) {
            val a = word[i].lowercaseChar()
            val b = base[i].lowercaseChar()
            if (a == b) continue
            val lengthened = (b == 'a' && a == 'á') || (b == 'e' && a == 'é')
            if (!lengthened || i != base.length - 1) return false
        }
        return true
    }

    /** A mondat eleji idézőjelek, gondolatjelek átugrása. */
    private fun skipOpeners(text: String, start: Int): Int {
        var i = start
        while (i < text.length && OPENERS.indexOf(text[i]) >= 0) i++
        return i
    }

    /** A szavak határai a szövegben: betűk és számjegyek összefüggő futamai. */
    private fun wordRanges(text: String): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (!text[i].isLetterOrDigit()) {
                i++
                continue
            }
            val from = i
            while (i < n && text[i].isLetterOrDigit()) i++
            out.add(from to i)
        }
        return out
    }

    /** Az a mondat, amelyikben a [pos] karakter áll. */
    private fun sentenceAround(text: String, starts: List<Int>, pos: Int): String {
        var s = 0
        var e = text.length
        for (i in starts.indices) {
            if (starts[i] <= pos) {
                s = starts[i]
                e = if (i + 1 < starts.size) starts[i + 1] else text.length
            } else {
                break
            }
        }
        return text.substring(s, e).trim()
    }
}
