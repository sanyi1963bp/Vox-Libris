package hu.konyvtar.tts

import hu.konyvtar.tts.reader.EpubParser
import hu.konyvtar.tts.reader.Fb2Parser
import hu.konyvtar.tts.reader.RtfParser
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.reader.TxtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A könyvformátumok próbái. A tesztfájlokat itt állítjuk elő, hogy senki
 * saját könyvére ne legyen szükség a futtatáshoz.
 */
class EpubParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Szabályos, minimális EPUB: container.xml + OPF + két fejezet. */
    private fun buildEpub(): File {
        val f = temp.newFile("proba.epub")
        ZipOutputStream(FileOutputStream(f)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container version="1.0"
                    xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf"
                        media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>""".trimIndent()
            )
            put(
                "OEBPS/content.opf",
                """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>A próbakönyv</dc:title>
                    <dc:creator>Teszt Elek</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>""".trimIndent()
            )
            put(
                "OEBPS/ch1.xhtml",
                "<html><body><h1>Első fejezet</h1><p>Az első bekezdés.</p></body></html>"
            )
            put(
                "OEBPS/ch2.xhtml",
                "<html><body><h1>Második fejezet</h1><p>A második bekezdés.</p></body></html>"
            )
        }
        return f
    }

    @Test
    fun `a spine sorrendjeben olvassa a fejezeteket`() {
        val paras = EpubParser.parse(buildEpub())
        assertEquals(4, paras.size)
        assertEquals("\u0001Első fejezet", paras[0])
        assertEquals("Az első bekezdés.", paras[1])
        assertEquals("\u0001Második fejezet", paras[2])
        assertEquals("A második bekezdés.", paras[3])
    }

    @Test
    fun `minden fejezet eleje fejezethatart kap`() {
        val paras = EpubParser.parse(buildEpub())
        val chapters = paras.count { it.startsWith('\u0001') }
        assertEquals(2, chapters)
    }
}

class Fb2ParserTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `az fb2 bekezdesei es fejezetcimei megjonnek`() {
        val f = temp.newFile("proba.fb2")
        f.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
            <FictionBook>
              <body>
                <section>
                  <title><p>Kezdet</p></title>
                  <p>Első mondat a szövegben.</p>
                  <p>Második bekezdés.</p>
                </section>
              </body>
            </FictionBook>""".trimIndent(),
            Charsets.UTF_8
        )
        val paras = Fb2Parser.parse(f)
        assertTrue(paras.any { it.contains("Első mondat a szövegben.") })
        assertTrue(paras.any { it.contains("Második bekezdés.") })
    }
}

class TxtParserTest {

    @Test
    fun `az utf8 szoveg bekezdesekre bomlik`() {
        val paras = TxtParser.parse(
            "Első bekezdés.\n\nMásodik bekezdés őű.".toByteArray(Charsets.UTF_8)
        )
        assertEquals(2, paras.size)
        assertTrue(paras[1].contains("őű"))
    }

    @Test
    fun `a bom nem kerul a szovegbe`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Szöveg".toByteArray(Charsets.UTF_8)
        assertFalse(TxtParser.decode(bytes).startsWith("﻿"))
    }
}

class RtfParserTest {

    @Test
    fun `az rtf vezerlokodjai nem kerulnek a szovegbe`() {
        // Az ékezet szabályos RTF-escape (\'f6 = ö a cp1252-ben), mert a
        // valódi RTF-fájlok sem tartalmaznak nyers UTF-8 bájtokat.
        val rtf = """{\rtf1\ansi\deff0 {\fonttbl{\f0 Arial;}}\f0\fs24 Ez a sz\'f6veg.\par}"""
        val paras = RtfParser.parse(rtf.toByteArray(Charsets.ISO_8859_1))
        val all = paras.joinToString(" ")
        assertTrue(all.contains("Ez a szöveg."))
        assertFalse(all.contains("rtf1"))
        assertFalse(all.contains("fonttbl"))
    }
}

class TextExtractorTest {

    @Test
    fun `a felolvashato formatumokat ismeri`() {
        assertTrue(TextExtractor.isSupported("epub"))
        assertTrue(TextExtractor.isSupported("EPUB"))
        assertTrue(TextExtractor.isSupported("pdf"))
        assertTrue(TextExtractor.isSupported("mobi"))
    }

    @Test
    fun `a nem tamogatott formatumokra sajat magyarazat jar`() {
        assertFalse(TextExtractor.isSupported("doc"))
        assertFalse(TextExtractor.isSupported("djvu"))
        assertEquals(R.string.err_doc, TextExtractor.unsupportedRes("doc"))
        assertEquals(R.string.err_djvu, TextExtractor.unsupportedRes("djvu"))
        assertEquals(R.string.err_format_unsupported, TextExtractor.unsupportedRes("xyz"))
    }
}
