package hu.konyvtar.tts.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * A felhős AI-szolgáltatók, akikkel a könyvről beszélgetni lehet.
 *
 * Mindegyiket a felhasználó SAJÁT kulcsával szólítjuk meg — ez a
 * „bejelentkezés". Nincs saját szerverünk: a kérés a telefonról egyenesen a
 * szolgáltatóhoz megy, és csak akkor, ha a felhasználó kérdez valamit.
 */
enum class AiProvider(
    val id: String,
    val label: String,
    /** Alapértelmezett modell — álnév, hogy a szolgáltató frissíthesse. */
    val defaultModel: String,
    /** Ahol kulcsot lehet szerezni. */
    val keyUrl: String,
    /** Rövid megjegyzés a választóhoz. */
    val free: Boolean
) {
    GEMINI(
        "gemini", "Google Gemini", "gemini-flash-latest",
        "https://aistudio.google.com/apikey", free = true
    ),
    CLAUDE(
        "claude", "Anthropic Claude", "claude-haiku-4-5",
        "https://console.anthropic.com/settings/keys", free = false
    ),
    OPENAI(
        "openai", "OpenAI ChatGPT", "gpt-5-mini",
        "https://platform.openai.com/api-keys", free = false
    );

    companion object {
        fun of(id: String?): AiProvider = entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

/** Egy üzenet a beszélgetésben. */
data class ChatMessage(val fromUser: Boolean, val text: String)

/** Érthető hibák — a felület ezekből mond magyar (vagy más nyelvű) szöveget. */
sealed class AiError(message: String) : Exception(message) {
    class NoKey : AiError("no key")
    class BadKey(detail: String) : AiError(detail)
    class Quota(detail: String) : AiError(detail)
    class BadModel(detail: String) : AiError(detail)
    class Blocked(detail: String) : AiError(detail)
    class Network(detail: String) : AiError(detail)
    class Other(detail: String) : AiError(detail)
}

/**
 * Egyszerű, függőség nélküli HTTP-kliens a három szolgáltatóhoz.
 *
 * Szándékosan nem húzunk be SDK-t: három JSON-kérésről van szó, és így az
 * APK mérete sem nő. Minden hívás blokkoló — IO szálról kell hívni.
 */
object AiClient {

    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 90_000

    /**
     * Választ kér a modelltől.
     *
     * @param system az alapprompt (megszemélyesítés + a könyv adatai)
     * @param history az eddigi beszélgetés, az utolsó elem a friss kérdés
     */
    fun chat(
        provider: AiProvider,
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMessage>,
        maxTokens: Int
    ): String {
        if (apiKey.isBlank()) throw AiError.NoKey()
        val m = model.ifBlank { provider.defaultModel }
        return when (provider) {
            AiProvider.GEMINI -> gemini(apiKey, m, system, history, maxTokens)
            AiProvider.CLAUDE -> claude(apiKey, m, system, history, maxTokens)
            AiProvider.OPENAI -> openai(apiKey, m, system, history, maxTokens)
        }
    }

    /** A kulccsal elérhető, csevegésre alkalmas modellek nevei. */
    fun listModels(provider: AiProvider, apiKey: String): List<String> {
        if (apiKey.isBlank()) throw AiError.NoKey()
        return when (provider) {
            AiProvider.GEMINI -> {
                val out = ArrayList<String>()
                var pageToken: String? = null
                do {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=200" +
                        (pageToken?.let { "&pageToken=$it" } ?: "")
                    val json = request("GET", url, mapOf("x-goog-api-key" to apiKey), null)
                    val arr = json.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val methods = o.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                        val name = o.optString("name").removePrefix("models/")
                        if ("generateContent" in methods && name.startsWith("gemini") &&
                            !name.contains("embedding") && !name.contains("image") &&
                            !name.contains("tts") && !name.contains("audio")
                        ) out.add(name)
                    }
                    pageToken = json.optString("nextPageToken").ifBlank { null }
                } while (pageToken != null)
                out.sorted()
            }
            AiProvider.CLAUDE -> {
                val json = request(
                    "GET", "https://api.anthropic.com/v1/models?limit=100",
                    claudeHeaders(apiKey), null
                )
                val arr = json.optJSONArray("data") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                    .filter { it.isNotBlank() }
            }
            AiProvider.OPENAI -> {
                val json = request(
                    "GET", "https://api.openai.com/v1/models",
                    mapOf("Authorization" to "Bearer $apiKey"), null
                )
                val arr = json.optJSONArray("data") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                    .filter {
                        it.startsWith("gpt") && !it.contains("audio") && !it.contains("realtime") &&
                            !it.contains("image") && !it.contains("transcribe") && !it.contains("tts") &&
                            !it.contains("search")
                    }
                    .sorted()
            }
        }
    }

    // ------------------------------------------------------------------ Gemini

    private fun gemini(
        key: String, model: String, system: String, history: List<ChatMessage>, maxTokens: Int
    ): String {
        val contents = JSONArray()
        for (msg in history) {
            contents.put(JSONObject().apply {
                put("role", if (msg.fromUser) "user" else "model")
                put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
            })
        }
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            // A gondolkodó modellek a keretből gondolkodásra is költenek,
            // ezért bőven hagyunk helyet — a válasz hosszát a prompt szabja meg.
            put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens * 4))
            // Irodalomról beszélgetünk: egy háborús regény vagy krimi miatt
            // ne hallgasson el a modell. A legsúlyosabb tartalmat a Google
            // ettől függetlenül is szűri.
            val safety = JSONArray()
            for (cat in listOf(
                "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"
            )) safety.put(JSONObject().put("category", cat).put("threshold", "BLOCK_ONLY_HIGH"))
            put("safetySettings", safety)
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val json = request("POST", url, mapOf("x-goog-api-key" to key), body)

        json.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }?.let {
            throw AiError.Blocked(it)
        }
        val cand = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiError.Other("üres válasz")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val text = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.optBoolean("thought", false)) continue
                text.append(p.optString("text"))
            }
        }
        if (text.isBlank()) {
            val reason = cand.optString("finishReason")
            if (reason == "SAFETY" || reason == "PROHIBITED_CONTENT" || reason == "RECITATION") {
                throw AiError.Blocked(reason)
            }
            throw AiError.Other(reason.ifBlank { "üres válasz" })
        }
        return text.toString().trim()
    }

    // ------------------------------------------------------------------ Claude

    private fun claudeHeaders(key: String) = mapOf(
        "x-api-key" to key,
        "anthropic-version" to "2023-06-01"
    )

    private fun claude(
        key: String, model: String, system: String, history: List<ChatMessage>, maxTokens: Int
    ): String {
        val msgs = JSONArray()
        for (msg in history) {
            msgs.put(JSONObject().put("role", if (msg.fromUser) "user" else "assistant").put("content", msg.text))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", maxTokens * 2)
            put("system", system)
            put("messages", msgs)
        }
        val json = request("POST", "https://api.anthropic.com/v1/messages", claudeHeaders(key), body)
        val content = json.optJSONArray("content") ?: throw AiError.Other("üres válasz")
        val text = StringBuilder()
        for (i in 0 until content.length()) {
            val c = content.getJSONObject(i)
            if (c.optString("type") == "text") text.append(c.optString("text"))
        }
        if (text.isBlank()) throw AiError.Other(json.optString("stop_reason", "üres válasz"))
        return text.toString().trim()
    }

    // ------------------------------------------------------------------ OpenAI

    private fun openai(
        key: String, model: String, system: String, history: List<ChatMessage>, maxTokens: Int
    ): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (msg in history) {
            msgs.put(JSONObject().put("role", if (msg.fromUser) "user" else "assistant").put("content", msg.text))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", msgs)
            // Az újabb (gondolkodó) modellek ebbe a gondolkodást is beleszámolják
            put("max_completion_tokens", maxTokens * 4)
        }
        val json = request(
            "POST", "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $key"), body
        )
        val text = json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) throw AiError.Other("üres válasz")
        return text.trim()
    }

    // ------------------------------------------------------------------ HTTP

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: JSONObject?
    ): JSONObject {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw AiError.Network(e.message ?: "")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw errorFor(code, text)
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                throw AiError.Other("hibás válasz")
            }
        } catch (e: AiError) {
            throw e
        } catch (e: UnknownHostException) {
            throw AiError.Network("nincs internet")
        } catch (e: SocketTimeoutException) {
            throw AiError.Network("időtúllépés")
        } catch (e: IOException) {
            throw AiError.Network(e.message ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** A szolgáltató hibaüzenetéből érthető hibát csinálunk. */
    private fun errorFor(code: Int, body: String): AiError {
        val msg = try {
            val o = JSONObject(body)
            val err = o.opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> o.optString("message")
            }
        } catch (_: Exception) {
            body.take(200)
        }.ifBlank { "HTTP $code" }
        val low = msg.lowercase()
        return when {
            code == 401 || code == 403 || low.contains("api key not valid") ||
                low.contains("invalid x-api-key") || low.contains("incorrect api key") -> AiError.BadKey(msg)
            code == 429 || low.contains("quota") || low.contains("credit balance") ||
                low.contains("insufficient") -> AiError.Quota(msg)
            code == 404 || low.contains("model") && (low.contains("not found") || low.contains("does not exist")) ->
                AiError.BadModel(msg)
            else -> AiError.Other(msg)
        }
    }
}
