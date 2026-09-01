package hu.konyvtar.tts

import hu.konyvtar.tts.data.Pronounce
import hu.konyvtar.tts.reader.Bionic
import hu.konyvtar.tts.reader.Sentences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 3. fázis próbái: kiejtési szótár, mondathatárok a műveletmenühöz,
 * szóválasztás, bionic szedés.
 *
 * Mindegyik olyan rész, ami hiba esetén nem omlik össze, csak *rosszul*
 * viselkedik: egy név félremondva, egy menü rossz mondatot mutatva. Ezért
 * érdemelnek tesztet.
 */
class PronounceTest {

    private fun rules(vararg pairs: Pair<String, String>): List<Pronounce.Rule> =
        pairs.mapIndexed { i, (p, s) -> Pronounce.Rule(i.toLong(), p, s) }

    @Test
    fun `szabaly nelkul a szoveg valtozatlan`() {
        val text = "Bree felé indultak."
        assertEquals(text, Pronounce.apply(text, emptyList()))
    }

    @Test
    fun `a mintat a megadott kiejtesre csereli`() {
        assertEquals(
            "Brí felé indultak.",
            Pronounce.apply("Bree felé indultak.", rules("Bree" to "Brí"))
        )
    }

    @Test
    fun `a magyar rag a helyen marad`() {
        // Ez a lényeg: csak a szó eleje cserélődik, a toldalék marad.
        assertEquals(
            "Bríben, Bríből, Brível.",
            Pronounce.apply("Breeben, Breeből, Breevel.", rules("Bree" to "Brí"))
        )
    }

    @Test
    fun `kis- es nagybetu kozott nem tesz kulonbseget`() {
        assertEquals(
            "Brí és Brí.",
            Pronounce.apply("Bree és bree.", rules("BREE" to "Brí"))
        )
    }

    @Test
    fun `a szo kozepebe nem kap bele`() {
        // A "ree" minta nem törhet bele a "Bree" közepébe.
        assertEquals("Bree", Pronounce.apply("Bree", rules("ree" to "XXX")))
    }

    @Test
    fun `a hosszabb minta nyer`() {
        // Ha a rövidebb nyerne, a Bregóból "Xgo" lenne.
        val r = rules("Bre" to "X", "Brego" to "Bregó")
        assertEquals("Bregó", Pronounce.apply("Brego", r))
    }

    @Test
    fun `a csere nem lancolodik`() {
        // Amit az első szabály beírt, azt a második már nem alakítja tovább —
        // különben egy ártatlan szótárpár végtelen meglepetéseket okozna.
        val r = rules("alma" to "korte", "korte" to "szilva")
        assertEquals("korte", Pronounce.apply("alma", r))
    }

    @Test
    fun `az ures mintat figyelmen kivul hagyja`() {
        assertEquals("alma", Pronounce.apply("alma", rules("   " to "X")))
    }

    @Test
    fun `tobb elofordulast is cserel`() {
        assertEquals(
            "Brí, majd Brí, végül Brí.",
            Pronounce.apply("Bree, majd Bree, végül Bree.", rules("Bree" to "Brí"))
        )
    }
}

class SentenceBoundsTest {

    @Test
    fun `az elso mondat hatarai`() {
        val t = "Első mondat. Második mondat."
        val (s, e) = Sentences.boundsAt(t, 3)
        assertEquals(0, s)
        assertEquals("Első mondat. ", t.substring(s, e))
    }

    @Test
    fun `a masodik mondat hatarai`() {
        val t = "Első mondat. Második mondat."
        val (s, e) = Sentences.boundsAt(t, 20)
        assertEquals("Második mondat.", t.substring(s, e))
    }

    @Test
    fun `egyetlen mondatnal az egesz szoveg jon vissza`() {
        val t = "Csak ennyi."
        val (s, e) = Sentences.boundsAt(t, 5)
        assertEquals(t, t.substring(s, e))
    }

    @Test
    fun `a hatarokon tuli offset sem szall el`() {
        val t = "Valami."
        assertEquals(t, t.substring(Sentences.boundsAt(t, -99).first, Sentences.boundsAt(t, -99).second))
        assertEquals(t, t.substring(Sentences.boundsAt(t, 999).first, Sentences.boundsAt(t, 999).second))
    }

    @Test
    fun `ures szoveg nem okoz kivetelt`() {
        assertEquals(0 to 0, Sentences.boundsAt("", 0))
    }
}

class WordsTest {

    @Test
    fun `a szavakat sorrendben adja vissza`() {
        assertEquals(
            listOf("Gandalf", "megérkezett", "Brího"),
            Sentences.words("Gandalf megérkezett Brího.")
        )
    }

    @Test
    fun `az ismetlodo szavakat csak egyszer kinalja`() {
        assertEquals(listOf("alma", "korte"), Sentences.words("alma korte Alma ALMA korte"))
    }

    @Test
    fun `az egybetus darabokat kihagyja`() {
        // A névelő és a kötőszó nem érdekes sem kiejtésnek, sem Wikipédiának.
        assertEquals(listOf("kutya", "macska"), Sentences.words("a kutya s macska"))
    }

    @Test
    fun `a kotojel elvalaszt`() {
        assertEquals(listOf("Bree", "nek"), Sentences.words("Bree-nek"))
    }

    @Test
    fun `ures szovegbol ures lista`() {
        assertTrue(Sentences.words("   ...   ").isEmpty())
    }
}

class BionicTest {

    private fun bolded(text: String): String {
        val sb = StringBuilder(text)
        // Hátulról befelé szúrunk, hogy az indexek ne csússzanak el.
        for ((s, e) in Bionic.boldRanges(text).reversed()) {
            sb.insert(e, ']')
            sb.insert(s, '[')
        }
        return sb.toString()
    }

    @Test
    fun `a szo elejet emeli ki`() {
        // Hét betű 40%-a 2,8 — vagyis három betű lesz félkövér.
        assertEquals("[olv]asás", bolded("olvasás"))
        assertEquals("[ol]vas", bolded("olvas"))
    }

    @Test
    fun `minden szobol marad normal betu is`() {
        // Kiemelés csak ott van, ahol van mihez képest.
        for (w in listOf("a", "az", "egy", "hosszabbacska")) {
            val r = Bionic.boldRanges(w)
            assertEquals(1, r.size)
            val (s, e) = r[0]
            assertTrue("$w: a kiemelés nem lehet üres", e > s)
            if (w.length > 1) {
                assertTrue("$w: nem maradt normál betű", e < w.length)
            }
        }
    }

    @Test
    fun `az irasjelek elvalasztjak a szavakat`() {
        assertEquals("[Sz]ia, [vi]lág!", bolded("Szia, világ!"))
    }

    @Test
    fun `ures szovegre nincs kiemeles`() {
        assertTrue(Bionic.boldRanges("").isEmpty())
        assertTrue(Bionic.boldRanges("  ...  ").isEmpty())
    }

    @Test
    fun `a tartomanyok nem fedik at egymast es sorrendben allnak`() {
        val text = "A magyar nyelv ragozó nyelv, ezért a szótő elöl van."
        var prevEnd = -1
        for ((s, e) in Bionic.boldRanges(text)) {
            assertTrue("átfedő vagy fordított tartomány", s > prevEnd)
            assertTrue(e > s)
            assertTrue(e <= text.length)
            prevEnd = e
        }
    }
}
