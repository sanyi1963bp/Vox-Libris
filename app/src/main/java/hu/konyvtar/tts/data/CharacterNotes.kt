package hu.konyvtar.tts.data

import org.json.JSONObject
import java.io.File

/**
 * Szereplőleírások a könyv mellől.
 *
 * A heurisztika falba ütközött: két valódi regényen mérve huszonöt
 * szereplőből egyre adott bemutatást, és az is hibás volt. Ahhoz, hogy egy
 * szövegről meg lehessen mondani, ki kicsoda, **valódi szövegértés kell** —
 * az pedig nem fér el egy telefonos alkalmazásban.
 *
 * Ez a megoldás megkerüli a problémát anélkül, hogy az app garanciáit
 * feladná: a nehéz munka **a PC-n történik**, egy helyben futó nyelvi
 * modellel (`tools/vox_characters.py`), és az eredmény egy kis JSON-fájl a
 * könyv mellett. Az app csak beolvassa.
 *
 * Ezért az appnak **továbbra sincs internet-engedélye**, nincs API-kulcsa, és
 * nem hagy el semmi a telefont. Ha nincs ilyen fájl, minden marad a régiben.
 *
 * A fájl neve a könyvé, `.vox.json` végződéssel:
 * ```
 * A király.epub
 * A király.vox.json
 * ```
 * Így a könyvvel együtt másolható, és nem vész el az app újratelepítésekor.
 */
object CharacterNotes {

    /** Egy szereplő leírása a kísérőfájlból. */
    data class Note(val name: String, val description: String)

    /** A könyvhöz tartozó kísérőfájl — akkor is, ha még nem létezik. */
    fun sidecarFor(bookPath: String): File {
        val f = File(bookPath)
        return File(f.parentFile, f.nameWithoutExtension + ".vox.json")
    }

    /**
     * A kísérőfájl betöltése.
     *
     * @return alias → leírás, normalizált kulcsokkal; üres, ha nincs fájl
     */
    fun load(bookPath: String): Map<String, Note> = try {
        val file = sidecarFor(bookPath)
        if (file.isFile && file.length() < MAX_BYTES) parse(file.readText()) else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    /**
     * A JSON feldolgozása.
     *
     * Szándékosan elnéző: ami hiányzik vagy rossz típusú, azt kihagyjuk. Egy
     * félresikerült kísérőfájl miatt nem eshet szét a szereplőlista.
     */
    internal fun parse(json: String): Map<String, Note> {
        val out = HashMap<String, Note>()
        val root = JSONObject(json)
        val list = root.optJSONArray("characters") ?: return out
        for (i in 0 until list.length()) {
            val c = list.optJSONObject(i) ?: continue
            val name = c.optString("name").trim()
            val desc = c.optString("description").trim()
            if (name.isEmpty() || desc.isEmpty()) continue
            val note = Note(name, desc)

            // A név minden szava külön kulcs: az app a szövegből „Szapiro"-t
            // vagy „Jakub"-ot ismer fel, a kísérőfájlban viszont „Jakub
            // Szapiro" áll. Enélkül sosem találnának egymásra.
            for (k in keysOf(name)) out.putIfAbsent(k, note)

            val aliases = c.optJSONArray("aliases")
            if (aliases != null) {
                for (j in 0 until aliases.length()) {
                    val a = aliases.optString(j).trim()
                    if (a.isNotEmpty()) for (k in keysOf(a)) out.putIfAbsent(k, note)
                }
            }
        }
        return out
    }

    /**
     * Egy névből képzett keresőkulcsok: a teljes név és a szavai külön-külön.
     * Ékezet nélkül, kisbetűsen, hogy a ragozott alakok tövével is találkozzon.
     */
    internal fun keysOf(name: String): List<String> {
        val out = ArrayList<String>(4)
        val whole = key(name)
        if (whole.isNotEmpty()) out.add(whole)
        for (w in name.split(' ', '\t', '-')) {
            val k = key(w)
            if (k.length >= MIN_KEY && k != whole) out.add(k)
        }
        return out
    }

    /** Egy szó keresőkulccsá alakítva. */
    internal fun key(text: String): String =
        Normalizer.foldHu(text.trim()).lowercase().filter { it.isLetterOrDigit() || it == ' ' }

    /**
     * Megkeresi a szereplőhöz tartozó leírást.
     *
     * Az app a szövegből felismert nevet adja át, ami ragozott is lehet
     * („Szapiróval"); ezért a pontos egyezés után előtag-egyezéssel is
     * próbálkozunk.
     */
    fun lookup(notes: Map<String, Note>, detectedName: String): Note? {
        if (notes.isEmpty()) return null
        val k = key(detectedName)
        if (k.isEmpty()) return null
        notes[k]?.let { return it }
        if (k.length < MIN_KEY) return null
        return notes.entries.firstOrNull { (alias, _) ->
            alias.length >= MIN_KEY && (k.startsWith(alias) || alias.startsWith(k))
        }?.value
    }

    /** Ennél rövidebb kulcsra nem illesztünk — túl sok véletlen egyezés lenne. */
    private const val MIN_KEY = 3

    /** A kísérőfájl néhány tíz kilobájt; ennél nagyobbat nem olvasunk be. */
    private const val MAX_BYTES = 2L * 1024 * 1024
}
