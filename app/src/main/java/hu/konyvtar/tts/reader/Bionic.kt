package hu.konyvtar.tts.reader

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Bionic Reading: minden szó elejét félkövéren szedjük.
 *
 * Az ötlet mögötti megfigyelés az, hogy olvasás közben a szem nem betűzi ki a
 * szavakat, hanem a szókezdetből találja ki őket. Ha a szókezdet kiemelkedik,
 * a tekintet kapaszkodót kap, és nem kell minden szón megállnia.
 *
 * Ebben az appban van egy külön haszna is: aki a felolvasást a szemével
 * követi, annak a hang és a szöveg összetartása lesz könnyebb.
 *
 * Ez a fájl szándékosan nem függ az Androidtól — csak a kiemelendő szakaszok
 * határait számolja ki, a megjelenítés a [hu.konyvtar.tts.ui.ReaderText]
 * dolga. Így a szabály egységteszttel ellenőrizhető.
 */
object Bionic {

    /** A szó elejéből ekkora rész lesz félkövér. */
    const val DEFAULT_FRACTION = 0.4f

    /**
     * A félkövéren szedendő szakaszok `[kezdet, vég)` párokként.
     *
     * Szónak a betűk és számjegyek összefüggő futamát tekintjük, így az
     * írásjelek, a szóközök és a kötőjelek elválasztanak. A magyar ragozásnak
     * ez kedvez: a rag a szó végén van, a kiemelés pedig az elején marad,
     * vagyis épp a szótő emelkedik ki.
     *
     * Egy betűnél rövidebb kiemelés nincs, és a szóból mindig marad legalább
     * egy betű normál szedéssel — kiemelés csak ott van, ahol van mihez képest.
     */
    fun boldRanges(text: String, fraction: Float = DEFAULT_FRACTION): List<Pair<Int, Int>> {
        if (text.isEmpty()) return emptyList()
        val f = fraction.coerceIn(0.1f, 0.9f)
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        val n = text.length
        while (i < n) {
            if (!text[i].isLetterOrDigit()) {
                i++
                continue
            }
            val start = i
            while (i < n && text[i].isLetterOrDigit()) i++
            val len = i - start
            // Legalább egy betű félkövér, de a szó végéből is maradjon valami.
            val bold = min(max(1, (len * f).roundToInt()), max(1, len - 1))
            out.add(start to start + bold)
        }
        return out
    }
}
