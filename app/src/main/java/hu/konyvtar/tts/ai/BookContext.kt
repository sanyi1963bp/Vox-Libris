package hu.konyvtar.tts.ai

import android.content.Context
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Catalog
import hu.konyvtar.tts.data.CharacterNotes
import hu.konyvtar.tts.data.MetadataExtractor
import hu.konyvtar.tts.data.Normalizer
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Az alapprompt összeállítása: amit az AI a beszélgetés elején megkap.
 *
 * Benne van a megszemélyesítés, a könyv adatai (katalógus, leírás, a
 * `.vox.json` kísérőfájl ismertetője, szereplői és fejezetösszefoglalói),
 * az olvasási pozíció és az eddig olvasott szöveg vége.
 *
 * A prompt angolul van, mert a modellek így követik a legmegbízhatóbban;
 * a válasz nyelvét külön előírjuk (a felület nyelve).
 *
 * **Spoilervédelem:** bekapcsolva csak az olvasási pozícióig adunk át
 * szöveget és összefoglalót, és a modellt is megkérjük, hogy a saját
 * tudásából se áruljon el többet.
 */
object BookContext {

    /** A beszélgetéshez szükséges, egyszer kiszámolt adatok. */
    data class Snapshot(
        val title: String,
        val author: String,
        /** A teljes alapprompt. */
        val systemPrompt: String,
        /** Felhasználónak mutatható összegzés: mit lát az AI. */
        val summaryLines: List<String>
    )

