package hu.konyvtar.tts

import hu.konyvtar.tts.reader.Characters
import hu.konyvtar.tts.reader.Recap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A 4. fázis próbái: „Hol voltam?" és karakternévtár.
 *
 * A legfontosabb, amit itt őrzünk, nem a találati pontosság, hanem a
 * **spoilermentesség**: egyik funkció sem nézhet előre az olvasási
 * pozíciónál. Ha az elromlik, az nem hibaüzenetként jelenik meg, hanem úgy,
 * hogy elárulja a könyv végét — és azt már nem lehet visszacsinálni.
 */
class RecapTest {

    private val book = listOf(
        "A hajó kikötött. A kapitány partra szállt, és elindult a piac felé.",
        "A piacon hangos volt a tömeg. Kereskedők kiabáltak, gyerekek szaladgáltak.",
        "A kapitány megvette a térképet. A térkép egy szigetet mutatott.",
        "Este visszatért a hajóra. A legénység már aludt.",
        "Reggel kifutottak a szigethez. A sziget üres volt, csak a torony állt rajta."
    )

    @Test
    fun `a felidezes nem nez tul az olvasasi pozicion`() {
        // A negyedik bekezdésnél tartunk: a toronyról még nem tudhatunk.
        val lines = Recap.of(book, toPara = 3, toChar = book[3].length, count = 4)
        assertTrue(lines.isNotEmpty())
        for (l in lines) {
            assertTrue("a pozíción túlról idéz: ${l.text}", l.paraIndex <= 3)
            assertFalse("spoiler szivárgott be", l.text.contains("torony"))
        }
    }

    @Test
    fun `a bekezdesen belul is megall a pozicional`() {
        // Az utolsó bekezdés első mondatáig olvastunk csak.
        val cut = book[4].indexOf("A sziget")
        val lines = Recap.of(book, toPara = 4, toChar = cut, count = 5)
        for (l in lines) {
            assertFalse("a bekezdés hátralévő része spoiler", l.text.contains("torony"))
        }
    }

    @Test
    fun `a mondatok eredeti sorrendben jonnek vissza`() {
        val lines = Recap.of(book, toPara = 4, toChar = book[4].length, count = 4)
        val indices = lines.map { it.paraIndex }
        assertEquals(indices.sorted(), indices)
    }

    @Test
    fun `nem ad vissza tobbet a kertnel`() {
        assertTrue(Recap.of(book, 4, book[4].length, count = 2).size <= 2)
    }

    @Test
    fun `ures konyv nem szall el`() {
        assertTrue(Recap.of(emptyList(), 0, 0).isEmpty())
        assertTrue(Recap.of(listOf(""), 0, 0).isEmpty())
    }

    @Test
    fun `a hatarokon tuli pozicio sem szall el`() {
        assertTrue(Recap.of(book, toPara = 999, toChar = 999, count = 3).isNotEmpty())
        assertTrue(Recap.of(book, toPara = -5, toChar = -5, count = 3).isEmpty())
    }

    @Test
    fun `a tul rovid mondatokat kihagyja`() {
        val short = listOf("Igen. Nem. Talán. Persze.")
        assertTrue(Recap.of(short, 0, short[0].length).isEmpty())
    }

    @Test
    fun `a mindenhol elofordulo szo nem huz sulyt`() {
        // A "hajó" minden bekezdésben ott van, tehát nem jellemző egyikre sem;
        // a "sárkány" egyetlen bekezdésben, tehát az annál inkább.
        val paras = List(9) { "A hajó ringott a vízen csendesen tovább." } +
            listOf("A hajó fedélzetén megjelent a sárkány, és mindenki elnémult.")
        val lines = Recap.of(paras, toPara = 9, toChar = paras[9].length, count = 1)
        assertEquals(1, lines.size)
        assertTrue("nem a jellemző mondatot hozta: ${lines[0].text}",
            lines[0].text.contains("sárkány"))
    }
}

class CharactersTest {

