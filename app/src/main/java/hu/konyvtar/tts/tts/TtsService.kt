package hu.konyvtar.tts.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import hu.konyvtar.tts.MainActivity
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.data.Pronounce
import hu.konyvtar.tts.model.ProgressRow
import hu.konyvtar.tts.reader.ExtractException
import hu.konyvtar.tts.reader.Sentences
import hu.konyvtar.tts.reader.TextExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Előtér-szolgáltatás: a rendszer TTS motorjával olvassa fel a könyvet,
 * bekezdésenként haladva, a pozíciót folyamatosan mentve.
 * A UI a [TtsService.state] StateFlow-t figyeli; vezérlés intent-akciókkal.
 */
class TtsService : Service(), TextToSpeech.OnInitListener {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(hu.konyvtar.tts.data.LocaleHelper.wrap(newBase))
    }

    data class PlayerState(
        val path: String? = null,
        val title: String = "",
        val author: String = "",
        val preparing: Boolean = false,
        val playing: Boolean = false,
        val paraIndex: Int = 0,
        val totalParas: Int = 0,
        val percent: Double = 0.0,
        val listenedMs: Long = 0,
        val speed: Float = 1.0f,
        val pitch: Float = 1.0f,
        val currentText: String = "",
        /** Az éppen felolvasott mondat kezdete/vége a currentText-en belül. */
        val sentStart: Int = 0,
        val sentEnd: Int = 0,
        /** Hányadik fejezetnél tartunk (0-alapú), és hány fejezet van. */
        val chapterIndex: Int = 0,
        val totalChapters: Int = 0,
        val error: String? = null
    )

    companion object {
        const val ACTION_PLAY_FILE = "hu.konyvtar.tts.PLAY_FILE"
        const val ACTION_TOGGLE = "hu.konyvtar.tts.TOGGLE"
        const val ACTION_NEXT = "hu.konyvtar.tts.NEXT"
        const val ACTION_PREV = "hu.konyvtar.tts.PREV"
        const val ACTION_NEXT_PARA = "hu.konyvtar.tts.NEXT_PARA"
        const val ACTION_PREV_PARA = "hu.konyvtar.tts.PREV_PARA"
        const val ACTION_NEXT_CHAPTER = "hu.konyvtar.tts.NEXT_CHAPTER"
        const val ACTION_PREV_CHAPTER = "hu.konyvtar.tts.PREV_CHAPTER"
        const val ACTION_STOP = "hu.konyvtar.tts.STOP"
        const val ACTION_SET_SPEED = "hu.konyvtar.tts.SET_SPEED"
        const val ACTION_SET_PITCH = "hu.konyvtar.tts.SET_PITCH"
        const val ACTION_SET_LANGUAGE = "hu.konyvtar.tts.SET_LANGUAGE"
        const val ACTION_SEEK = "hu.konyvtar.tts.SEEK"
        const val ACTION_PRONOUNCE_CHANGED = "hu.konyvtar.tts.PRONOUNCE_CHANGED"

        const val EXTRA_PATH = "path"
        const val EXTRA_TITLE = "title"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_KONYV_ID = "konyv_id"
        const val EXTRA_RESTART = "restart"
        const val EXTRA_VALUE = "value"
        const val EXTRA_INDEX = "index"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_START_CHAR = "start_char"

        private const val CHANNEL_ID = "tts_playback"
        private const val NOTIF_ID = 42

        private val _state = MutableStateFlow(PlayerState())
        val state: StateFlow<PlayerState> = _state

        /**
         * Kényelmi indító: könyv felolvasása (folytatás mentett pozícióról).
         * [startIndex] >= 0 esetén pontosan attól a bekezdéstől indul,
         * [startChar] > 0 esetén a bekezdésen belül attól a karaktertől
         * (mondatkezdettől) — csak az első elmondott bekezdésre érvényes.
         */
        fun playFile(
            context: Context,
            path: String,
            title: String,
            author: String,
            konyvId: Long?,
            restart: Boolean = false,
            startIndex: Int = -1,
            startChar: Int = 0
        ) {
            val intent = Intent(context, TtsService::class.java).apply {
                action = ACTION_PLAY_FILE
                putExtra(EXTRA_PATH, path)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_AUTHOR, author)
                if (konyvId != null) putExtra(EXTRA_KONYV_ID, konyvId)
                putExtra(EXTRA_RESTART, restart)
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_START_CHAR, startChar)
            }
            context.startForegroundService(intent)
        }

        fun send(context: Context, action: String, configure: (Intent.() -> Unit)? = null) {
            val intent = Intent(context, TtsService::class.java).apply {
                this.action = action
                configure?.invoke(this)
            }
            context.startService(intent)
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingStart: Intent? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Egy felolvasási egység: bekezdés + azon belüli mondat [start, end). */
    private data class SentenceUnit(val para: Int, val start: Int, val end: Int)

    private var paragraphs: List<String> = emptyList()
    private var chapters: List<Int> = emptyList()
    private var chapterSet: HashSet<Int> = HashSet()
    private var sentences: List<SentenceUnit> = emptyList()
    private var sentIndex = 0
    private var cumulativeChars: LongArray = LongArray(0)
    private var totalChars: Long = 1
    private var paraIndex = 0 // az aktuális mondat bekezdése (a UI-nak)
    private var konyvId: Long? = null
    private var listenedMs: Long = 0
    private var playStartedAt: Long = 0
    private var utteranceCounter = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSessionCompat? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }
    private var noisyRegistered = false

    override fun onCreate() {
        super.onCreate()
        AppDb.init(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createChannel()
        initMediaSession()
        tts = TextToSpeech(this, this)
        _state.value = _state.value.copy(
            speed = Prefs.speed(this),
            pitch = Prefs.pitch(this)
        )
    }

    /**
     * MediaSession: a fülhallgató-gombok (Bluetooth AVRCP és vezetékes
     * HEADSETHOOK) ide futnak be. Egygombos fülhallgatón:
     *  - 1 nyomás  -> onPlay/onPause (a rendszer a lejátszási állapot alapján dönt)
     *  - 2 nyomás  -> onSkipToNext (Android-konvenció) -> nálunk: 5 mp vissza
     *  - 3 nyomás  -> onSkipToPrevious -> szintén 5 mp vissza
     */
    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "KonyvtarTTS").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resume()
                }

                override fun onPause() {
                    pause()
                }

                override fun onStop() {
                    stopPlayback()
                }

                override fun onSkipToNext() {
                    rewindSeconds(Prefs.rewindSeconds(this@TtsService))
                }

                override fun onSkipToPrevious() {
                    rewindSeconds(Prefs.rewindSeconds(this@TtsService))
                }

                override fun onRewind() {
                    rewindSeconds(Prefs.rewindSeconds(this@TtsService))
                }

                override fun onFastForward() {
                    skip(1)
                }
            })
            isActive = true
        }
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val s = _state.value
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD
        val stateCode = when {
            s.playing -> PlaybackStateCompat.STATE_PLAYING
            s.path != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(stateCode, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, s.speed)
                .build()
        )
    }

    private fun updateMediaMetadata() {
        val s = _state.value
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, s.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, s.author)
                .build()
        )
    }

    /**
     * Becsült [seconds] másodpercnyi visszaugrás. A TTS-nek nincs valódi
     * idővonala, ezért a beszédsebességből becsült karakterszám alapján
     * keressük meg a cél-mondatot (mondathatárra igazítva).
     */
    private fun rewindSeconds(seconds: Int) {
        if (sentences.isEmpty()) return
        val s = _state.value
        val cps = 14f * s.speed.coerceAtLeast(0.5f) // becsült karakter/mp magyar TTS-nél
        val curIdx = sentIndex.coerceIn(0, sentences.size - 1)
        val cur = sentences[curIdx]
        val curStartGlobal = cumulativeChars[cur.para] + cur.start
        val elapsedMs = if (s.playing && playStartedAt > 0) {
            SystemClock.elapsedRealtime() - playStartedAt
        } else {
            0L
        }
        val intoSentence = ((elapsedMs / 1000f) * cps).toLong()
            .coerceAtMost((cur.end - cur.start).toLong())
        val target = (curStartGlobal + intoSentence - (seconds * cps).toLong()).coerceAtLeast(0L)
        var idx = curIdx
        while (idx > 0) {
            val u = sentences[idx]
            if (cumulativeChars[u.para] + u.start <= target) break
            idx--
        }
        // Ha épp csak elkezdődött a mondat, az elejére ugrás nem elég — egyet vissza
        if (idx == curIdx && elapsedMs < 1500 && idx > 0) {
            idx--
        }
        sentIndex = idx
        publishPosition()
        if (s.playing) speakCurrent() else saveProgress()
        updateNotification()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            val engine = tts ?: return
            applyLanguage(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    mainHandler.post { onUtteranceDone(utteranceId) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onUtteranceDone(utteranceId) }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post { onUtteranceDone(utteranceId) }
                }
            })
            pendingStart?.let {
                pendingStart = null
                handlePlayFile(it)
            }
        } else {
            _state.value = _state.value.copy(
                error = getString(R.string.err_tts_init)
            )
        }
    }

    /**
     * A felolvasás nyelve: a beállításban választott nyelv, ha van;
     * egyébként előbb magyar, végül a rendszer nyelve.
     */
    private fun applyLanguage(engine: TextToSpeech) {
        val tag = Prefs.ttsLanguage(this)
        if (tag.isNotBlank()) {
            val loc = Locale.forLanguageTag(tag)
            val r = engine.setLanguage(loc)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) return
        }
        val hu = Locale("hu", "HU")
        val res = engine.setLanguage(hu)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_FILE -> {
                // Azonnal előtérbe lépünk (Android-követelmény startForegroundService után)
                startForeground(NOTIF_ID, buildNotification())
                if (!ttsReady) {
                    pendingStart = intent
                } else {
                    handlePlayFile(intent)
                }
            }
            ACTION_TOGGLE -> if (_state.value.playing) pause() else resume()
            ACTION_NEXT -> skip(1)
            ACTION_PREV -> skip(-1)
            ACTION_NEXT_PARA -> skipParagraph(1)
            ACTION_PREV_PARA -> skipParagraph(-1)
            ACTION_NEXT_CHAPTER -> skipChapter(1)
            ACTION_PREV_CHAPTER -> skipChapter(-1)
            ACTION_STOP -> stopPlayback()
            ACTION_SET_SPEED -> {
                val v = intent.getFloatExtra(EXTRA_VALUE, 1.0f).coerceIn(0.5f, 3.0f)
                Prefs.setSpeed(this, v)
                _state.value = _state.value.copy(speed = v)
                if (_state.value.playing) restartCurrentUtterance()
            }
            ACTION_SET_LANGUAGE -> {
                tts?.let { applyLanguage(it) }
                if (_state.value.playing) restartCurrentUtterance()
            }
            ACTION_SET_PITCH -> {
                val v = intent.getFloatExtra(EXTRA_VALUE, 1.0f).coerceIn(0.5f, 2.0f)
                Prefs.setPitch(this, v)
                _state.value = _state.value.copy(pitch = v)
                if (_state.value.playing) restartCurrentUtterance()
            }
            ACTION_PRONOUNCE_CHANGED -> {
                // A szótár változott: az épp mondott mondatot újramondjuk, hogy
                // rögtön hallható legyen a javítás.
                Pronounce.invalidate()
                if (_state.value.playing) restartCurrentUtterance()
            }
            ACTION_SEEK -> {
                val idx = intent.getIntExtra(EXTRA_INDEX, -1)
                if (idx in paragraphs.indices) {
                    sentIndex = sentenceIndexFor(idx, 0)
                    publishPosition()
                    if (_state.value.playing) speakCurrent() else saveProgress()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- lejátszásvezérlés

    private fun handlePlayFile(intent: Intent) {
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: File(path).name
        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        val restart = intent.getBooleanExtra(EXTRA_RESTART, false)
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, -1)
        val startChar = intent.getIntExtra(EXTRA_START_CHAR, 0).coerceAtLeast(0)
        konyvId = if (intent.hasExtra(EXTRA_KONYV_ID)) intent.getLongExtra(EXTRA_KONYV_ID, -1).takeIf { it >= 0 } else null

        // Ha ugyanez a könyv megy éppen: folytatás vagy ugrás a kért mondatra
        if (_state.value.path == path && paragraphs.isNotEmpty() && !restart) {
            if (startIndex >= 0) {
                sentIndex = sentenceIndexFor(startIndex.coerceIn(0, paragraphs.size - 1), startChar)
                publishPosition()
                if (_state.value.playing) speakCurrent() else resume()
            } else if (!_state.value.playing) {
                resume()
            }
            return
        }

        stopSpeaking()
        accumulateListened()
        saveProgress()

        _state.value = PlayerState(
            path = path,
            title = title,
            author = author,
            preparing = true,
            speed = Prefs.speed(this),
            pitch = Prefs.pitch(this)
        )
        updateNotification()

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val book = TextExtractor.book(this@TtsService, File(path))
                    val saved = if (restart) null else AppDb.progressFor(path)
                    Triple(book, saved, null as String?)
                } catch (e: ExtractException) {
                    Triple(null, null, e.localized(this@TtsService))
                } catch (e: Exception) {
                    Triple(null, null, getString(R.string.err_extract_generic, e.message ?: "?"))
                }
            }
            val (book, saved, error) = result
            val paras = book?.paragraphs ?: emptyList()
            if (error != null || paras.isEmpty()) {
                _state.value = _state.value.copy(
                    preparing = false,
                    error = error ?: getString(R.string.err_no_text)
                )
                updateNotification()
                return@launch
            }
            paragraphs = paras
            chapters = book?.chapters ?: emptyList()
            chapterSet = HashSet(chapters)
            cumulativeChars = LongArray(paras.size + 1)
            for (i in paras.indices) {
                cumulativeChars[i + 1] = cumulativeChars[i] + paras[i].length
            }
            totalChars = maxOf(1L, cumulativeChars[paras.size])
            sentences = buildSentences(paras)
            sentIndex = if (startIndex >= 0) {
                sentenceIndexFor(startIndex.coerceIn(0, paras.size - 1), startChar)
            } else if (saved != null) {
                sentenceIndexFor(saved.paraIndex.coerceIn(0, paras.size - 1), saved.paraChar)
            } else {
                0
            }
            listenedMs = saved?.listenedMs ?: 0
            _state.value = _state.value.copy(
                preparing = false,
                totalParas = paras.size,
                totalChapters = chapters.size,
                listenedMs = listenedMs,
                error = null
            )
            publishPosition()
            updateMediaMetadata()
            resume()
        }
    }

    /** Bekezdéslista -> mondat-egységek. */
    private fun buildSentences(paras: List<String>): List<SentenceUnit> {
        val out = ArrayList<SentenceUnit>(paras.size * 4)
        for (p in paras.indices) {
            val starts = Sentences.starts(paras[p])
            for (i in starts.indices) {
                val end = if (i + 1 < starts.size) starts[i + 1] else paras[p].length
                out.add(SentenceUnit(p, starts[i], end))
            }
        }
        return out
    }

    /** A (bekezdés, karakter) helyhez tartozó mondat indexe. */
    private fun sentenceIndexFor(para: Int, char: Int): Int {
        if (sentences.isEmpty()) return 0
        var first = -1
        for (i in sentences.indices) {
            if (sentences[i].para == para) {
                first = i
                break
            }
            if (sentences[i].para > para) return i
        }
        if (first < 0) return sentences.size - 1
        var i = first
        while (i < sentences.size && sentences[i].para == para) {
            if (char < sentences[i].end) return i
            i++
        }
        return (i - 1).coerceAtLeast(0)
    }

    /** Karakterpontos haladás a mondat kezdeténél. */
    private fun percentAtSentence(index: Int): Double {
        if (sentences.isEmpty() || cumulativeChars.isEmpty()) return 0.0
        if (index >= sentences.size) return 100.0
        val u = sentences[index.coerceAtLeast(0)]
        val global = cumulativeChars[u.para] + u.start
        return global.toDouble() * 100.0 / totalChars.toDouble()
    }

    private fun resume() {
        if (paragraphs.isEmpty() || !ttsReady) return
        if (!requestFocus()) return
        registerNoisy()
        acquireWakeLock()
        playStartedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(playing = true, error = null)
        updateMediaSessionState()
        startForeground(NOTIF_ID, buildNotification())
        speakCurrent()
    }

    private fun pause() {
        if (!_state.value.playing) return
        stopSpeaking()
        accumulateListened()
        _state.value = _state.value.copy(playing = false)
        saveProgress()
        releaseWakeLock()
        abandonFocus()
        updateMediaSessionState()
        updateNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun stopPlayback() {
        stopSpeaking()
        accumulateListened()
        saveProgress()
        // Teljes leállítás után nincs betöltött könyv: a képernyők
        // indítás/szünet gombja is eltűnik, hogy ne lehessen hiába nyomni
        _state.value = PlayerState()
        releaseWakeLock()
        abandonFocus()
        unregisterNoisy()
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f)
                .build()
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun skip(delta: Int) {
        if (sentences.isEmpty()) return
        sentIndex = (sentIndex + delta).coerceIn(0, sentences.size - 1)
        publishPosition()
        if (_state.value.playing) {
            speakCurrent()
        } else {
            saveProgress()
        }
        updateNotification()
    }

    /**
     * Bekezdésugrás. Visszafelé: ha a bekezdés közepén állunk, előbb az
     * elejére ugrik (mint a zenelejátszók „előző szám" gombja).
     */
    private fun skipParagraph(delta: Int) {
        if (sentences.isEmpty() || paragraphs.isEmpty()) return
        val u = sentences[sentIndex.coerceIn(0, sentences.size - 1)]
        val target = when {
            delta > 0 -> (u.para + 1).coerceAtMost(paragraphs.size - 1)
            u.start > 0 -> u.para                       // vissza a bekezdés elejére
            else -> (u.para - 1).coerceAtLeast(0)
        }
        sentIndex = sentenceIndexFor(target, 0)
        afterJump()
    }

    /** Fejezetugrás; ha nincs fejezetadat, ~5%-nyi bekezdést lép. */
    private fun skipChapter(delta: Int) {
        if (sentences.isEmpty() || paragraphs.isEmpty()) return
        val cur = sentences[sentIndex.coerceIn(0, sentences.size - 1)].para
        val target = if (chapters.size >= 2) {
            if (delta > 0) {
                chapters.firstOrNull { it > cur } ?: (paragraphs.size - 1)
            } else {
                val start = chapters.lastOrNull { it <= cur } ?: 0
                if (cur > start) start else (chapters.lastOrNull { it < start } ?: 0)
            }
        } else {
            val step = maxOf(20, paragraphs.size / 20)
            (cur + delta * step).coerceIn(0, paragraphs.size - 1)
        }
        sentIndex = sentenceIndexFor(target, 0)
        afterJump()
    }

    private fun afterJump() {
        publishPosition()
        if (_state.value.playing) speakCurrent() else saveProgress()
        updateNotification()
    }

    private fun restartCurrentUtterance() {
        if (_state.value.playing) speakCurrent(withCue = false)
    }

    private fun speakCurrent(withCue: Boolean = true) {
        val engine = tts ?: return
        if (sentIndex !in sentences.indices) {
            // A könyv vége
            accumulateListened()
            _state.value = _state.value.copy(playing = false, percent = 100.0)
            saveProgress()
            releaseWakeLock()
            abandonFocus()
            updateMediaSessionState()
            updateNotification()
            stopForeground(STOP_FOREGROUND_DETACH)
            return
        }
        val u = sentences[sentIndex]
        val text = paragraphs[u.para].substring(u.start, u.end).trim()
        if (text.isEmpty()) {
            sentIndex++
            speakCurrent()
            return
        }
        // A kiejtési szótár csak azon a szövegen dolgozik, amit a motornak
        // átadunk. A könyv szövegéhez nem nyúlunk, így a képernyőn kiemelt
        // mondat karakterpoziciói nem csúsznak el.
        val spoken = Pronounce.apply(text, Pronounce.rules())
        engine.setSpeechRate(_state.value.speed)
        engine.setPitch(_state.value.pitch)
        utteranceCounter++
        val id = "sent_${sentIndex}_$utteranceCounter"
        val myCounter = utteranceCounter
        publishPosition()

        // Fejezetjelző hang a fejezet első mondata előtt
        var cueDelay = 0L
        if (withCue && u.start == 0 && u.para in chapterSet && Prefs.cueChapter(this)) {
            ToneCue.chapter(Prefs.cueVolume(this))
            cueDelay = ToneCue.CHAPTER_MS.toLong()
        }

        val speakNow = {
            if (utteranceCounter == myCounter) {
                engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            }
        }
        if (cueDelay > 0) {
            mainHandler.postDelayed({
                if (_state.value.playing) speakNow()
            }, cueDelay)
        } else {
            speakNow()
        }
        updateNotification()
    }

    private fun onUtteranceDone(utteranceId: String?) {
        if (utteranceId == null || !utteranceId.endsWith("_$utteranceCounter")) return
        if (!_state.value.playing) return
        accumulateListened()
        playStartedAt = SystemClock.elapsedRealtime()
        sentIndex++
        saveProgress()
        speakCurrent()
    }

    private fun publishPosition() {
        val u = sentences.getOrNull(sentIndex)
        paraIndex = u?.para ?: 0
        val chIdx = if (chapters.isEmpty()) 0
        else chapters.indexOfLast { it <= paraIndex }.coerceAtLeast(0)
        _state.value = _state.value.copy(
            paraIndex = paraIndex,
            totalParas = paragraphs.size,
            chapterIndex = chIdx,
            totalChapters = chapters.size,
            percent = percentAtSentence(sentIndex),
            listenedMs = listenedMs,
            currentText = u?.let { paragraphs[it.para] } ?: "",
            sentStart = u?.start ?: 0,
            sentEnd = u?.end ?: 0
        )
    }

    private fun stopSpeaking() {
        utteranceCounter++
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun accumulateListened() {
        if (_state.value.playing && playStartedAt > 0) {
            listenedMs += SystemClock.elapsedRealtime() - playStartedAt
        }
        playStartedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(listenedMs = listenedMs)
    }

    private fun buildProgressRow(): ProgressRow? {
        val s = _state.value
        val path = s.path ?: return null
        if (paragraphs.isEmpty() || sentences.isEmpty()) return null
        val u = sentences[sentIndex.coerceIn(0, sentences.size - 1)]
        return ProgressRow(
            path = path,
            konyvId = konyvId,
            paraIndex = u.para.coerceIn(0, paragraphs.size - 1),
            totalParas = paragraphs.size,
            percent = percentAtSentence(sentIndex),
            listenedMs = listenedMs,
            lastAccess = System.currentTimeMillis(),
            title = s.title,
            author = s.author,
            paraChar = u.start
        )
    }

    private fun saveProgress() {
        val row = buildProgressRow() ?: return
        scope.launch(Dispatchers.IO) {
            AppDb.upsertProgress(row)
        }
    }

    /** Szinkron mentés — onDestroy-ban, ahol a coroutine már nem futna le. */
    private fun saveProgressSync() {
        val row = buildProgressRow() ?: return
        try {
            AppDb.upsertProgress(row)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- audio fókusz, wake lock

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
        }
    }

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KonyvtarTTS:tts")
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun registerNoisy() {
        if (!noisyRegistered) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            noisyRegistered = true
        }
    }

    private fun unregisterNoisy() {
        if (noisyRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {
            }
            noisyRegistered = false
        }
    }

    // ---------------------------------------------------------------- értesítés

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.tts_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun servicePending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): android.app.Notification {
        val s = _state.value
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_player", true)
        }
        val contentPi = PendingIntent.getActivity(
            this, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pctText = String.format(Locale.getDefault(), "%.1f%%", s.percent)
        val sub = when {
            s.preparing -> getString(R.string.notif_preparing)
            s.totalParas > 0 -> getString(
                R.string.notif_status, s.paraIndex + 1, s.totalParas, pctText
            )
            else -> ""
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(s.title.ifEmpty { getString(R.string.app_name) })
            .setContentText(listOf(s.author, sub).filter { it.isNotEmpty() }.joinToString(" — "))
            .setContentIntent(contentPi)
            .setOngoing(s.playing)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_prev, getString(R.string.notif_prev), servicePending(ACTION_PREV, 1))
            .addAction(
                if (s.playing) R.drawable.ic_pause else R.drawable.ic_play,
                getString(if (s.playing) R.string.common_pause else R.string.common_play),
                servicePending(ACTION_TOGGLE, 2)
            )
            .addAction(R.drawable.ic_next, getString(R.string.notif_next), servicePending(ACTION_NEXT, 3))
            .addAction(R.drawable.ic_stop, getString(R.string.notif_stop), servicePending(ACTION_STOP, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- életciklus

    override fun onDestroy() {
        stopSpeaking()
        accumulateListened()
        saveProgressSync()
        unregisterNoisy()
        releaseWakeLock()
        abandonFocus()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
