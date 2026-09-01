package hu.konyvtar.tts.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Idézetkártya: a kijelölt mondatból kép, amit meg lehet osztani.
 *
 * A kártya a **futó színséma színeit** kapja meg — a hívó adja át őket, mert
 * a színsémát a Compose témája ismeri, ez a fájl viszont szándékosan a
 * Compose-tól függetlenül rajzol. Így ugyanaz a kártya sötét témában sötét,
 * világosban világos, és nem kell külön kartonszínt kitalálni.
 */
object QuoteCard {

    private const val WIDTH = 1080
    private const val PAD = 84f
    private const val QUOTE_SIZE = 52f
    private const val META_SIZE = 30f
    private const val MARK_SIZE = 24f

    /** A kártya színei ARGB-ben, a hívó témájából. */
    data class Colors(
        val background: Int,
        val text: Int,
        val muted: Int,
        val accent: Int
    )

    /**
     * A kártya megrajzolása és megosztása.
     *
     * A kép a gyorsítótárba kerül (`cacheDir/share/`), onnan adjuk tovább a
     * FileProviderrel — a fogadó app így olvashatja, anélkül hogy bárminek
     * külső tárhelyre kellene kerülnie.
     *
     * @return false, ha a kép elkészítése nem sikerült
     */
    fun share(
        context: Context,
        quote: String,
        title: String,
        author: String,
        colors: Colors
    ): Boolean {
        return try {
            val bitmap = render(quote, title, author, colors)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "idezet.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, plainText(quote, title, author))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** A kép mellé menő sima szöveg — ahová kép nem fér, oda ez megy. */
    private fun plainText(quote: String, title: String, author: String): String {
        val who = listOf(title, author).filter { it.isNotBlank() }.joinToString(" — ")
        return if (who.isEmpty()) "„$quote”" else "„$quote”\n$who"
    }

    /** A kártya kirajzolása. A magasságot a szöveg hossza szabja meg. */
    fun render(quote: String, title: String, author: String, colors: Colors): Bitmap {
        val contentWidth = (WIDTH - 2 * PAD).toInt()

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.text
            textSize = QUOTE_SIZE
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.muted
            textSize = META_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.muted
            textSize = MARK_SIZE
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val quoteLayout = layout("„" + quote.trim() + "”", quotePaint, contentWidth)
        val meta = listOf(title, author).filter { it.isNotBlank() }.joinToString(" · ")
        val metaLayout = if (meta.isBlank()) null else layout(meta, metaPaint, contentWidth)

        val ruleGap = 44f
        val height = (
            PAD + quoteLayout.height +
                ruleGap + 2f + ruleGap +
                (metaLayout?.height ?: 0) +
                (if (metaLayout != null) 20f else 0f) +
                MARK_SIZE + PAD
            ).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background)

        // Vékony színes sáv a bal szélen — ettől kártya, nem képernyőkép.
        canvas.drawRect(
            0f, 0f, 10f, height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }
        )

        var y = PAD
        canvas.withTranslation(PAD, y) { quoteLayout.draw(this) }
        y += quoteLayout.height + ruleGap

        canvas.drawRect(
            PAD, y, PAD + contentWidth * 0.22f, y + 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }
        )
        y += 2f + ruleGap

        if (metaLayout != null) {
            canvas.withTranslation(PAD, y) { metaLayout.draw(this) }
            y += metaLayout.height + 20f
        }
        canvas.drawText("Vox Libris", PAD, y + MARK_SIZE, markPaint)

        return bitmap
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.0f)
            .setIncludePad(false)
            .build()

    private inline fun Canvas.withTranslation(dx: Float, dy: Float, block: Canvas.() -> Unit) {
        val saved = save()
        try {
            translate(dx, dy)
            block()
        } finally {
            restoreToCount(saved)
        }
    }
}
