package hu.konyvtar.tts.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppLanguages
import hu.konyvtar.tts.data.CatalogBuilder
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.ui.theme.APP_SCHEMES
import hu.konyvtar.tts.ui.theme.ThemeState
import hu.konyvtar.tts.vm.LibraryViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

/** Beállítások: adatbázis és gyökérmappa kiválasztása, cache kezelése, TTS beállítások. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onPickDb: () -> Unit,
    onPickRoot: () -> Unit
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()
    var cacheSize by remember { mutableLongStateOf(0L) }
    var cacheReload by remember { mutableLongStateOf(0L) }
    var includePdf by remember { mutableStateOf(true) }
    var cueChapter by remember { mutableStateOf(Prefs.cueChapter(context)) }
    var cueVolume by remember { mutableFloatStateOf(Prefs.cueVolume(context)) }
    var rewindSec by remember { mutableFloatStateOf(Prefs.rewindSeconds(context).toFloat()) }
    var keepScreen by remember { mutableStateOf(Prefs.keepScreenOn(context)) }
    var readerFollow by remember { mutableStateOf(Prefs.readerFollow(context)) }
    var uiScale by remember { mutableFloatStateOf(Prefs.uiScale(context)) }
    var ttsLangs by remember { mutableStateOf<List<Locale>>(emptyList()) }
    var ttsLangTag by remember { mutableStateOf(Prefs.ttsLanguage(context)) }
    var langDialogOpen by remember { mutableStateOf(false) }
    var uiLangDialogOpen by remember { mutableStateOf(false) }
    var uiLangTag by remember { mutableStateOf(Prefs.uiLanguage(context)) }

    // A rendszer TTS motorjatol lekerdezzuk, milyen nyelvek erhetok el
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsLangs = try {
                    (engine?.availableLanguages ?: emptySet())
                        .sortedBy { it.getDisplayName(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        onDispose {
            try {
                engine?.shutdown()
            } catch (_: Exception) {
            }
        }
    }
    var builtStats by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }

    LaunchedEffect(cacheReload) {
        cacheSize = withContext(Dispatchers.IO) { TextExtractor.cachedSizeBytes(context) }
    }

    // Az épített katalógus állapotának frissítése (induláskor és építés után)
    LaunchedEffect(ui.build.done, ui.build.running) {
        builtStats = withContext(Dispatchers.IO) {
            CatalogBuilder.stats(CatalogBuilder.defaultDbFile())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            // Adatbázis
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_db_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ui.db.path ?: stringResource(R.string.set_db_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (ui.db.opened) stringResource(R.string.set_db_opened, ui.db.bookCount)
                        else stringResource(R.string.set_db_closed),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.db.opened) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onPickDb) { Text(stringResource(R.string.set_db_pick)) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Gyökérmappa
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_root_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ui.currentDir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onPickRoot) { Text(stringResource(R.string.set_root_pick)) }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Katalógus építése a könyvek saját metaadataiból
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_build_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.set_build_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.set_build_source, ui.currentDir),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(
                            R.string.set_build_target,
                            CatalogBuilder.defaultDbFile().absolutePath
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    builtStats?.let { (books, files, rich) ->
                        Text(
                            text = stringResource(R.string.set_build_stats, books, files, rich),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = includePdf,
                            onCheckedChange = { includePdf = it },
                            enabled = !ui.build.running
                        )
                        Text(
                            text = stringResource(R.string.set_build_pdf),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (ui.build.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.set_build_progress,
                                ui.build.scanned, ui.build.added, ui.build.skipped
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = ui.build.currentFile,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { vm.cancelBuild() }) {
                            Text(stringResource(R.string.common_abort))
                        }
                    } else {
                        Button(onClick = { vm.buildCatalog(includePdf) }) {
                            Text(
                                stringResource(
                                    if (builtStats == null) R.string.set_build_start
                                    else R.string.set_build_update
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Gyorsítótárak
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_cache_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.set_cache_scanned, ui.cachedTotal, ui.cachedMatched),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.set_cache_text, fmtSize(cacheSize)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedButton(onClick = {
                            Thread {
                                hu.konyvtar.tts.data.AppDb.clearScanCache()
                            }.start()
                            Toast.makeText(
                                context,
                                context.getString(R.string.set_cache_scan_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                            vm.refresh()
                        }) { Text(stringResource(R.string.set_cache_clear_scan)) }
                        Spacer(Modifier.height(0.dp))
                        Spacer(modifier = Modifier.padding(4.dp))
                        OutlinedButton(onClick = {
                            Thread {
                                TextExtractor.clearCache(context)
                            }.start()
                            cacheReload++
                            Toast.makeText(
                                context,
                                context.getString(R.string.set_cache_text_cleared),
                                Toast.LENGTH_SHORT
                            ).show()
                        }) { Text(stringResource(R.string.set_cache_clear_text)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Megjelenes
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_look_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "system" to stringResource(R.string.set_theme_system),
                            "light" to stringResource(R.string.set_theme_light),
                            "dark" to stringResource(R.string.set_theme_dark)
                        ).forEach { pair ->
                            FilterChip(
                                selected = ThemeState.mode == pair.first,
                                onClick = {
                                    ThemeState.mode = pair.first
                                    Prefs.setThemeMode(context, pair.first)
                                },
                                label = {
                                    Text(pair.second, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.set_color_scheme), style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        APP_SCHEMES.forEach { sch ->
                            FilterChip(
                                selected = ThemeState.schemeId == sch.id,
                                onClick = {
                                    ThemeState.schemeId = sch.id
                                    Prefs.setColorScheme(context, sch.id)
                                },
                                label = {
                                    Text(
                                        stringResource(sch.nameRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.set_ui_scale, (uiScale * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Slider(
                        value = uiScale,
                        onValueChange = { uiScale = it },
                        onValueChangeFinished = {
                            Prefs.setUiScale(context, uiScale)
                            ThemeState.uiScale = uiScale
                        },
                        valueRange = 0.8f..1.6f,
                        steps = 15
                    )
                    Text(
                        text = stringResource(R.string.set_ui_scale_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // A felulet nyelve
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_ui_lang_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (uiLangTag.isBlank()) stringResource(R.string.set_ui_lang_system)
                        else AppLanguages.nameOf(uiLangTag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.set_ui_lang_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { uiLangDialogOpen = true }) {
                        Text(stringResource(R.string.set_ui_lang_pick))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Felolvasas nyelve
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_tts_lang_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (ttsLangTag.isBlank()) {
                            stringResource(R.string.set_tts_lang_auto)
                        } else {
                            val l = Locale.forLanguageTag(ttsLangTag)
                            l.getDisplayName(l)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (ttsLangs.isEmpty()) stringResource(R.string.set_tts_lang_loading)
                        else stringResource(R.string.set_tts_lang_count, ttsLangs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { langDialogOpen = true }) {
                            Text(stringResource(R.string.set_tts_lang_pick))
                        }
                        OutlinedButton(onClick = {
                            try {
                                val i = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(i)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.set_tts_download_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text(stringResource(R.string.set_tts_lang_download)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Szkenneles
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_scan_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.set_scan_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.set_scan_stats, ui.cachedTotal, ui.cachedMatched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (ui.scan.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.set_scan_progress, ui.scan.filesFound, ui.scan.matched
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                        OutlinedButton(onClick = { vm.cancelScan() }) {
                            Text(stringResource(R.string.common_abort))
                        }
                    } else {
                        Button(onClick = { vm.startScan() }) { Text(stringResource(R.string.set_scan_start)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Hangjelzések
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_cue_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.set_cue_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = cueChapter, onCheckedChange = {
                            cueChapter = it; Prefs.setCueChapter(context, it)
                        })
                        Text(
                            stringResource(R.string.set_cue_chapter),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.set_cue_volume),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(74.dp)
                        )
                        Slider(
                            value = cueVolume,
                            onValueChange = { cueVolume = it },
                            onValueChangeFinished = { Prefs.setCueVolume(context, cueVolume) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(cueVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Olvasás és vezérlés
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_reading_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = readerFollow, onCheckedChange = {
                            readerFollow = it; Prefs.setReaderFollow(context, it)
                        })
                        Text(
                            stringResource(R.string.set_reading_follow),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = keepScreen, onCheckedChange = {
                            keepScreen = it; Prefs.setKeepScreenOn(context, it)
                        })
                        Text(
                            stringResource(R.string.set_reading_keep_screen),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.set_reading_rewind, rewindSec.toInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = rewindSec,
                        onValueChange = { rewindSec = it },
                        onValueChangeFinished = { Prefs.setRewindSeconds(context, rewindSec.toInt()) },
                        valueRange = 3f..30f,
                        steps = 26
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // TTS
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.set_tts_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.set_tts_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        try {
                            val intent = Intent("com.android.settings.TTS_SETTINGS")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.set_tts_open_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text(stringResource(R.string.set_tts_open)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------------------------------------------------------- felulet nyelve
    if (uiLangDialogOpen) {
        val activity = context as? android.app.Activity
        AlertDialog(
            onDismissRequest = { uiLangDialogOpen = false },
            title = {
                Text(
                    stringResource(R.string.set_ui_lang_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(onClick = {
                        uiLangTag = ""
                        Prefs.setUiLanguage(context, "")
                        uiLangDialogOpen = false
                        activity?.recreate()
                    }) { Text(stringResource(R.string.set_ui_lang_system)) }
                    AppLanguages.ALL.forEach { lang ->
                        TextButton(onClick = {
                            uiLangTag = lang.tag
                            Prefs.setUiLanguage(context, lang.tag)
                            uiLangDialogOpen = false
                            activity?.recreate()
                        }) { Text(lang.name, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { uiLangDialogOpen = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    // ---------------------------------------------------------------- nyelvvalaszto
    if (langDialogOpen) {
        AlertDialog(
            onDismissRequest = { langDialogOpen = false },
            title = {
                Text(
                    stringResource(R.string.set_tts_lang_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(onClick = {
                        ttsLangTag = ""
                        Prefs.setTtsLanguage(context, "")
                        TtsService.send(context, TtsService.ACTION_SET_LANGUAGE)
                        langDialogOpen = false
                    }) { Text(stringResource(R.string.set_tts_lang_auto)) }
                    if (ttsLangs.isEmpty()) {
                        Text(
                            stringResource(R.string.set_tts_lang_none),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    ttsLangs.forEach { loc ->
                        TextButton(onClick = {
                            ttsLangTag = loc.toLanguageTag()
                            Prefs.setTtsLanguage(context, ttsLangTag)
                            TtsService.send(context, TtsService.ACTION_SET_LANGUAGE)
                            langDialogOpen = false
                        }) {
                            Text(
                                text = loc.getDisplayName(loc) + "  (" + loc.toLanguageTag() + ")",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { langDialogOpen = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    // ---------------------------------------------------------------- építés eredménye
    val build = ui.build
    if (!build.running && (build.done || build.cancelled || build.error != null)) {
        AlertDialog(
            onDismissRequest = { vm.clearBuildResult() },
            title = {
                Text(
                    when {
                        build.error != null -> stringResource(R.string.common_error)
                        build.cancelled -> stringResource(R.string.build_cancelled_title)
                        else -> stringResource(R.string.build_done_title)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    if (build.error != null) {
                        Text(build.error!!, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            text = stringResource(
                                R.string.build_result,
                                build.scanned, build.added, build.newBooks, build.skipped
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        build.dbPath?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (build.error == null && build.dbPath != null) {
                    TextButton(onClick = {
                        vm.openDb(build.dbPath!!)
                        vm.clearBuildResult()
                    }) { Text(stringResource(R.string.build_use_it)) }
                } else {
                    TextButton(onClick = { vm.clearBuildResult() }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            },
            dismissButton = {
                if (build.error == null && build.dbPath != null) {
                    TextButton(onClick = { vm.clearBuildResult() }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        )
    }
}