    /**
     * Összerakja az alapproptot. Lassú lehet (fájl- és adatbázis-olvasás),
     * ezért IO szálról kell hívni.
     *
     * @param paragraphs a könyv bekezdései
     * @param chapters a fejezetkezdő bekezdések indexei
     * @param posPara az olvasási pozíció bekezdése
     * @param posChar a pozíció karaktere a bekezdésen belül
     */
    fun build(
        context: Context,
        path: String,
        title: String,
        author: String,
        paragraphs: List<String>,
        chapters: List<Int>,
        posPara: Int,
        posChar: Int
    ): Snapshot {
        val guard = AiSettings.spoilerGuard(context)
        val para = posPara.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0))
        val percent = if (paragraphs.size > 1) para * 100 / (paragraphs.size - 1) else 0
        val appChapter = chapters.count { it <= para }        // 1-től, 0 = előszó/nincs fejezet
        val summary = ArrayList<String>()

        // ---------------------------------------------------------------- adatok
        val catalog = try { Catalog.bookForPath(path) } catch (_: Exception) { null }
        val meta = if (catalog == null) {
            try { MetadataExtractor.extract(context, File(path), true) } catch (_: Exception) { null }
        } else null
        val sidecar = loadSidecar(path)
        val note = try { AppDb.noteFor(path) } catch (_: Exception) { null }

        val sb = StringBuilder()

        // ---------------------------------------------------------------- szerep
        sb.append(personaText(context, title, author, sidecar))
        sb.append("\n\n")

        val name = AiSettings.userName(context)
        if (name.isNotBlank()) {
            sb.append("The reader's name is \"").append(name).append("\". Address them by name now and then, naturally.\n")
        }
        val lang = context.resources.configuration.locales[0]
        sb.append("Always answer in ").append(lang.getDisplayLanguage(Locale.ENGLISH))
            .append(" unless the reader writes in another language — then answer in theirs.\n")
        sb.append(lengthText(AiSettings.length(context))).append("\n")
        sb.append(
            "Your answers may be read aloud by a text-to-speech engine, so write plain " +
                "conversational prose: no markdown, no headings, no bullet symbols, no emojis, " +
                "no tables.\n"
        )
        sb.append(
            "If you are unsure about a detail of this book, say so instead of inventing it. " +
                "Prefer the book information and text below over your general memory.\n"
        )

        // ---------------------------------------------------------------- spoiler
        sb.append("\n## Spoiler rule\n")
        if (guard) {
            sb.append(
                "The reader is at about $percent% of the book" +
                    (if (appChapter > 0) ", in chapter $appChapter" else "") + ". " +
                    "STRICT: never reveal, hint at or confirm anything that happens after this point — " +
                    "not even from your own knowledge of the book, and not even if asked indirectly. " +
                    "If the reader asks about later events, kindly say you will not spoil it and offer " +
                    "to talk about what they have read so far. Some notes below (e.g. character " +
                    "descriptions) may contain later information; use them only as far as they " +
                    "agree with the text read so far.\n"
            )
            summary.add("spoiler")
        } else {
            sb.append(
                "The reader is at about $percent% of the book. They have switched spoiler " +
                    "protection off, so you may discuss the whole book, including the ending, " +
                    "if they ask. Still do not volunteer the ending unasked.\n"
            )
        }

        // ---------------------------------------------------------------- könyv
        sb.append("\n## The book\n")
        sb.append("Title: ").append(title).append("\n")
        if (author.isNotBlank()) sb.append("Author: ").append(author).append("\n")
        sidecar?.optString("original_title")?.takeIf { it.isNotBlank() }?.let {
            sb.append("Original title: ").append(it).append("\n")
        }
        (sidecar?.optInt("published", 0)?.takeIf { it > 0 }?.toString()
            ?: catalog?.kiadasEve ?: meta?.year)?.takeIf { it.isNotBlank() }?.let {
            sb.append("Published: ").append(it).append("\n")
        }
        (catalog?.kiado ?: meta?.publisher)?.takeIf { it.isNotBlank() }?.let {
            sb.append("Publisher: ").append(it).append("\n")
        }
        val series = sidecar?.optString("series")?.ifBlank { null } ?: catalog?.sorozat ?: meta?.series
        if (!series.isNullOrBlank()) {
            val idx = sidecar?.optInt("series_index", 0)?.takeIf { it > 0 }?.toString()
                ?: catalog?.sorozatSzama ?: meta?.seriesIndex
            sb.append("Series: ").append(series).append(if (!idx.isNullOrBlank()) " #$idx" else "").append("\n")
        }
        val genres = sidecar?.optJSONArray("genres")?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
        }?.ifBlank { null } ?: catalog?.cimkek ?: meta?.tags
        if (!genres.isNullOrBlank()) sb.append("Genres/tags: ").append(genres).append("\n")
        sidecar?.optString("setting")?.takeIf { it.isNotBlank() }?.let {
            sb.append("Setting: ").append(it).append("\n")
        }
        sidecar?.optString("tagline")?.takeIf { it.isNotBlank() }?.let {
            sb.append("In one sentence: ").append(it).append("\n")
        }
        val blurb = sidecar?.optString("blurb")?.ifBlank { null }
        val desc = (catalog?.leiras ?: meta?.description)?.let { cleanHtml(it) }?.take(4000)
        if (!blurb.isNullOrBlank()) sb.append("Blurb: ").append(blurb).append("\n")
        if (!desc.isNullOrBlank()) sb.append("Publisher's description: ").append(desc).append("\n")
        if (!blurb.isNullOrBlank() || !desc.isNullOrBlank()) summary.add("description")
        sb.append("Length: ").append(paragraphs.size).append(" paragraphs")
        if (chapters.isNotEmpty()) sb.append(", ").append(chapters.size).append(" chapters")
        sb.append(".\n")

        // ---------------------------------------------------------------- fejezetek
        val sideChapters = sidecar?.optJSONArray("chapters")
        if (sideChapters != null && sideChapters.length() > 0) {
            val current = currentSidecarChapter(sideChapters, paragraphs, para, appChapter)
            val story = StringBuilder()
            for (i in 0 until sideChapters.length()) {
                val ch = sideChapters.optJSONObject(i) ?: continue
                val n = ch.optInt("number", i + 1)
                if (guard && n >= current) break   // a folyamatban lévő fejezet összefoglalója már előre néz
                val soFar = ch.optString("so_far").trim()
                if (soFar.isEmpty()) continue
                // A „so_far" halmozott, ezért elég a legutolsó engedélyezett —
                // de a címeket mind felsoroljuk a tájékozódáshoz.
                story.setLength(0)
                story.append("Story up to the end of chapter ").append(n)
                ch.optString("title").takeIf { it.isNotBlank() }?.let { story.append(" (\"").append(it).append("\")") }
                story.append(": ").append(soFar)
            }
            if (story.isNotEmpty()) {
                sb.append("\n## Story so far\n").append(story).append("\n")
                if (guard) {
                    sb.append(
                        "(This summary ends where the reader's current chapter begins. What happened " +
                            "since then is in the text excerpt below.)\n"
                    )
                }
                summary.add("story")
            }
        }

        // ---------------------------------------------------------------- szereplők
        val chars = sidecar?.optJSONArray("characters")
        if (chars != null && chars.length() > 0) {
            val current = if (sideChapters != null && sideChapters.length() > 0)
                currentSidecarChapter(sideChapters, paragraphs, para, appChapter) else Int.MAX_VALUE
            val lines = ArrayList<String>()
            for (i in 0 until chars.length()) {
                val c = chars.optJSONObject(i) ?: continue
                val n = c.optString("name").trim()
                val d = c.optString("description").trim()
                if (n.isEmpty()) continue
                val first = c.optInt("first_chapter", 0)
                if (guard && first > 0 && first > current) continue
                val role = c.optString("role").trim()
                val aliases = c.optJSONArray("aliases")?.let { a ->
                    (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                }.orEmpty()
                lines.add(
                    "- " + n + (if (aliases.isNotEmpty()) " (also: ${aliases.joinToString(", ")})" else "") +
                        (if (role.isNotEmpty()) " [$role]" else "") + (if (d.isNotEmpty()) ": $d" else "")
                )
                if (lines.size >= 40) break
            }
            if (lines.isNotEmpty()) {
                sb.append("\n## Characters\n").append(lines.joinToString("\n")).append("\n")
                summary.add("characters")
            }
        }

        // ---------------------------------------------------------------- jegyzet
        if (!note.isNullOrBlank()) {
            sb.append("\n## The reader's own note about this book\n").append(note.take(2000)).append("\n")
        }

        // ---------------------------------------------------------------- szöveg
        val excerpt = textUpTo(paragraphs, para, posChar, AiSettings.contextChars(context))
        if (excerpt.isNotBlank()) {
            sb.append("\n## The text the reader has just read\n")
            sb.append(
                "Below is the end of the text read so far (up to ${excerpt.length} characters). " +
                    "The LAST sentence is exactly where the reader is now.\n"
            )
            sb.append("<<<\n").append(excerpt).append("\n>>>\n")
            summary.add("text:${excerpt.length}")
        }

        val extra = AiSettings.extraInstructions(context)
        if (extra.isNotBlank()) {
            sb.append("\n## Extra wishes of the reader\n").append(extra.take(1500)).append("\n")
        }

        return Snapshot(title, author, sb.toString(), summary)
    }

    // ------------------------------------------------------------------ szerepek

    private fun personaText(context: Context, title: String, author: String, sidecar: JSONObject?): String {
        val book = "\"$title\"" + if (author.isNotBlank()) " by $author" else ""
        return when (AiSettings.persona(context)) {
            AiSettings.PERSONA_TEACHER ->
                "You are an experienced, warm literature teacher talking one-on-one with a reader " +
                    "of the book $book. Explain context, themes, style, historical background and " +
                    "difficult passages clearly, and ask a thoughtful question back now and then."
            AiSettings.PERSONA_AUTHOR ->
                "You are ${author.ifBlank { "the author" }}, the author of $book, talking with one of your " +
                    "readers. Speak in the first person about why and how you wrote the book, your " +
                    "characters and your intentions, in a voice that fits the author. Stay plausible: " +
                    "where real biographical facts are unknown, say it is your imagined view. " +
                    "(This is a role-play the reader asked for.)"
            AiSettings.PERSONA_CHARACTER -> {
                val wanted = AiSettings.characterName(context).ifBlank {
                    sidecar?.optJSONArray("characters")?.optJSONObject(0)?.optString("name").orEmpty()
                }.ifBlank { "the main character" }
                "You are $wanted, a character of the book $book, talking with the reader who is " +
                    "reading your story. Speak in the first person, in your own voice and manner, " +
                    "knowing only what you would know at the reader's current point in the story. " +
                    "(This is a role-play the reader asked for.)"
            }
            AiSettings.PERSONA_LIBRARIAN ->
                "You are a well-read, friendly librarian. The reader is reading $book. Besides " +
                    "talking about it, you like to connect it to other books, authors and periods " +
                    "and to suggest what to read next."
            AiSettings.PERSONA_CUSTOM -> {
                val custom = AiSettings.customPersona(context).trim()
                if (custom.isNotEmpty()) "$custom\nThe reader is reading the book $book."
                else "You are a friendly reading companion. The reader is reading the book $book."
            }
            else ->
                "You are a friendly, curious reading companion — like a well-read friend who is " +
                    "reading the same book, $book. Talk about the story, characters, words and ideas " +
                    "the reader brings up, and share your impressions."
        }
    }

    private fun lengthText(length: String): String = when (length) {
        AiSettings.LENGTH_SHORT -> "Keep answers short: one to three sentences."
        AiSettings.LENGTH_LONG -> "Detailed answers are welcome, but stay focused (at most about 4 short paragraphs)."
        else -> "Keep answers fairly short: usually one short paragraph."
    }

    // ------------------------------------------------------------------ segédek

    /** A `.vox.json` kísérőfájl (a vox_gemini.py / vox_characters.py készíti). */
    private fun loadSidecar(path: String): JSONObject? = try {
        val f = CharacterNotes.sidecarFor(path)
        if (f.isFile && f.length() < 4L * 1024 * 1024) JSONObject(f.readText()) else null
    } catch (_: Exception) {
        null
    }

    /**
     * Melyik kísérőfájl-fejezetben tart az olvasó.
     *
     * Elsősorban a fejezetek szó szerinti első mondatát keressük meg a
     * szövegben (ezt a vox_gemini.py pont ezért kéri), mert az app és a modell
     * fejezetszámozása eltérhet. Ha ez nem megy, az app fejezetszámát vesszük.
     */
    private fun currentSidecarChapter(
        chapters: org.json.JSONArray,
        paragraphs: List<String>,
        para: Int,
        fallback: Int
    ): Int {
        var found = 0
        var anyMatch = false
        var searchFrom = 0
        val folded = HashMap<Int, String>()
        fun fold(i: Int) = folded.getOrPut(i) { Normalizer.foldHu(paragraphs[i]).lowercase() }
        for (i in 0 until chapters.length()) {
            val ch = chapters.optJSONObject(i) ?: continue
            val first = Normalizer.foldHu(ch.optString("first_sentence")).lowercase().trim().take(48)
            if (first.length < 12) continue
            var at = -1
            for (p in searchFrom until paragraphs.size) {
                if (fold(p).contains(first)) { at = p; break }
                if (p - searchFrom > 6000) break
            }
            if (at < 0) continue
            anyMatch = true
            searchFrom = at + 1
            if (at <= para) found = ch.optInt("number", i + 1) else break
        }
        return if (anyMatch) found else fallback
    }

    /** Az eddig olvasott szöveg utolsó [maxChars] karaktere, a pozícióig. */
    private fun textUpTo(paragraphs: List<String>, para: Int, char: Int, maxChars: Int): String {
        if (paragraphs.isEmpty()) return ""
        val parts = ArrayList<String>()
        var total = 0
        var i = para
        while (i >= 0 && total < maxChars) {
            var p = paragraphs[i]
            if (i == para && char in 1 until p.length) p = p.substring(0, char)
            parts.add(p)
            total += p.length + 2
            i--
        }
        parts.reverse()
        val joined = parts.joinToString("\n\n")
        return if (joined.length > maxChars) joined.takeLast(maxChars) else joined
    }

    private fun cleanHtml(s: String): String =
        s.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()
}
