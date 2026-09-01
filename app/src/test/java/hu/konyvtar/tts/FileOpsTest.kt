package hu.konyvtar.tts

import hu.konyvtar.tts.data.FileOps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A fájlnév-ellenőrzés próbái.
 *
 * Ez az a pont, ahol egy elgépelés tönkretehet egy fájlt: egy perjel a
 * névben más mappába írna, egy pont önmagában a mappára hivatkozna.
 */
class FileNameTest {

    @Test
    fun `a rendes fajlnevet elfogadja`() {
        assertTrue(FileOps.isValidName("Rejtő Jenő - A tizennégy karátos autó.epub"))
        assertTrue(FileOps.isValidName("konyv.pdf"))
        assertTrue(FileOps.isValidName("2001 Űrodüsszeia.mobi"))
    }

    @Test
    fun `az ures nev nem jo`() {
        assertFalse(FileOps.isValidName(""))
        assertFalse(FileOps.isValidName("   "))
    }

    @Test
    fun `a mappahatarolo jelek tiltottak`() {
        assertFalse(FileOps.isValidName("mappa/konyv.epub"))
        assertFalse(FileOps.isValidName("mappa\\konyv.epub"))
    }

    @Test
    fun `a fajlrendszerben foglalt jelek tiltottak`() {
        for (bad in listOf("a:b.epub", "a*b.epub", "a?b.epub", "a\"b.epub",
                           "a<b.epub", "a>b.epub", "a|b.epub")) {
            assertFalse(bad, FileOps.isValidName(bad))
        }
    }

    @Test
    fun `az egy es ket pont a mappara hivatkozna`() {
        assertFalse(FileOps.isValidName("."))
        assertFalse(FileOps.isValidName(".."))
    }

    @Test
    fun `a rejtett fajl neve viszont rendben van`() {
        assertTrue(FileOps.isValidName(".rejtett.epub"))
    }

    @Test
    fun `a tulsagosan hosszu nev nem jo`() {
        assertFalse(FileOps.isValidName("a".repeat(201) + ".epub"))
        assertTrue(FileOps.isValidName("a".repeat(150) + ".epub"))
    }
}
