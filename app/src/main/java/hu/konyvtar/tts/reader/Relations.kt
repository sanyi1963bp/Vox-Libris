package hu.konyvtar.tts.reader

/**
 * Kapcsolatszavak: rokonság és szerep.
 *
 * Ez az egyetlen szólista az egész funkcióban, és tudatosan az: a rokonsági
 * szavak halmaza **zárt és állandó** — nem úgy, mint egy kötőszólista, amit
 * tíz nyelvre kellene karbantartani, és ami minden szövegtípusnál mást
 * kívánna. Néhány tucat szó, ami száz éve ugyanaz.
 *
 * A lista magyar. Más nyelvű könyvnél a közbevetés-felismerés akkor is
 * működik (az nem szótárból dolgozik), csak ez a plusz kapaszkodó marad ki.
 */
internal object Relations {

    /**
     * Birtokos alakok — így fordulnak elő a szövegben: „Jóska **bátyja**",
     * nem „báty". A ragozott végződéseket a [CASE_SUFFIXES] fedi le.
     */
    private val STEMS = listOf(
        // vérrokonság
        "apja", "anyja", "bátyja", "öccse", "nővére", "húga", "testvére",
        "fia", "lánya", "leánya", "gyermeke", "unokája",
        "nagyapja", "nagyanyja", "dédapja", "dédanyja",
        "unokaöccse", "unokahúga", "unokatestvére",
        // házasság és rokonság
        "férje", "felesége", "menyasszonya", "vőlegénye",
        "sógora", "sógornője", "veje", "menye", "apósa", "anyósa",
        "mostohaapja", "mostohaanyja", "keresztfia", "keresztlánya",
        "özvegye", "szeretője",
        // szerep, viszony
        "barátja", "barátnője", "társa", "szomszédja", "mestere",
        "tanítványa", "szolgája", "ura", "úrnője", "gazdája",
        "kapitánya", "parancsnoka", "helyettese", "titkára",
        "tanácsadója", "örököse", "elődje", "utódja", "ellensége"
    ).sortedByDescending { it.length }

    /**
     * Magyar esetragok, amik a birtokos alak után jöhetnek.
     *
     * Ez a lista dönti el, hogy a „fiát" kapcsolatszó-e (`fia` + `t` → igen),
     * a „fiatal" pedig nem (`fia` + `tal` → nincs ilyen rag). Enélkül a puszta
     * előtag-egyezés rengeteg hétköznapi szót besöpörne.
     */
    private val CASE_SUFFIXES = setOf(
        "", "t", "ot", "et", "öt", "at",
        "nak", "nek", "val", "vel", "hoz", "hez", "höz",
        "ról", "ről", "ban", "ben", "ba", "be", "ból", "ből",
        "tól", "től", "ra", "re", "on", "en", "ön", "n",
        "ig", "ként", "vá", "vé", "ul", "ül", "hez", "kal", "kel"
    )

    /** Kapcsolatszó-e a szó, ragozott alakban is. */
    fun isRelationWord(word: String): Boolean {
        val w = word.lowercase()
        for (stem in STEMS) {
            if (w.length < stem.length) continue
            if (!stemMatches(w, stem)) continue
            if (w.substring(stem.length) in CASE_SUFFIXES) return true
        }
        return false
    }

    /**
     * A tő egyezik-e a szó elejével, megengedve a magyar **tővégi
     * magánhangzó-nyúlást**: `fia → fiát`, `lánya → lányát`, `öccse → öccsét`.
     * Enélkül épp a leggyakoribb, tárgyesetű alakok maradnának ki.
     */
    private fun stemMatches(word: String, stem: String): Boolean {
        for (i in stem.indices) {
            val a = word[i]
            val b = stem[i]
            if (a == b) continue
            val lengthened = (b == 'a' && a == 'á') || (b == 'e' && a == 'é')
            if (!lengthened || i != stem.length - 1) return false
        }
        return true
    }
}
