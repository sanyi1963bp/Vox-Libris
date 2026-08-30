package hu.konyvtar.tts

import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.reader.HtmlText
import hu.konyvtar.tts.reader.Sentences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A szövegkezelés próbái.
 *
 * Ezek a részek Android nélkül futnak, és pont itt a legveszélyesebb a hiba:
 * nem omlik össze semmi, csak rosszul lesz felolvasva egy könyv — olyasmi,
 * amit hetekkel később, egy fejezet közepén venne észre az ember.
 */
class NormalizerTest {

    @Test
    fun `az ekezetek elhagyasa nem valtoztat a nem ekezetes szovegen`() {
        assertEquals("konyv", Normalizer.foldAll("konyv"))
    }

    @Test
    fun `a keresokulcs ekezet nelkul is illeszkedik`() {
        assertEquals("jozsef attila", Normalizer.foldAll("József Attila"))
        assertEquals("orokosok", Normalizer.foldAll("Örökösök"))
        assertEquals("tuzkeresztseg", Normalizer.foldAll("Tűzkeresztség"))
    }

    @Test
    fun `a foldAll mas nyelvek ekezeteit is kezeli`() {
        assertEquals("caslav", Normalizer.foldAll("Čáslav"))
        assertEquals("munoz", Normalizer.foldAll("Muñoz"))
    }

    @Test
    fun `a nemet eszett nem bomlik ket s-re`() {
        // A ligatúrákat az unicode-bontás nem nyitja szét: a "Große"
        // keresésnél nem található meg "grosse" begépelésével. Tudott
        // korlát, nem hiba — azért rögzítjük, hogy ne változzon észrevétlenül.
        assertEquals("große", Normalizer.foldAll("Große"))
    }

    @Test
    fun `a foldAll a nem latin irast meghagyja`() {
        // A cirill й unicode szerint и + rövidítőjel, ezért a folding и-t hagy.
        // Keresésre ez jó: a "voina" és a "vojna" is megtalálja ugyanazt.
        assertEquals("воина", Normalizer.foldAll("Война"))
        assertEquals("книга", Normalizer.foldAll("Книга"))
    }

    @Test
    fun `a lathatatlan karakterek eltunnek`() {
        assertEquals("Cim", Normalizer.stripInvisible("C​im"))
        assertEquals("cim", Normalizer.foldAll("C﻿im"))
    }

    // ---------------------------------------------------------------- betűsáv

    @Test
    fun `a kezdobetu ekezet nelkul, nagybetuvel jon vissza`() {
        assertEquals("A", Normalizer.letterOf("Álom"))
        assertEquals("O", Normalizer.letterOf("Őrjárat"))
        assertEquals("U", Normalizer.letterOf("Űrhajó"))
        assertEquals("E", Normalizer.letterOf("egy kisregény"))
    }

    @Test
    fun `a szammal kezdodo cim a kettoskereszt csoportba kerul`() {
        assertEquals("#", Normalizer.letterOf("2001 Űrodüsszeia"))
        assertEquals("#", Normalizer.letterOf("1984"))
    }

    @Test
    fun `az irasjelet atlepi, az ures szoveg kettoskereszt`() {
        assertEquals("A", Normalizer.letterOf("...Aranyember"))
        assertEquals("H", Normalizer.letterOf("„Ha”"))
        assertEquals("#", Normalizer.letterOf(""))
        assertEquals("#", Normalizer.letterOf("   "))
    }

    @Test
    fun `a cirill kezdobetu megmarad`() {
        assertEquals("В", Normalizer.letterOf("Война és béke"))
    }

    // ---------------------------------------------------------------- párosítás

    @Test
    fun `a hasonlosag nem fugg a nevsorrendtol`() {
        val a = Normalizer.similarity("Rejtő Jenő", "Jenő Rejtő")
        assertEquals(1.0, a, 0.001)
    }

    @Test
    fun `a teljesen mas cimek hasonlosaga alacsony`() {
        assertTrue(Normalizer.similarity("A kőszívű ember fiai", "Csillagok háborúja") < 0.2)
    }
}

class SentencesTest {

    @Test
    fun `az elso mondat mindig a nulladik karakternel kezdodik`() {
        assertEquals(listOf(0), Sentences.starts("Egyetlen mondat pont nélkül"))
    }

    @Test
    fun `a mondatvegi pont utan uj mondat kezdodik`() {
        val t = "Első mondat. Második mondat. Harmadik."
        assertEquals(listOf(0, 13, 29), Sentences.starts(t))
    }

    @Test
    fun `a felkialtojel es a kerdojel is mondatvege`() {
        val t = "Hova mész? Haza! Jó."
        assertEquals(3, Sentences.starts(t).size)
    }

    @Test
    fun `a rovidites nem tevesztheti meg`() {
        // A "dr." után kisbetű jön, tehát nem mondathatár.
        val t = "Ezt dr. kovács mondta el nekünk."
        assertEquals(listOf(0), Sentences.starts(t))
    }

    @Test
    fun `a zaro idezojel utan is felismeri a mondathatart`() {
        val t = "„Menj haza.” Aztán elment."
        assertTrue(Sentences.starts(t).size >= 2)
    }

    @Test
    fun `a startAt a mondat elejere igazit`() {
        val t = "Első mondat. Második mondat."
        assertEquals(0, Sentences.startAt(t, 5))
        assertEquals(13, Sentences.startAt(t, 20))
        // A szöveg végén túl is a helyes mondatot adja
        assertEquals(13, Sentences.startAt(t, 9999))
        assertEquals(0, Sentences.startAt(t, -5))
    }
}

class HtmlTextTest {

    @Test
    fun `a bekezdesek kulon sorokra bomlanak`() {
        val out = HtmlText.toParagraphs("<p>Első</p><p>Második</p>")
        assertEquals(listOf("Első", "Második"), out)
    }

    @Test
    fun `a cimsor fejezetjelolest kap`() {
        val out = HtmlText.toParagraphs("<h1>Első fejezet</h1><p>Szöveg</p>")
        assertEquals("\u0001Első fejezet", out[0])
        assertEquals("Szöveg", out[1])
    }

    @Test
    fun `a szkript es a stilus nem kerul a szovegbe`() {
        val out = HtmlText.toParagraphs(
            "<style>p{color:red}</style><script>alert(1)</script><p>Csak ez</p>"
        )
        assertEquals(listOf("Csak ez"), out)
    }

    @Test
    fun `az entitasok feloldodnak`() {
        assertEquals("Ő & ő", HtmlText.decodeEntities("&Otilde; &amp; &otilde;"))
        assertEquals("őű", HtmlText.decodeEntities("&odblac;&udblac;"))
        assertEquals("A", HtmlText.decodeEntities("&#65;"))
        assertEquals("A", HtmlText.decodeEntities("&#x41;"))
    }

    @Test
    fun `az ismeretlen entitas valtozatlan marad`() {
        assertEquals("&nincsilyen;", HtmlText.decodeEntities("&nincsilyen;"))
    }

    @Test
    fun `a sortoresbol uj bekezdes lesz`() {
        val out = HtmlText.toParagraphs("Egy<br>Kettő")
        assertEquals(listOf("Egy", "Kettő"), out)
    }
}