    private val book = listOf(
        "Gandalf megérkezett a faluba. A gyerekek körülvették Gandalfot.",
        "Frodó kinyitotta az ajtót. Gandalf belépett, és leült Frodóval szemben.",
        "A kert csendes volt. Frodó teát főzött Gandalfnak.",
        "Szarumán a toronyban várakozott. Szarumán tudta, hogy Gandalf közeleg."
    )

    @Test
    fun `megtalalja a szereploket`() {
        val people = Characters.find(book, toPara = 3, toChar = book[3].length)
        val names = people.map { it.name }
        assertTrue("Gandalf hiányzik: $names", names.contains("Gandalf"))
        assertTrue("Frodó hiányzik: $names", names.contains("Frodó"))
    }

    @Test
    fun `a ragozott alakokat egy nev ala vonja`() {
        val people = Characters.find(book, toPara = 3, toChar = book[3].length)
        val gandalf = people.first { it.name == "Gandalf" }
        // Gandalf, Gandalfot, Gandalfnak, és még kétszer Gandalf
        assertTrue("a ragozott alakok nem olvadtak össze: ${gandalf.count}", gandalf.count >= 4)
        assertFalse(people.any { it.name == "Gandalfot" })
        assertFalse(people.any { it.name == "Gandalfnak" })
    }

    @Test
    fun `nem nez tul az olvasasi pozicion`() {
        // Az első két bekezdésig olvastunk — Szarumán még nem létezik nekünk.
        val people = Characters.find(book, toPara = 1, toChar = book[1].length)
        assertFalse("spoiler: ${people.map { it.name }}", people.any { it.name == "Szarumán" })
    }

    @Test
    fun `gyakorisag szerint rendez`() {
        val people = Characters.find(book, toPara = 3, toChar = book[3].length)
        for (i in 1 until people.size) {
            assertTrue(people[i - 1].count >= people[i].count)
        }
    }

    @Test
    fun `az elso elofordulas mondatat adja`() {
        val people = Characters.find(book, toPara = 3, toChar = book[3].length)
        val gandalf = people.first { it.name == "Gandalf" }
        assertEquals(0, gandalf.firstParaIndex)
        assertTrue(gandalf.firstSentence.contains("megérkezett"))
    }

    @Test
    fun `a mondatkezdo koznevet nem veszi szereplonek`() {
        // "Este" és "Reggel" mindig mondat elején állnak — nem nevek.
        val paras = listOf(
            "Este hazamentem. Reggel felkeltem.",
            "Este esett az eső. Reggel sütött a nap.",
            "Este olvastam. Reggel dolgoztam."
        )
        val names = Characters.find(paras, 2, paras[2].length).map { it.name }
        assertFalse("mondatkezdő szót vett névnek: $names", names.contains("Este"))
        assertFalse("mondatkezdő szót vett névnek: $names", names.contains("Reggel"))
    }

    @Test
    fun `a csupa nagybetus szo nem nev`() {
        val paras = List(3) { "A felirat azt mondta VIGYÁZAT, és mindenki megállt VIGYÁZAT előtt." }
        val names = Characters.find(paras, 2, paras[2].length).map { it.name }
        assertFalse("kiabálást vett névnek: $names", names.contains("VIGYÁZAT"))
    }

    @Test
    fun `egyetlen elofordulas nem eleg`() {
        val paras = listOf("Egyszer láttam Bélát a boltban, aztán soha többé.")
        assertTrue(Characters.find(paras, 0, paras[0].length).isEmpty())
    }

    @Test
    fun `a tul hosszu toldalek nem olvad ossze`() {
        // Az "Anna" és az "Annamária" két külön ember.
        val groups = Characters.group(listOf("Anna", "Annamária", "Annát"))
        assertTrue(groups.containsKey("Anna"))
        assertTrue(groups.containsKey("Annamária"))
        assertTrue(groups.getValue("Anna").contains("Annát"))
    }

    @Test
    fun `ures konyv nem szall el`() {
        assertTrue(Characters.find(emptyList(), 0, 0).isEmpty())
        assertTrue(Characters.find(listOf(""), 0, 0).isEmpty())
    }
}
