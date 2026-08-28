package hu.konyvtar.tts.reader

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/**
 * PDF szövegréteg kinyerése (PDFBox). Képalapú/szkennelt PDF-re érthető hibát ad
 * — képfeldolgozást/OCR-t szándékosan nem végzünk.
 */
object PdfParser {

    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    fun parse(context: Context, file: File): List<String> {
        ensureInit(context)
        val text: String = try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper()
                stripper.getText(doc)
            }
        } catch (e: InvalidPasswordException) {
            throw ExtractException("Ez a PDF jelszóval védett, nem olvasható fel.")
        } catch (e: ExtractException) {
            throw e
        } catch (e: Exception) {
            throw ExtractException("A PDF nem dolgozható fel: ${e.message ?: "ismeretlen hiba"}")
        }

        if (text.isBlank()) {
            throw ExtractException("A PDF nem tartalmaz szövegréteget (valószínűleg szkennelt/képalapú). OCR után .txt-ként felolvasható.")
        }

        // A PDF sortörései vizuálisak — mondatvégeknél tartunk bekezdéshatárt,
        // egyébként a sorokat összefűzzük.
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>(1024)
        val sb = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString().trim())
                    sb.setLength(0)
                }
                continue
            }
            // kötőjeles elválasztás összeolvasztása: "vala-" + "mi" -> "valami"
            if (sb.isNotEmpty() && sb.endsWith("-")) {
                sb.setLength(sb.length - 1)
                sb.append(line)
            } else {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(line)
            }
            val endsSentence = line.endsWith(".") || line.endsWith("!") || line.endsWith("?") ||
                line.endsWith("…") || line.endsWith(".\"") || line.endsWith("!\"") ||
                line.endsWith("?\"") || line.endsWith(".”") || line.endsWith("!”") || line.endsWith("?”")
            if (endsSentence && sb.length > 200) {
                out.add(sb.toString().trim())
                sb.setLength(0)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString().trim())

        val paras = out.filter { it.isNotEmpty() }
        if (paras.isEmpty()) throw ExtractException("A PDF-ből nem sikerült szöveget kinyerni.")
        return paras
    }
}
