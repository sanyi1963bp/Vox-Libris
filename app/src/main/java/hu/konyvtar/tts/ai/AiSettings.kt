package hu.konyvtar.tts.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Az AI-beszélgetés beállításai.
 *
 * A kulcsok a telefon biztonsági tárolójával (Android Keystore) titkosítva
 * kerülnek a beállítások közé: a titkosító kulcs sosem hagyja el a
 * készüléket, tehát egy kimásolt beállításfájlból sem olvasható ki az
 * API-kulcs. Ha a visszafejtés nem sikerül (pl. másik telefonra
 * visszaállított mentés), a kulcsot egyszerűen újra meg kell adni.
 */
object AiSettings {

    private const val NAME = "vox_ai_prefs"
    private const val KS_ALIAS = "vox_ai_key"

    /** Megszemélyesítések. A „custom" a felhasználó saját leírása. */
    const val PERSONA_COMPANION = "companion"
    const val PERSONA_TEACHER = "teacher"
    const val PERSONA_AUTHOR = "author"
    const val PERSONA_CHARACTER = "character"
    const val PERSONA_LIBRARIAN = "librarian"
    const val PERSONA_CUSTOM = "custom"

    val PERSONAS = listOf(
        PERSONA_COMPANION, PERSONA_TEACHER, PERSONA_AUTHOR,
        PERSONA_CHARACTER, PERSONA_LIBRARIAN, PERSONA_CUSTOM
    )

    /** Válaszhossz. */
    const val LENGTH_SHORT = "short"
    const val LENGTH_MEDIUM = "medium"
    const val LENGTH_LONG = "long"

    /** Mennyi szöveget kapjon meg a modell a könyvből. */
    const val CONTEXT_SMALL = "small"
    const val CONTEXT_MEDIUM = "medium"
    const val CONTEXT_LARGE = "large"

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ szolgáltató

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun provider(c: Context): AiProvider = AiProvider.of(sp(c).getString("provider", null))
    fun setProvider(c: Context, p: AiProvider) = sp(c).edit().putString("provider", p.id).apply()

    fun model(c: Context, p: AiProvider): String =
        sp(c).getString("model_${p.id}", null)?.ifBlank { null } ?: p.defaultModel

    fun setModel(c: Context, p: AiProvider, v: String) =
        sp(c).edit().putString("model_${p.id}", v.trim()).apply()

    fun apiKey(c: Context, p: AiProvider): String {
        val stored = sp(c).getString("key_${p.id}", null) ?: return ""
        return decrypt(stored) ?: ""
    }

    fun setApiKey(c: Context, p: AiProvider, key: String) {
        val k = key.trim()
        val e = sp(c).edit()
        if (k.isEmpty()) {
            e.remove("key_${p.id}")
        } else {
            // A Keystore ritkán, de elhasalhat (sérült kulcstár, gyártói hiba).
            // Emiatt nem omolhat össze a Beállítások képernyő: a kulcs ilyenkor
            // nem mentődik el, a teszt pedig jelzi, hogy nincs megadva.
            val enc = try { encrypt(k) } catch (_: Exception) { null }
            if (enc == null) e.remove("key_${p.id}") else e.putString("key_${p.id}", enc)
        }
        e.apply()
    }

    fun hasKey(c: Context, p: AiProvider = provider(c)): Boolean = apiKey(c, p).isNotBlank()

    /** Kész-e a beszélgetésre: be van kapcsolva és van kulcs. */
    fun ready(c: Context): Boolean = enabled(c) && hasKey(c)

    // ------------------------------------------------------------ jellemzők

    /** Hogyan szólítsa a felhasználót (üres = nem szólítja néven). */
    fun userName(c: Context): String = sp(c).getString("user_name", "") ?: ""
    fun setUserName(c: Context, v: String) = sp(c).edit().putString("user_name", v.trim()).apply()

    fun persona(c: Context): String = sp(c).getString("persona", PERSONA_COMPANION) ?: PERSONA_COMPANION
    fun setPersona(c: Context, v: String) = sp(c).edit().putString("persona", v).apply()

    /** A „szereplő" megszemélyesítésnél: melyik szereplő (üres = a főszereplő). */
    fun characterName(c: Context): String = sp(c).getString("character_name", "") ?: ""
    fun setCharacterName(c: Context, v: String) = sp(c).edit().putString("character_name", v.trim()).apply()

    /** Saját megszemélyesítés szövege. */
    fun customPersona(c: Context): String = sp(c).getString("custom_persona", "") ?: ""
    fun setCustomPersona(c: Context, v: String) = sp(c).edit().putString("custom_persona", v).apply()

    fun length(c: Context): String = sp(c).getString("length", LENGTH_MEDIUM) ?: LENGTH_MEDIUM
    fun setLength(c: Context, v: String) = sp(c).edit().putString("length", v).apply()

    /** Hány tokenre kérjük a választ (a szolgáltatók ezt még szoroznak). */
    fun maxTokens(c: Context): Int = when (length(c)) {
        LENGTH_SHORT -> 400
        LENGTH_LONG -> 2000
        else -> 900
    }

    fun contextSize(c: Context): String = sp(c).getString("context", CONTEXT_MEDIUM) ?: CONTEXT_MEDIUM
    fun setContextSize(c: Context, v: String) = sp(c).edit().putString("context", v).apply()

    /** Ennyi karakternyi eddig olvasott szöveget küldünk a könyvből. */
    fun contextChars(c: Context): Int = when (contextSize(c)) {
        CONTEXT_SMALL -> 3_000
        CONTEXT_LARGE -> 120_000
        else -> 20_000
    }

    /** Spoilervédelem: a modell csak addig beszélhet, ahol a felhasználó tart. */
    fun spoilerGuard(c: Context): Boolean = sp(c).getBoolean("spoiler_guard", true)
    fun setSpoilerGuard(c: Context, v: Boolean) = sp(c).edit().putBoolean("spoiler_guard", v).apply()

    /** Olvassa-e fel az app a válaszokat. */
    fun speakAnswers(c: Context): Boolean = sp(c).getBoolean("speak_answers", false)
    fun setSpeakAnswers(c: Context, v: Boolean) = sp(c).edit().putBoolean("speak_answers", v).apply()

    /** Szabad szöveges kiegészítés az alappromptokhoz („mindig tegezz", stb.). */
    fun extraInstructions(c: Context): String = sp(c).getString("extra", "") ?: ""
    fun setExtraInstructions(c: Context, v: String) = sp(c).edit().putString("extra", v).apply()

    // ------------------------------------------------------------ titkosítás

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KS_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KS_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val (ivB, dataB) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, secretKey(),
            GCMParameterSpec(128, Base64.decode(ivB, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(dataB, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
