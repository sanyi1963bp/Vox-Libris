package hu.konyvtar.tts.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * A felület nyelvei. Egy nyelv hozzáadásához elég egy sort felvenni ide,
 * és létrehozni a res/values-<kód>/strings.xml fájlt.
 */
object AppLanguages {

    /** [tag] = BCP-47 nyelvi címke, [name] = a nyelv saját neve. */
    data class Lang(val tag: String, val name: String)

    val ALL: List<Lang> = listOf(
        Lang("hu", "Magyar"),
        Lang("en", "English"),
        Lang("de", "Deutsch"),
        Lang("fr", "Français"),
        Lang("es", "Español"),
        Lang("pt", "Português"),
        Lang("pl", "Polski"),
        Lang("cs", "Čeština"),
        Lang("sk", "Slovenčina"),
        Lang("ru", "Русский")
    )

    fun nameOf(tag: String): String =
        ALL.firstOrNull { it.tag == tag }?.name ?: tag
}

/**
 * A választott felületnyelv alkalmazása. Az Activity és a Service is ezen
 * keresztül kapja meg a helyes nyelvű erőforrásokat, ezért a nyelvváltás
 * a rendszer nyelvétől függetlenül működik (Android 11-től felfelé is).
 */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val tag = try {
            Prefs.uiLanguage(base)
        } catch (e: Exception) {
            ""
        }
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
