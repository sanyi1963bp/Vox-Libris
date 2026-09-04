package hu.konyvtar.tts.reader

import hu.konyvtar.tts.data.Normalizer
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * „Hol voltam?" — visszahelyezés a szövegbe.
 *
 * Nem összefoglaló és nem meséli el a történetet: a legutóbb hallgatott
 * részből kiválasztja azt a néhány mondatot, amelyik a legjellemzőbb rá, és
 * eredeti sorrendben mutatja. Aki a szemével olvas, visszalapoz egy oldalt;
 * aki hallgat, nem tud — ez pótolja azt.
 *
 * **Sosem néz előre.** A vizsgált szakasz az olvasási pozíciódnál véget ér,
 * tehát nem árulhat el olyat, amit még nem hallottál. Ez nem apróság: a
 * fejezet hátralévő része spoiler volna.
 *
 * A mondatok pontozása szótár nélkül működik, tehát **nyelvfüggetlen**. Nincs
 * beépített kötőszólista, amit karban kellene tartani tíz nyelvre; a szöveg
 * maga árulja el, mely szavak jelentés nélküliek: amelyik szó majdnem minden
 * bekezdésben ott van, az önmagában nem mond semmit.
 */
object Recap {

    /** Egy kiemelt mondat és a bekezdés, ahonnan való. */
    data class Line(val paraIndex: Int, val text: String)

    /** Ennél régebbre nem megyünk vissza — a „hol voltam" nem a fél könyv. */
    const val WINDOW_PARAS = 40

    /** Ennél kevesebb szóból álló mondat nem visz vissza sehova. */
    private const val MIN_WORDS = 4

    /**
     * A visszahelyező mondatok.
     *
     * @param paragraphs a könyv bekezdései
     * @param toPara az olvasási pozíció bekezdése (ezt még beleszámoljuk)
     * @param toChar a pozíció karaktere ezen a bekezdésen belül
     * @param count hány mondatot kérünk
     */
    fun of(
        paragraphs: List<String>,
        toPara: Int,
        toChar: Int,
        count: Int = 4
    ): List<Line> {
        if (paragraphs.isEmpty() || count <= 0) return emptyList()
        val end = toPara.coerceIn(0, paragraphs.size - 1)

        // A szakasz, amiről a felidézés szól: a pozíció előtti bekezdések.
        // Szándékosan nem fejezethez kötjük — sok formátumban (PDF, TXT) a
        // fejezethatár bizonytalan, a „mi történt az imént" viszont mindig
        // értelmes kérdés.
        val from = (end - WINDOW_PARAS + 1).coerceAtLeast(0)

        val corpus = readSoFar(paragraphs, end, toChar)
        if (corpus.isEmpty()) return emptyList()

        // A szakasz mondatai, bekezdésszámmal együtt
        val lines = ArrayList<Line>()
        for (i in from..end) {
            val text = cutAt(paragraphs[i], i, end, toChar)
            for (s in split(text)) lines.add(Line(i, s))
        }
        if (lines.isEmpty()) return emptyList()

        val tf = HashMap<String, Int>()
        for (l in lines) for (w in tokens(l.text)) tf[w] = (tf[w] ?: 0) + 1
        if (tf.isEmpty()) return emptyList()

        // Hány eddig olvasott bekezdésben fordul elő az adott szó? Amelyik
        // majdnem mindegyikben, az kötőszó vagy névelő — a súlya nullához tart.
        val df = HashMap<String, Int>()
        for (p in corpus) {
            for (w in tokens(p).toHashSet()) {
                if (tf.containsKey(w)) df[w] = (df[w] ?: 0) + 1
            }
        }
        // A súly a klasszikus TF-IDF: a szó gyakorisága a szakaszban, szorozva
        // azzal, mennyire ritka az eddig olvasott szövegben.
        //
        // A képlet lényege, hogy ami MINDEN bekezdésben ott van, az pontosan
        // nulla súlyt kap: ln(n/n) = 0. A névelő és a kötőszó így magától
        // esik ki, szótár nélkül, bármelyik nyelven. Ha ide bármi hozzáadódna
        // (mondjuk +1 a logaritmuson belül), a jelentés nélküli szavak is
        // súlyt kapnának — és mivel azok a leggyakoribbak, a legunalmasabb
        // mondat nyerne.
        val n = corpus.size.toDouble()
        val weight = HashMap<String, Double>(tf.size)
        for ((w, f) in tf) {
            val d = (df[w] ?: 1).coerceIn(1, corpus.size.coerceAtLeast(1))
            weight[w] = f * ln(n / d).coerceAtLeast(0.0)
        }

        // Egy mondat annyit ér, amennyi jellemző szót tartalmaz. A hosszal
        // osztunk, különben mindig a leghosszabb mondat nyerne.
        val scored = lines.mapIndexedNotNull { idx, line ->
            val words = tokens(line.text)
            if (words.size < MIN_WORDS) return@mapIndexedNotNull null
            val sum = words.toHashSet().sumOf { weight[it] ?: 0.0 }
            idx to sum / sqrt(words.size.toDouble())
        }
        if (scored.isEmpty()) return emptyList()

        // A legjobbak, de eredeti sorrendben visszaadva: a felidézés akkor
        // segít, ha úgy olvasható, ahogy a könyvben állt.
        return scored.sortedByDescending { it.second }
            .take(count)
            .map { it.first }
            .sorted()
            .map { lines[it] }
    }

    /** Az eddig olvasott bekezdések, az utolsó a pozíciónál elvágva. */
    private fun readSoFar(paragraphs: List<String>, end: Int, toChar: Int): List<String> {
        val out = ArrayList<String>(end + 1)
        for (i in 0..end) {
            val t = cutAt(paragraphs[i], i, end, toChar)
            if (t.isNotBlank()) out.add(t)
        }
        return out
    }

    /** Az utolsó bekezdést a pozíciónál vágjuk el, a többit érintetlenül hagyjuk. */
    private fun cutAt(text: String, index: Int, end: Int, toChar: Int): String =
        if (index == end) text.substring(0, toChar.coerceIn(0, text.length)) else text

    /** Mondatokra bontás a meglévő mondathatár-felismerővel. */
    private fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val starts = Sentences.starts(text)
        val out = ArrayList<String>(starts.size)
        for (i in starts.indices) {
            val s = starts[i]
            val e = if (i + 1 < starts.size) starts[i + 1] else text.length
            val piece = text.substring(s, e).trim()
            if (piece.isNotEmpty()) out.add(piece)
        }
        return out
    }

    /**
     * Szavak összehasonlítható alakban: ékezet nélkül, kisbetűsen. A három
     * betűnél rövidebbeket és a számokat elhagyjuk — azok se nem jellemzőek,
     * se nem emlékeztetnek semmire.
     */
    internal fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.length >= 3) {
                val w = Normalizer.foldHu(sb.toString()).lowercase()
                if (w.any { it.isLetter() }) out.add(w)
            }
            sb.setLength(0)
        }
        for (c in text) {
            if (c.isLetterOrDigit()) sb.append(c) else flush()
        }
        flush()
        return out
    }
}
