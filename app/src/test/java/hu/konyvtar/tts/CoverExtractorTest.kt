package hu.konyvtar.tts

import hu.konyvtar.tts.data.CoverExtractor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A borítókinyerés próbái.
 *
 * A képek dekódolása már az Android dolga, ezt itt nem tudjuk futtatni — de
 * a nehezét igen: hol keresse a borítót az egyes formátumokban. A MOBI-nál
 * ez bájtpontos offset-számolás, épp az a fajta hiba, ami csendben rossz
 * képet ad vissza.
 */
class EpubCoverTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Ez a bájtsor játssza a borítóképet; a tartalma most nem számít. */
    private val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4, 5)
    private val otherBytes = byteArrayOf(9, 9, 9, 9)

    private fun epub(opf: String, coverPath: String = "OEBPS/images/cover.jpg"): File {
        val f = temp.newFile("proba-${System.nanoTime()}.epub")
        ZipOutputStream(FileOutputStream(f)).use { zip ->
            fun put(name: String, content: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container version="1.0"
                    xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf"
                        media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""".trimIndent().toByteArray()
            )
            put("OEBPS/content.opf", opf.toByteArray())
            put(coverPath, coverBytes)
            put("OEBPS/images/masik.jpg", otherBytes)
        }
        return f
    }

    @Test
    fun `az epub2 meta cover hivatkozast koveti`() {
        val f = epub(
            """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata>
                <meta name="cover" content="borito"/>
              </metadata>
              <manifest>
                <item id="masik" href="images/masik.jpg" media-type="image/jpeg"/>
                <item id="borito" href="images/cover.jpg" media-type="image/jpeg"/>
              </manifest>
            </package>""".trimIndent()
        )
        assertArrayEquals(coverBytes, CoverExtractor.fromEpub(f))
    }

    @Test
    fun `az epub3 cover-image tulajdonsagot is ismeri`() {
        val f = epub(
            """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>
                <item id="masik" href="images/masik.jpg" media-type="image/jpeg"/>
                <item id="b" href="images/cover.jpg" media-type="image/jpeg"
                      properties="cover-image"/>
              </manifest>
            </package>""".trimIndent()
        )
        assertArrayEquals(coverBytes, CoverExtractor.fromEpub(f))
    }

    @Test
    fun `jeloles nelkul a cover nevu kepet valasztja`() {
        val f = epub(
            """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <manifest>
                <item id="masik" href="images/masik.jpg" media-type="image/jpeg"/>
                <item id="c" href="images/cover.jpg" media-type="image/jpeg"/>
              </manifest>
            </package>""".trimIndent()
        )
        assertArrayEquals(coverBytes, CoverExtractor.fromEpub(f))
    }

    @Test
    fun `ha nincs kep a manifestben, nincs borito sem`() {
        val f = epub(
            """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <manifest>
                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
            </package>""".trimIndent()
        )
        assertNull(CoverExtractor.fromEpub(f))
    }

    // ---------------------------------------------------------------- útvonalak

    @Test
    fun `a relativ hivatkozas az opf mappajahoz kepest oldodik fel`() {
        assertEquals("OEBPS/images/cover.jpg", CoverExtractor.resolve("OEBPS", "images/cover.jpg"))
        assertEquals("cover.jpg", CoverExtractor.resolve("", "cover.jpg"))
    }

    @Test
    fun `a ket pont visszalep egy mappat`() {
        assertEquals("images/cover.jpg", CoverExtractor.resolve("OEBPS", "../images/cover.jpg"))
        assertEquals("OEBPS/cover.jpg", CoverExtractor.resolve("OEBPS/text", "../cover.jpg"))
    }

    @Test
    fun `a horgony es a szokoz-kodolas eltunik`() {
        assertEquals("a/b c.jpg", CoverExtractor.resolve("a", "b%20c.jpg#x"))
    }
}

class Fb2CoverTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a coverpage altal hivatkozott kepet adja vissza`() {
        // "Hello" base64-ben, sortöréssel — a valódi FB2-k is így tördelik
        val f = temp.newFile("proba.fb2")
        f.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <FictionBook>
              <description>
                <title-info>
                  <coverpage><image href="#borito.jpg"/></coverpage>
                </title-info>
              </description>
              <body><section><p>Szöveg.</p></section></body>
              <binary id="masik.jpg" content-type="image/jpeg">TWFzaWs=</binary>
              <binary id="borito.jpg" content-type="image/jpeg">SGVs
              bG8=</binary>
            </FictionBook>""".trimIndent(),
            Charsets.UTF_8
        )
        val out = CoverExtractor.fromFb2(f)
        assertEquals("Hello", out?.toString(Charsets.UTF_8))
    }

    @Test
    fun `coverpage nelkul az elso beagyazott kep jon`() {
        val f = temp.newFile("nincscover.fb2")
        f.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <FictionBook>
              <body><section><p>Szöveg.</p></section></body>
              <binary id="elso.jpg" content-type="image/jpeg">RWxzbw==</binary>
              <binary id="masodik.jpg" content-type="image/jpeg">TWFzb2Rpaw==</binary>
            </FictionBook>""".trimIndent(),
            Charsets.UTF_8
        )
        assertEquals("Elso", CoverExtractor.fromFb2(f)?.toString(Charsets.UTF_8))
    }
}

class MobiCoverTest {

    @get:Rule
    val temp = TemporaryFolder()

    /**
     * Minimális, de szabályos MOBI: PDB-fejléc a rekordtáblával, 0. rekord a
     * MOBI-fejléccel és az EXTH blokkal, utána szövegrekord, végül a képek.
     * Ez a teszt lényege: jó rekordot talál-e meg az offset-számolás.
     */
    private fun mobi(coverExth: Int?, images: List<ByteArray>): File {
        val f = temp.newFile("proba-${System.nanoTime()}.mobi")

        // --- 0. rekord összeállítása
        val exth = ByteArray(12 + (if (coverExth != null) 12 else 0)).also { e ->
            "EXTH".toByteArray(Charsets.US_ASCII).copyInto(e, 0)
            putInt(e, 4, e.size)
            putInt(e, 8, if (coverExth != null) 1 else 0)
            if (coverExth != null) {
                putInt(e, 12, 201)
                putInt(e, 16, 12)
                putInt(e, 20, coverExth)
            }
        }
        val mobiHeaderLen = 232
        val rec0 = ByteArray(16 + mobiHeaderLen + exth.size)
        "MOBI".toByteArray(Charsets.US_ASCII).copyInto(rec0, 16)
        putInt(rec0, 20, mobiHeaderLen)      // fejléchossz
        putInt(rec0, 28, 65001)              // kódolás
        putInt(rec0, 108, 3)                 // első képrekord indexe
        putInt(rec0, 128, 0x40)              // EXTH jelen van
        exth.copyInto(rec0, 16 + mobiHeaderLen)

        val text = "SZOVEG".toByteArray()
        val records = listOf(rec0, text, "MASODIK".toByteArray()) + images

        // --- PDB fejléc
        val n = records.size
        val headerSize = 78 + n * 8 + 2
        val out = ByteArray(headerSize + records.sumOf { it.size })
        "BOOKMOBI".toByteArray(Charsets.US_ASCII).copyInto(out, 60)
        out[76] = ((n shr 8) and 0xFF).toByte()
        out[77] = (n and 0xFF).toByte()
        var pos = headerSize
        for ((i, r) in records.withIndex()) {
            putInt(out, 78 + i * 8, pos)
            r.copyInto(out, pos)
            pos += r.size
        }
        FileOutputStream(f).use { it.write(out) }
        return f
    }

    private fun putInt(b: ByteArray, o: Int, v: Int) {
        b[o] = ((v ushr 24) and 0xFF).toByte()
        b[o + 1] = ((v ushr 16) and 0xFF).toByte()
        b[o + 2] = ((v ushr 8) and 0xFF).toByte()
        b[o + 3] = (v and 0xFF).toByte()
    }

    @Test
    fun `a 201-es exth rekord altal jelolt kepet adja vissza`() {
        val elso = byteArrayOf(1, 1, 1)
        val masodik = byteArrayOf(2, 2, 2, 2)
        // 3 = első képrekord, a 201-es értéke 1 -> a 4. rekord, vagyis a második kép
        val f = mobi(coverExth = 1, images = listOf(elso, masodik))
        assertArrayEquals(masodik, CoverExtractor.fromMobi(f))
    }

    @Test
    fun `a nulla eltolas az elso kepre mutat`() {
        val elso = byteArrayOf(7, 7, 7)
        val f = mobi(coverExth = 0, images = listOf(elso, byteArrayOf(8)))
        assertArrayEquals(elso, CoverExtractor.fromMobi(f))
    }

    @Test
    fun `exth rekord nelkul nincs borito`() {
        val f = mobi(coverExth = null, images = listOf(byteArrayOf(1, 2, 3)))
        assertNull(CoverExtractor.fromMobi(f))
    }

    @Test
    fun `a tartomanyon kivuli eltolas nem omlik ossze`() {
        val f = mobi(coverExth = 999, images = listOf(byteArrayOf(1, 2, 3)))
        assertNull(CoverExtractor.fromMobi(f))
    }

    @Test
    fun `a nem mobi fajlon nem hasal el`() {
        val f = temp.newFile("nem.mobi")
        f.writeText("ez nem egy mobi fájl")
        assertNull(CoverExtractor.fromMobi(f))
    }
}

class CoverFormatTest {

    @Test
    fun `csak azok a formatumok jonnek szoba, amikben lehet borito`() {
        assertTrue(CoverExtractor.canHaveCover("epub"))
        assertTrue(CoverExtractor.canHaveCover("MOBI"))
        assertTrue(CoverExtractor.canHaveCover("pdf"))
        assertTrue(CoverExtractor.canHaveCover("fb2"))
    }

    @Test
    fun `a sima szovegben nincs mit keresni`() {
        assertTrue(!CoverExtractor.canHaveCover("txt"))
        assertTrue(!CoverExtractor.canHaveCover("rtf"))
        assertTrue(!CoverExtractor.canHaveCover("docx"))
    }
}
