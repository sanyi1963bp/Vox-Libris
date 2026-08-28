package hu.konyvtar.tts.reader

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** A felolvasáshoz nem támogatott vagy hibás fájlok jelzésére. */
class ExtractException(message: String) : Exception(message)

/** Egy könyv kinyert tartalma: bekezdések + fejezetkezdő bekezdés-indexek. */
data class ExtractedBook(
    val paragraphs: List<String>,
    val chapters: List<Int>
)

/**
 * Egységes szövegkinyerés minden formátumhoz + bekezdés-gyorsítótár.
 * A parserek a fejezetkezdő bekezdéseket '\u0001' előtaggal jelölik;
 * itt válik szét tiszta szöveglistára és fejezetindex-listára.
 */
object TextExtractor {

    /** Formátumok, amikből szöveget tudunk kinyerni a felolvasáshoz. */
    val SUPPORTED = setOf("txt", "epub", "fb2", "mobi", "prc", "azw", "azw3", "rtf", "pdf", "docx", "htm", "html")

    /** Legfeljebb ekkora egy TTS-nek átadott bekezdés. */
    private const val MAX_CHUNK = 3000

    private const val CH_MARK = '\u0001'
    private const val SEPARATOR = "\n\n"

    fun isSupported(ext: String): Boolean = ext.lowercase() in SUPPORTED

    fun unsupportedHint(ext: String): String = when (ext.lowercase()) {
        "doc" -> "A régi bináris .doc formátum nem támogatott — konvertáld .docx-re vagy .txt-re (pl. Calibre/Word)."
        "djvu" -> "A DjVu képalapú formátum, nincs benne kinyerhető szöveg — OCR után .txt-ként felolvasható."
        else -> "Ez a formátum (.$ext) nem támogatott felolvasáshoz."
    }

