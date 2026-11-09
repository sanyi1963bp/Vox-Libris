package hu.konyvtar.tts.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.ai.AiClient
import hu.konyvtar.tts.ai.AiSettings
import hu.konyvtar.tts.ai.BookContext
import hu.konyvtar.tts.ai.ChatMessage
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.reader.ExtractedBook
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Az eddigi beszélgetések, könyvenként. Csak a memóriában élnek: ha
 * visszalépsz az olvasóba és újra megnyitod, folytatódik; az app
 * bezárásával elfelejtődik — semmit nem írunk lemezre a beszélgetésből.
 */
private object ChatStore {
    private val chats = HashMap<String, SnapshotStateList<ChatMessage>>()
    fun of(path: String): SnapshotStateList<ChatMessage> = chats.getOrPut(path) { mutableStateListOf() }
}

/** Legfeljebb ennyi korábbi üzenetet küldünk vissza a modellnek. */
private const val HISTORY_LIMIT = 24

/**
 * Beszélgetés az AI-val az aktuális könyvről.
 *
 * Minden kérdésnél frissen állítjuk össze az alappromptot, hogy ha közben
 * továbbhaladt a felolvasás, az AI is tudja, hol tartasz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    path: String,
    title: String,
    author: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember(path) { ChatStore.of(path) }
    val listState = rememberLazyListState()

    val ready = remember { AiSettings.ready(context) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var book by remember(path) { mutableStateOf<ExtractedBook?>(null) }
    var seen by remember(path) { mutableStateOf<List<String>>(emptyList()) }
    var speakOn by remember { mutableStateOf(AiSettings.speakAnswers(context)) }
    var speakingIdx by remember { mutableStateOf(-1) }

    // ---------------------------------------------------------------- felolvasó
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tag = Prefs.ttsLanguage(context)
                val loc = if (tag.isNotBlank()) Locale.forLanguageTag(tag)
                else context.resources.configuration.locales[0]
                engine?.language = loc
                engine?.setSpeechRate(Prefs.speed(context))
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { speakingIdx = -1 }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speakingIdx = -1 }
                })
                tts.value = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun speak(idx: Int, text: String) {
        val engine = tts.value ?: return
        // A könyv felolvasása ne szóljon a válaszba
        val s = TtsService.state.value
        if (s.playing) TtsService.send(context, TtsService.ACTION_TOGGLE)
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai_$idx")
        speakingIdx = idx
    }

    fun stopSpeaking() {
        tts.value?.stop()
        speakingIdx = -1
    }

    // ---------------------------------------------------------------- könyv betöltése
    LaunchedEffect(path) {
        book = withContext(Dispatchers.IO) {
            try { TextExtractor.book(context, File(path)) } catch (_: Exception) { ExtractedBook(emptyList(), emptyList()) }
        }
    }

    /** Hol tart az olvasó: a felolvasás, ha ez a könyv szól; különben a mentett pozíció. */
    suspend fun position(paras: List<String>): Pair<Int, Int> {
        val s = TtsService.state.value
        if (s.path == path && s.totalParas > 0) return s.paraIndex to s.sentEnd
        val prog = withContext(Dispatchers.IO) { AppDb.progressFor(path) }
        val p = when {
            prog == null -> 0
            else -> maxOf(prog.readPara, prog.paraIndex)
        }.coerceIn(0, (paras.size - 1).coerceAtLeast(0))
        return p to (paras.getOrNull(p)?.length ?: 0)
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        val paras = book ?: return
        input = ""
        error = null
        messages.add(ChatMessage(true, q))
        busy = true
        scope.launch {
            listState.animateScrollToItem(messages.size)
            val result = withContext(Dispatchers.IO) {
                try {
                    val (pp, pc) = position(paras.paragraphs)
                    val snap = BookContext.build(
                        context, path, title, author, paras.paragraphs, paras.chapters, pp, pc
                    )
                    seen = snap.summaryLines
                    val provider = AiSettings.provider(context)
                    Result.success(
                        AiClient.chat(
                            provider = provider,
                            apiKey = AiSettings.apiKey(context, provider),
                            model = AiSettings.model(context, provider),
                            system = snap.systemPrompt,
                            history = messages.toList().takeLast(HISTORY_LIMIT)
                                .dropWhile { !it.fromUser },   // az első üzenet a felhasználóé legyen
                            maxTokens = AiSettings.maxTokens(context)
                        )
                    )
                } catch (e: Exception) {
                    Result.failure<String>(e)
                }
            }
            busy = false
            result.onSuccess { answer ->
                messages.add(ChatMessage(false, answer))
                if (speakOn) speak(messages.size - 1, answer)
            }.onFailure { e ->
                // A sikertelen kérdést visszaadjuk a beviteli mezőbe, hogy ne kelljen újragépelni
                if (messages.lastOrNull()?.fromUser == true) messages.removeAt(messages.size - 1)
                input = q
                error = aiErrorText(context, e)
            }
            listState.animateScrollToItem((messages.size).coerceAtLeast(0))
        }
    }

    // ---------------------------------------------------------------- hangalapú kérdés
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val heard = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) send(heard)
        }
    }

    fun listen() {
        stopSpeaking()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, context.resources.configuration.locales[0].toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.ai_listen_prompt))
        }
        try {
            speechLauncher.launch(i)
        } catch (_: Exception) {
            error = context.getString(R.string.ai_no_speech)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.ai_chat_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { stopSpeaking(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        speakOn = !speakOn
                        AiSettings.setSpeakAnswers(context, speakOn)
                        if (!speakOn) stopSpeaking()
                    }) {
                        Icon(
                            if (speakOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            stringResource(R.string.ai_speak)
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { stopSpeaking(); messages.clear(); error = null }) {
                            Icon(Icons.Filled.DeleteSweep, stringResource(R.string.ai_new_chat))
                        }
                    }
                    IconButton(onClick = { stopSpeaking(); onOpenSettings() }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.common_settings))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (!ready) {
                NotReadyCard(onOpenSettings)
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    IntroLine(seen)
                }
                itemsIndexed(messages) { idx, m ->
                    Bubble(
                        message = m,
                        speaking = speakingIdx == idx,
                        onClick = {
                            if (!m.fromUser) {
                                if (speakingIdx == idx) stopSpeaking() else speak(idx, m.text)
                            }
                        }
                    )
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                stringResource(R.string.ai_thinking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (messages.isEmpty() && !busy) {
                val suggestions = listOf(
                    stringResource(R.string.ai_sugg_sofar),
                    stringResource(R.string.ai_sugg_who),
                    stringResource(R.string.ai_sugg_explain),
                    stringResource(R.string.ai_sugg_author),
                    stringResource(R.string.ai_sugg_words)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (s in suggestions) {
                        SuggestionChip(onClick = { send(s) }, label = { Text(s) }, enabled = book != null)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { listen() }, enabled = !busy && book != null) {
                    Icon(Icons.Filled.Mic, stringResource(R.string.ai_listen))
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                    maxLines = 4
                )
                if (speakingIdx >= 0) {
                    IconButton(onClick = { stopSpeaking() }) {
                        Icon(Icons.Filled.StopCircle, stringResource(R.string.ai_stop_speaking))
                    }
                }
                IconButton(
                    onClick = { send(input) },
                    enabled = input.isNotBlank() && !busy && book != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ai_send))
                }
            }
        }
    }
}

@Composable
private fun IntroLine(seen: List<String>) {
    val parts = seen.mapNotNull { s ->
        when {
            s == "description" -> stringResource(R.string.ai_seen_description)
            s == "story" -> stringResource(R.string.ai_seen_story)
            s == "characters" -> stringResource(R.string.ai_seen_characters)
            s == "spoiler" -> stringResource(R.string.ai_seen_spoiler)
            s.startsWith("text:") -> stringResource(
                R.string.ai_seen_text, s.removePrefix("text:").toIntOrNull() ?: 0
            )
            else -> null
        }
    }
    Text(
        if (parts.isEmpty()) stringResource(R.string.ai_intro)
        else stringResource(R.string.ai_intro_seen, parts.joinToString(" · ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Bubble(message: ChatMessage, speaking: Boolean, onClick: () -> Unit) {
    val mine = message.fromUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    when {
                        mine -> MaterialTheme.colorScheme.primaryContainer
                        speaking -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    RoundedCornerShape(
                        topStart = 14.dp, topEnd = 14.dp,
                        bottomStart = if (mine) 14.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 14.dp
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun NotReadyCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ai_not_ready_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_not_ready_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.ai_open_settings)) }
        }
    }
}
