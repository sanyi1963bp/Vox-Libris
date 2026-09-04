package hu.konyvtar.tts.reader

/**
 * Karakternévtár: kik szerepelnek a könyvben — abból, amit **már olvastál**.
 *
 * Felbukkan egy név, és nem emlékszel, ki az. Ez kigyűjti a szereplőket
 * gyakoriság szerint, és megpróbálja meg is mondani, kicsodák.
 *
 * **Spoilermentes**: csak az olvasási pozícióig néz. Nem árulja el, hogy a
 * későbbi fejezetekben ki bukkan még fel — ez a lényege, nem mellékes
 * körülmény.
 *
 * ## Hogyan ismer fel egy nevet
 *
 * Nagybetűvel kezdődik, és **nem csak mondat elején** áll. Ez a kettő együtt
 * elég erős jel: a hétköznapi szavak is nagybetűsek mondatkezdéskor, de
 * mondat közepén már nem azok. Ezért nem kell hozzá névlista, és ezért
 * működik bármelyik nyelven.
 *
 * A magyar ragozást tőre vonással kezeljük: a „Gandalfot", „Gandalfnak",
 * „Gandalffal" alakok a „Gandalf" alá kerülnek, mert az a legrövidebb alak,
 * aminek a többi a folytatása. **Ez nem tökéletes** — a fő szereplőknél
 * megbízhatóan működik, ritkább neveknél elmaradhat egy-egy alak.
 *
 * ## Ki kicsoda
 *
 * A puszta darabszám keveset mond. Hogy többet mondjunk, **nem értelmezzük a
 * szöveget** — megkeressük, hol mondja ki maga a könyv, és szó szerint
 * idézzük:
 *
 *  - **Közbevetés**: „Pista**, Jóska bátyja,** belépett." A két vessző közötti
 *    rész a regényekben majdnem mindig azonosítás. Ehhez nem kell szótár.
 *  - **Kapcsolatszó**: „Pista **Jóska bátyja** volt." Itt a [Relations]
 *    rokonsági és szereplistája adja a kapaszkodót.
 *  - **Kikkel szerepel együtt**: együtt-előfordulás bekezdésenként. Nem
 *    mondja meg, hogy „bátyja", de megmutatja a szereplő körét — szótár
 *    nélkül, bármelyik nyelven.
 */
object Characters {

    /** Egy másik szereplő, akivel gyakran egy bekezdésben szerepel. */
    data class Companion(val name: String, val count: Int)

    /** Egy szereplő mindazzal, amit a könyvből ki tudtunk olvasni róla. */
    data class Person(
        val name: String,
        val count: Int,
        val firstParaIndex: Int,
        val firstSentence: String,
        /** Amit a könyv mond róla, szó szerint — ha talált ilyet. */
        val descriptor: String? = null,
        /** Kikkel szerepel a leggyakrabban együtt. */
        val companions: List<Companion> = emptyList()
    )

    /** Ennyi előfordulás alatt nem tekintjük szereplőnek. */
    private const val MIN_HITS = 2

    /** Ekkora ragot még hozzátoldásnak nézünk, nem külön névnek. */
    private const val MAX_SUFFIX = 4

    /** Ennél hosszabb bemutatás már nem bemutatás, hanem fél bekezdés. */
    private const val MAX_DESCRIPTOR = 70

    /** Ennyi társat mutatunk egy szereplőnél. */
    private const val MAX_COMPANIONS = 3

    /** Idézőjelek és gondolatjelek, amiket a mondat elején átugrunk. */
    private const val OPENERS = "\"'„“«»–—-*·•  \t"

    /**
     * Felsorolás-kötőszavak: ha a közbevetés ezekkel kezdődik, akkor nem
     * bemutatás, hanem lista — „Pista, és Jóska, meg Elemér".
     */
    private val LIST_WORDS = setOf("és", "s", "meg", "vagy", "valamint", "illetve")

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

        // ---- első menet: ki lehet egyáltalán név
        for (i in 0..end) {
            val text = cut(paragraphs, i, end, toChar)
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
        val groups = group(candidates).filter { (_, forms) ->
            forms.sumOf { midCount[it] ?: 0 } >= 1 &&
                forms.sumOf { upperCount[it] ?: 0 } >= MIN_HITS
        }
        if (groups.isEmpty()) return emptyList()

        /** Melyik ragozott alak melyik szereplőhöz tartozik. */
        val formToName = HashMap<String, String>()
        for ((name, forms) in groups) for (f in forms) formToName[f] = name

        // ---- második menet: ki kicsoda, és kivel van
        val descriptors = HashMap<String, String>()
        val together = HashMap<String, HashMap<String, Int>>()

        for (i in 0..end) {
            val text = cut(paragraphs, i, end, toChar)
            if (text.isBlank()) continue
            val starts = Sentences.starts(text)

            val here = LinkedHashSet<String>()
            for ((from, to) in wordRanges(text)) {
                val name = formToName[text.substring(from, to)] ?: continue
                here.add(name)
                if (name !in descriptors) {
                    describe(text, starts, from, to, name, formToName)?.let {
                        descriptors[name] = it
                    }
                }
            }
            // Egy bekezdésben együtt szereplő nevek: mindenki mindenkivel
            for (a in here) {
                val row = together.getOrPut(a) { HashMap() }
                for (b in here) if (a != b) row[b] = (row[b] ?: 0) + 1
            }
        }

        val people = groups.map { (name, forms) ->
            val first = forms.mapNotNull { firstAt[it] }.minByOrNull { it.first }
            Person(
                name = name,
                count = forms.sumOf { upperCount[it] ?: 0 },
                firstParaIndex = first?.first ?: 0,
                firstSentence = first?.second.orEmpty(),
                descriptor = descriptors[name],
                companions = together[name].orEmpty()
                    .entries
                    .sortedByDescending { it.value }
                    .take(MAX_COMPANIONS)
                    .map { Companion(it.key, it.value) }
            )
        }
        return people.sortedWith(
            compareByDescending<Person> { it.count }.thenBy { it.name }
        ).take(limit)
    }

