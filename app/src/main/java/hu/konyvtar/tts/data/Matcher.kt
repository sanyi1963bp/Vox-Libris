package hu.konyvtar.tts.data

import hu.konyvtar.tts.model.BookBrief
import hu.konyvtar.tts.model.MatchResult

/** Szöveg-normalizálás: kisbetű, ékezetek és írásjelek nélkül, szóközökkel. */
object Normalizer {

    private val zeroWidth = Regex("[\\u200B\\u200C\\u200D\\uFEFF\\u00AD]")
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    /** Láthatatlan karakterek eltávolítása (a moly-os címekben U+200B is van). */
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

    /**
     * Gyors, hossztartó magyar ékezet-összevonás kereséshez
     * (á→a, ő→o…). Mivel a hossz nem változik, a találat pozíciója
     * az eredeti szövegben is pontos — kiemeléshez használható.
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

    /** Token-halmazok hasonlósága (0..1) — a névsorrendre érzéketlen. */
    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        return inter.toDouble() / union.toDouble()
    }
}

/**
 * Fájlnév -> katalógusbeli könyv párosítás.
 *
 * Sorrend:
 *  1. pontos fájlnév a fizikai_fajlok táblából (a PC-n már elvégzett munka)
 *  2. normalizált fájlnév ugyanonnan
 *  3. fájlnévből kinyert cím+szerző a konyvek tábla ellen
 */
class Matcher(
    private val exactNameIndex: HashMap<String, Long>,
    private val normNameIndex: HashMap<String, Long>,
    books: List<BookBrief>
) {

    private val titleMap = HashMap<String, MutableList<BookBrief>>(books.size * 2)
    private val authorTitleMap = HashMap<String, Long>(books.size * 2)

    init {
        for (b in books) {
            val nt = Normalizer.norm(b.cim)
            if (nt.isEmpty()) continue
            titleMap.getOrPut(nt) { ArrayList(1) }.add(b)
            val na = Normalizer.norm(b.szerzo)
            if (na.isNotEmpty()) {
                authorTitleMap[sortedKey(na, nt)] = b.id
            }
        }
    }

    private fun sortedKey(author: String, title: String): String {
        // A szerző tokenjeit ábécébe rendezzük, így "Trux Béla" és "Béla Trux" ugyanaz.
        val a = author.split(' ').filter { it.isNotEmpty() }.sorted().joinToString(" ")
        return "$a|$title"
    }

    /** Zárójeles "szemét" csoportok, amiket a fájlnevekből ki kell dobni. */
    private fun isJunkGroup(g: String): Boolean {
        val l = g.lowercase()
        if (l.isBlank()) return true
        if (Regex("^\\d{1,4}$").matches(l.trim())) return true // évszám vagy sorszám
        val junkWords = listOf(
            "z-library", "zlibrary", "z-lib", "1lib", "lib.sk", "annas", "anna's",
            "libgen", "ncore", "hunebook", "ebook", "epub", "mobi", "pdf",
            "olvas", "javított", "javitott", "szerk", "vágatlan", "vagatlan", "scan", "ocr"
        )
        return junkWords.any { l.contains(it) }
    }

    private val parenRegex = Regex("\\(([^()]*)\\)|\\[([^\\[\\]]*)\\]")
    private val leadingNumber = Regex("^\\s*\\d{1,3}[.\\-_ ]+\\s*")

    /**
     * A fő párosító. A [fileName] a kiterjesztéssel együtt érkezik.
     */
    fun match(fileName: String): MatchResult? {
        val lower = fileName.lowercase()

        // 1) Pontos fájlnév-egyezés a PC-s párosításból
        exactNameIndex[lower]?.let { return MatchResult(it, "fajlnev") }

        val base = fileName.substringBeforeLast('.')

        // 2) Normalizált fájlnév-egyezés
        val normBase = Normalizer.norm(base)
        if (normBase.isNotEmpty()) {
            normNameIndex[normBase]?.let { return MatchResult(it, "fajlnev~") }
        }

        // 3) Cím + szerző kinyerése a fájlnévből
        val groups = ArrayList<String>()
        var remainder = base
        parenRegex.findAll(base).forEach { m ->
            val g = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (g.isNotEmpty()) groups.add(g)
        }
        remainder = parenRegex.replace(remainder, " ").trim()

        val authorCandidates = groups.filter { !isJunkGroup(it) }

        // 3/a: "Szerző - Cím" vagy "Cím - Szerző" minta
        val dashIdx = remainder.indexOf(" - ")
        if (dashIdx > 0) {
            val left = remainder.substring(0, dashIdx).trim()
            val right = remainder.substring(dashIdx + 3).trim()
            tryAuthorTitle(left, right)?.let { return it }
            tryAuthorTitle(right, left)?.let { return it }
        }

        // 3/b: "Cím (Szerző)" minta
        for (author in authorCandidates) {
            tryAuthorTitle(author, remainder)?.let { return it }
            // vezető sorszám nélkül is ("2. Akkon ostroma")
            val stripped = leadingNumber.replace(remainder, "")
            if (stripped != remainder) {
                tryAuthorTitle(author, stripped)?.let { return it }
            }
        }

        // 3/c: csak cím — akkor fogadjuk el, ha a katalógusban egyértelmű
        tryTitleOnly(remainder)?.let { return it }
        val stripped = leadingNumber.replace(remainder, "")
        if (stripped != remainder) {
            tryTitleOnly(stripped)?.let { return it }
        }

        return null
    }

    private fun tryAuthorTitle(author: String, title: String): MatchResult? {
        val na = Normalizer.norm(author)
        val nt = Normalizer.norm(title)
        if (na.isEmpty() || nt.isEmpty()) return null
        authorTitleMap[sortedKey(na, nt)]?.let { return MatchResult(it, "cim+szerzo") }
        // Cím szerint keresünk, szerzőt hasonlósággal ellenőrizzük
        val list = titleMap[nt] ?: return null
        var best: BookBrief? = null
        var bestSim = 0.0
        for (b in list) {
            val sim = Normalizer.similarity(author, b.szerzo)
            if (sim > bestSim) {
                bestSim = sim
                best = b
            }
        }
        if (best != null && bestSim >= 0.5) return MatchResult(best.id, "cim+szerzo")
        return null
    }

    private fun tryTitleOnly(title: String): MatchResult? {
        val nt = Normalizer.norm(title)
        if (nt.length < 8) return null // túl rövid cím önmagában nem megbízható
        val list = titleMap[nt] ?: return null
        if (list.size == 1) return MatchResult(list[0].id, "cim")
        return null
    }
}
