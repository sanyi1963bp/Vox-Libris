package hu.konyvtar.tts

import hu.konyvtar.tts.data.CharacterNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A könyv mellől beolvasott szereplőleírások.
 *
 * A kísérőfájlt egy PC-n futó nyelvi modell írja, tehát **nem bízhatunk a
 * tartalmában**: lehet hiányos mező, rossz típus, csonka lista. Ezek a
 * próbák azt őrzik, hogy egy félresikerült fájltól ne essen szét a
 * szereplőlista — a rosszabb eset itt nem a hibaüzenet, hanem a néma
 * összeomlás olvasás közben.
 */
class CharacterNotesTest {

    private val json = """
        {
          "format": 1,
          "book": "A király",
          "spoilers": true,
          "characters": [
            {
              "name": "Jakub Szapiro",
              "aliases": ["Szapiro", "Jakub"],
              "description": "Zsidó bokszoló, Kaplica bandájának verőembere."
            },
            {
              "name": "Ryfka Kij",
              "description": "Bordélytulajdonos, Szapiro szeretője."
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a teljes nevet es az aliasokat is megtalalja`() {
        val notes = CharacterNotes.parse(json)
        for (name in listOf("Jakub Szapiro", "Szapiro", "Jakub")) {
            val hit = CharacterNotes.lookup(notes, name)
            assertNotNull("nem találta: $name", hit)
            assertEquals("Jakub Szapiro", hit!!.name)
        }
    }

    @Test
    fun `alias nelkul a nev szavaira is illeszt`() {
        // A szövegből az app csak „Ryfka"-t ismeri fel, a fájlban „Ryfka Kij".
        val notes = CharacterNotes.parse(json)
        assertEquals("Ryfka Kij", CharacterNotes.lookup(notes, "Ryfka")?.name)
    }

    @Test
    fun `a ragozott alakot is elkapja`() {
        // A névfelismerő néha ragozott alakot ad vissza.
        val notes = CharacterNotes.parse(json)
        assertEquals("Jakub Szapiro", CharacterNotes.lookup(notes, "Szapiróval")?.name)
    }

    @Test
    fun `az ekezet nem szamit`() {
        val notes = CharacterNotes.parse("""{"characters":[
            {"name":"Bíró Márton","description":"A falu bírája."}]}""")
        assertNotNull(CharacterNotes.lookup(notes, "Biro"))
    }

    @Test
    fun `ismeretlen nevre nem talal ki semmit`() {
        val notes = CharacterNotes.parse(json)
        assertNull(CharacterNotes.lookup(notes, "Gandalf"))
    }

    @Test
    fun `a rovid nevre nem illeszt veletlenul`() {
        val notes = CharacterNotes.parse(json)
        assertNull(CharacterNotes.lookup(notes, "Ja"))
    }

    @Test
    fun `a hianyos tetelt atugorja`() {
        val notes = CharacterNotes.parse("""{"characters":[
            {"name":"Névtelen"},
            {"description":"Leírás név nélkül."},
            {"name":"Teljes","description":"Ez rendben van."}]}""")
        assertNull(CharacterNotes.lookup(notes, "Névtelen"))
        assertNotNull(CharacterNotes.lookup(notes, "Teljes"))
    }

    @Test
    fun `a rossz tipusu mezo nem szall el`() {
        val notes = CharacterNotes.parse("""{"characters":[
            {"name":123,"description":["lista"]},
            {"name":"Jó","description":"Ez jó."}]}""")
        assertNotNull(CharacterNotes.lookup(notes, "Jó"))
    }

    @Test
    fun `a hianyzo lista ures terkepet ad`() {
        assertTrue(CharacterNotes.parse("""{"format":1}""").isEmpty())
        assertTrue(CharacterNotes.parse("""{"characters":[]}""").isEmpty())
    }

    @Test
    fun `ures terkepen a kereses nem szall el`() {
        assertNull(CharacterNotes.lookup(emptyMap(), "Bárki"))
    }

    @Test
    fun `a kiserofajl neve a konyv neve`() {
        val f = CharacterNotes.sidecarFor("/konyvek/A király.epub")
        assertEquals("A király.vox.json", f.name)
    }

    @Test
    fun `hianyzo fajlra ures terkep`() {
        assertTrue(CharacterNotes.load("/nincs/ilyen/konyv.epub").isEmpty())
    }
}