    // ------------------------------------------------------------- bemutatás

    /**
     * Mit mond a könyv erről a szereplőről ezen a helyen?
     *
     * Előbb a közbevetést próbáljuk (az a legjobb minőségű), utána a
     * kapcsolatszavas fordulatot.
     */
    private fun describe(
        text: String,
        starts: List<Int>,
        nameFrom: Int,
        nameTo: Int,
        self: String,
        formToName: Map<String, String>
    ): String? {
        val (sFrom, sTo) = sentenceBounds(text, starts, nameFrom)
        return apposition(text, nameTo, sTo) ?: relationPhrase(text, sFrom, sTo, self, formToName)
    }

    /**
     * Közbevetés: a név után közvetlenül vessző, majd a következő vesszőig
     * tartó rész. „Pista**, Jóska bátyja,** belépett."
     */
    internal fun apposition(text: String, nameTo: Int, sentenceEnd: Int): String? {
        var i = nameTo
        while (i < sentenceEnd && text[i] == ' ') i++
        if (i >= sentenceEnd || text[i] != ',') return null
        var j = i + 1
        while (j < sentenceEnd && text[j] == ' ') j++
        var k = j
        while (k < sentenceEnd && text[k] != ',') k++
        if (k >= sentenceEnd) return null

        val phrase = text.substring(j, k).trim()
        if (phrase.length < 4 || phrase.length > MAX_DESCRIPTOR) return null

        val words = phrase.split(' ', '\t').filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        if (words[0].lowercase().trimEnd(',', '.') in LIST_WORDS) return null

        // Egyetlen nagybetűs szó nem bemutatás, hanem felsorolás egy tagja:
        // „Pista, Jóska, és Elemér".
        val hasRelation = words.any { Relations.isRelationWord(it.trim(',', '.', '!', '?')) }
        val startsLower = phrase[0].isLowerCase()
        if (!hasRelation && !(startsLower && words.size >= 2)) return null

        return phrase
    }

    /**
     * Kapcsolatszavas fordulat: „Pista **Jóska bátyja** volt."
     *
     * A kapcsolatszó előtti legközelebbi nagybetűs szótól a kapcsolatszó
     * végéig idézünk. Ha a birtokos maga a szereplő volna („Pista bátyja"),
     * akkor a mondat nem róla szól, hanem a bátyjáról — olyankor kihagyjuk.
     */
    internal fun relationPhrase(
        text: String,
        sentenceStart: Int,
        sentenceEnd: Int,
        self: String,
        formToName: Map<String, String>
    ): String? {
        val words = wordRanges(text).filter { it.first >= sentenceStart && it.second <= sentenceEnd }
        for ((idx, range) in words.withIndex()) {
            val (from, to) = range
            if (!Relations.isRelationWord(text.substring(from, to))) continue

            // A legközelebbi nagybetűs szó visszafelé: ő a birtokos.
            for (k in idx - 1 downTo 0) {
                val (pf, pt) = words[k]
                val owner = text.substring(pf, pt)
                if (!owner[0].isUpperCase()) continue
                if (formToName[owner] == self) return null
                val phrase = text.substring(pf, to).trim()
                return if (phrase.length in 4..MAX_DESCRIPTOR) phrase else null
            }
        }
        return null
    }

    // ------------------------------------------------------------- segédek

    /** Az utolsó bekezdést a pozíciónál vágjuk el, a többit érintetlenül. */
    private fun cut(paragraphs: List<String>, index: Int, end: Int, toChar: Int): String {
        val full = paragraphs[index]
        return if (index == end) full.substring(0, toChar.coerceIn(0, full.length)) else full
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

    /** Annak a mondatnak a határai, amelyikben a [pos] karakter áll. */
    private fun sentenceBounds(text: String, starts: List<Int>, pos: Int): Pair<Int, Int> {
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
        return s to e
    }

    /** Az a mondat, amelyikben a [pos] karakter áll. */
    private fun sentenceAround(text: String, starts: List<Int>, pos: Int): String {
        val (s, e) = sentenceBounds(text, starts, pos)
        return text.substring(s, e).trim()
    }
}
