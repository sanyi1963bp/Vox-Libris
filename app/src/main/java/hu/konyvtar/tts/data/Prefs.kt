package hu.konyvtar.tts.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment

/** Egyszerű beállítástároló (SharedPreferences). */
object Prefs {

    private const val NAME = "konyvtar_tts_prefs"

    private const val KEY_ROOT_PATH = "root_path"
    private const val KEY_SPEED = "tts_speed"
    private const val KEY_PITCH = "tts_pitch"
    private const val KEY_SORT_KEY = "sort_key"
    private const val KEY_SORT_ASC = "sort_asc"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun rootPath(context: Context): String {
        val def = Environment.getExternalStorageDirectory().absolutePath
        return sp(context).getString(KEY_ROOT_PATH, def) ?: def
    }

    fun setRootPath(context: Context, path: String) {
        sp(context).edit().putString(KEY_ROOT_PATH, path).apply()
        sp(context).edit().putBoolean("root_chosen", true).apply()
    }

    /** Választott-e már a felhasználó könyvmappát (az indulási varázslóhoz). */
    fun rootChosen(context: Context): Boolean = sp(context).getBoolean("root_chosen", false)

    fun speed(context: Context): Float = sp(context).getFloat(KEY_SPEED, 1.0f)

    fun setSpeed(context: Context, value: Float) {
        sp(context).edit().putFloat(KEY_SPEED, value).apply()
    }

    fun pitch(context: Context): Float = sp(context).getFloat(KEY_PITCH, 1.0f)

    fun setPitch(context: Context, value: Float) {
        sp(context).edit().putFloat(KEY_PITCH, value).apply()
    }

    fun sortKey(context: Context): String = sp(context).getString(KEY_SORT_KEY, "NAME") ?: "NAME"

    fun setSortKey(context: Context, value: String) {
        sp(context).edit().putString(KEY_SORT_KEY, value).apply()
    }

    fun sortAsc(context: Context): Boolean = sp(context).getBoolean(KEY_SORT_ASC, true)

    fun setSortAsc(context: Context, value: Boolean) {
        sp(context).edit().putBoolean(KEY_SORT_ASC, value).apply()
    }

    // ------------------------------------------------------- könyvtár lista

    /** A lista rendezése: TITLE, AUTHOR vagy FORMAT. */
    fun libSortKey(context: Context): String =
        sp(context).getString("lib_sort_key", "TITLE") ?: "TITLE"

    fun setLibSortKey(context: Context, value: String) {
        sp(context).edit().putString("lib_sort_key", value).apply()
    }

    fun libSortAsc(context: Context): Boolean = sp(context).getBoolean("lib_sort_asc", true)

    fun setLibSortAsc(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("lib_sort_asc", value).apply()
    }

    /**
     * Megnyitott-e már könyvet a listáról. Amíg nem, a lista tetején ott a
     * kétujjnyi súgó a koppintásokról; utána eltűnik, hogy ne foglalja a helyet.
     */
    fun gestureHintSeen(context: Context): Boolean =
        sp(context).getBoolean("gesture_hint_seen", false)

    fun setGestureHintSeen(context: Context) {
        sp(context).edit().putBoolean("gesture_hint_seen", true).apply()
    }

    /**
     * Látszódjon-e bélyegkép a könyvlistában. Alapból nem: a képek magasabb
     * sorokat jelentenek, és így kevesebb könyv fér egy képernyőre.
     */
    fun coversInList(context: Context): Boolean =
        sp(context).getBoolean("covers_in_list", false)

    fun setCoversInList(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("covers_in_list", value).apply()
    }

    /** Keressen-e a kereső az almappákban is. */
    fun searchRecursive(context: Context): Boolean =
        sp(context).getBoolean("search_recursive", false)

    fun setSearchRecursive(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("search_recursive", value).apply()
    }

    fun readerFont(context: Context): Float = sp(context).getFloat("reader_font_sp", 17f)

    fun setReaderFont(context: Context, value: Float) {
        sp(context).edit().putFloat("reader_font_sp", value).apply()
    }

    /** A szöveg kövesse-e automatikusan a felolvasást az olvasó képernyőn. */
    fun readerFollow(context: Context): Boolean = sp(context).getBoolean("reader_follow", true)

    fun setReaderFollow(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("reader_follow", value).apply()
    }

    // ---------------------------------------------------------------- hangjelzések

    /** Mélyebb, kettős jelzőhang minden fejezet előtt. */
    fun cueChapter(context: Context): Boolean = sp(context).getBoolean("cue_chapter", true)

    fun setCueChapter(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("cue_chapter", value).apply()
    }

    /** A jelzőhangok hangereje (0..1). */
    fun cueVolume(context: Context): Float = sp(context).getFloat("cue_volume", 0.7f)

    fun setCueVolume(context: Context, value: Float) {
        sp(context).edit().putFloat("cue_volume", value).apply()
    }

    // ---------------------------------------------------------------- egyéb

    /** A fülhallgató dupla nyomására ennyi másodpercet ugrik vissza. */
    fun rewindSeconds(context: Context): Int = sp(context).getInt("rewind_seconds", 5)

    fun setRewindSeconds(context: Context, value: Int) {
        sp(context).edit().putInt("rewind_seconds", value).apply()
    }

    /** Maradjon-e ébren a képernyő az olvasóban. */
    fun keepScreenOn(context: Context): Boolean = sp(context).getBoolean("keep_screen_on", false)

    fun setKeepScreenOn(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("keep_screen_on", value).apply()
    }

    // ---------------------------------------------------------------- megjelenés

    /** Téma: "system", "light" vagy "dark". */
    fun themeMode(context: Context): String = sp(context).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(context: Context, value: String) {
        sp(context).edit().putString("theme_mode", value).apply()
    }

    /** A választott színséma azonosítója. */
    fun colorScheme(context: Context): String = sp(context).getString("color_scheme", "klasszikus") ?: "klasszikus"

    fun setColorScheme(context: Context, value: String) {
        sp(context).edit().putString("color_scheme", value).apply()
    }

    /** A kezelőfelület betűméret-szorzója (0.8–1.6). */
    fun uiScale(context: Context): Float = sp(context).getFloat("ui_scale", 1.0f)

    fun setUiScale(context: Context, value: Float) {
        sp(context).edit().putFloat("ui_scale", value).apply()
    }

    /** A felület nyelve BCP-47 címkeként; üres = a rendszer nyelve. */
    fun uiLanguage(context: Context): String = sp(context).getString("ui_language", "") ?: ""

    fun setUiLanguage(context: Context, value: String) {
        sp(context).edit().putString("ui_language", value).apply()
    }

    // ---------------------------------------------------------------- felolvasás nyelve

    /**
     * A felolvasás nyelve BCP-47 címkeként (pl. "hu-HU", "tr-TR").
     * Üres = automatikus (előbb magyar, aztán a rendszer nyelve).
     */
    fun ttsLanguage(context: Context): String = sp(context).getString("tts_language", "") ?: ""

    fun setTtsLanguage(context: Context, value: String) {
        sp(context).edit().putString("tts_language", value).apply()
    }
}