    // ---------------------------------------------------------------- cache

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "text")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cacheKey(file: File): String {
        // v2: fejezetadatokkal együtt tárolt formátum
        val raw = "${file.absolutePath}|${file.length()}|${file.lastModified()}|v2"
        val md = MessageDigest.getInstance("MD5")
        return md.digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun cachedSizeBytes(context: Context): Long {
        val dir = File(context.cacheDir, "text")
        if (!dir.exists()) return 0
        return dir.listFiles()?.sumOf { it.length() } ?: 0
    }

    fun clearCache(context: Context) {
        File(context.cacheDir, "text").listFiles()?.forEach { it.delete() }
        File(context.cacheDir, "share").listFiles()?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------- fő belépési pontok

    /**
     * A könyv teljes tartalma: bekezdések + fejezethatárok. Cache-elve.
     * Hiba esetén [ExtractException]-t dob magyar üzenettel.
     */
    fun book(context: Context, file: File): ExtractedBook {
        if (!file.exists()) throw ExtractException("A fájl nem található: ${file.absolutePath}")
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (!isSupported(ext)) throw ExtractException(unsupportedHint(ext))

        val key = cacheKey(file)
        val cacheFile = File(cacheDir(context), "$key.txt")
        val chFile = File(cacheDir(context), "$key.ch")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val paras = cacheFile.readText(Charsets.UTF_8).split(SEPARATOR)
                if (paras.isNotEmpty() && paras[0].isNotEmpty()) {
                    val chapters = if (chFile.exists()) {
                        chFile.readText(Charsets.UTF_8).split(',')
                            .mapNotNull { it.trim().toIntOrNull() }
                            .filter { it in paras.indices }
                    } else {
                        emptyList()
                    }
                    return ExtractedBook(paras, chapters)
                }
            } catch (_: Exception) {
            }
        }

        val raw: List<String> = when (ext) {
            "txt" -> TxtParser.parse(file.readBytes())
            "epub" -> EpubParser.parse(file)
            "fb2" -> Fb2Parser.parse(file)
            "mobi", "prc", "azw", "azw3" -> MobiParser.parse(file)
            "rtf" -> RtfParser.parse(file.readBytes())
            "pdf" -> PdfParser.parse(context, file)
            "docx" -> DocxParser.parse(file)
            "htm", "html" -> HtmlText.toParagraphs(TxtParser.decode(file.readBytes()))
            else -> throw ExtractException(unsupportedHint(ext))
        }

        val chunked = chunk(raw)
        if (chunked.isEmpty()) throw ExtractException("A fájlból nem sikerült szöveget kinyerni.")

        var chapters = ArrayList<Int>()
        val clean = ArrayList<String>(chunked.size)
        for (i in chunked.indices) {
            val p = chunked[i]
            if (p.startsWith(CH_MARK)) {
                chapters.add(i)
                clean.add(p.substring(1))
            } else {
                clean.add(p)
            }
        }
        if (chapters.size < 2) {
            chapters = ArrayList(heuristicChapters(clean))
        }
        // Túl sok "fejezet" gyanús (rosszul sült el a felismerés) — inkább semmi
        if (chapters.size > clean.size / 6 + 2) {
            chapters = ArrayList()
        }

        try {
            cacheFile.writeText(clean.joinToString(SEPARATOR), Charsets.UTF_8)
            chFile.writeText(chapters.joinToString(","), Charsets.UTF_8)
        } catch (_: Exception) {
            // ha a cache írása nem megy, attól a felolvasás még működik
        }
        return ExtractedBook(clean, chapters)
    }

    /** Csak a bekezdéslista (kompatibilitási belépési pont). */
    fun paragraphs(context: Context, file: File): List<String> = book(context, file).paragraphs

    /** Rövid szöveg-előnézet a részletező képernyőhöz. */
    fun preview(context: Context, file: File, maxChars: Int = 3000): String {
        val paras = paragraphs(context, file)
        val sb = StringBuilder()
        for (p in paras) {
            if (sb.length >= maxChars) break
            sb.append(p).append("\n\n")
        }
        var s = sb.toString().trim()
        if (s.length > maxChars) s = s.substring(0, maxChars) + "…"
        return s
    }

    // ---------------------------------------------------------------- feldolgozás

    /**
     * Hosszú bekezdések darabolása mondathatáron, hogy a TTS limitbe beleférjenek.
     * A '\u0001' fejezetjelölőt az első darabon megőrzi.
     */
    private fun chunk(raw: List<String>): List<String> {
        val out = ArrayList<String>(raw.size)
        for (p0 in raw) {
            val marked = p0.startsWith(CH_MARK)
            var p = (if (marked) p0.substring(1) else p0).trim()
            if (p.isEmpty()) continue
            var first = true
            while (p.length > MAX_CHUNK) {
                var cut = -1
                for (mark in listOf(". ", "! ", "? ", "… ", "; ")) {
                    val idx = p.lastIndexOf(mark, MAX_CHUNK)
                    if (idx > cut) cut = idx + mark.length - 1
                }
                if (cut < MAX_CHUNK / 4) {
                    val sp = p.lastIndexOf(' ', MAX_CHUNK)
                    cut = if (sp > MAX_CHUNK / 4) sp else MAX_CHUNK
                }
                val piece = p.substring(0, cut).trim()
                if (piece.isNotEmpty()) {
                    out.add(if (first && marked) CH_MARK + piece else piece)
                    first = false
                }
                p = p.substring(cut).trim()
            }
            if (p.isNotEmpty()) {
                out.add(if (first && marked) CH_MARK + p else p)
            }
        }
        return out
    }

    private val numHeading = Regex("^\\d{1,3}\\.?$")
    private val romanHeading = Regex("^[IVXLCDM]{1,8}\\.?$")
    private val chapterWord = Regex(
        "(?i)^(\\d{1,3}\\.?\\s+)?(fejezet|rész|könyv|prológus|epilógus|előszó|utószó|bevezetés|bevezető|első|második|harmadik|negyedik|ötödik)\\b.*"
    )

    /**
     * Címsor-heurisztika olyan formátumokhoz, ahol nincs jelölt fejezet
     * (txt, rtf, pdf…): rövid, számozás- vagy címszerű bekezdések.
     */
    private fun heuristicChapters(paras: List<String>): List<Int> {
        val out = ArrayList<Int>()
        for (i in paras.indices) {
            val t = paras[i].trim()
            if (t.length > 60 || t.isEmpty()) continue
            val letters = t.filter { it.isLetter() }
            val isAllCaps = letters.length >= 4 && t.length <= 48 && letters.all { it.isUpperCase() }
            if (numHeading.matches(t) || romanHeading.matches(t) ||
                (t.length <= 60 && chapterWord.matches(t)) || isAllCaps
            ) {
                out.add(i)
            }
        }
        return if (out.size < 2) emptyList() else out
    }

    // ---------------------------------------------------------------- export

    /**
     * A könyv teljes szövege egyetlen .txt fájlba a megosztáshoz
     * (külső TTS appnak ACTION_SEND-del).
     */
    fun exportTxt(context: Context, file: File): File {
        val paras = paragraphs(context, file)
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        val safeName = file.name.substringBeforeLast('.').take(60).replace(Regex("[^\\p{L}\\p{N} _.-]"), "_")
        val outFile = File(dir, "$safeName.txt")
        outFile.writeText(paras.joinToString("\n\n"), Charsets.UTF_8)
        return outFile
    }
}
