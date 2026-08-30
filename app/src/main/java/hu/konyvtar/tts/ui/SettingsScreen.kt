package hu.konyvtar.tts.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppLanguages
import hu.konyvtar.tts.data.Catalog
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.ui.theme.APP_SCHEMES
import hu.konyvtar.tts.ui.theme.ThemeState
import hu.konyvtar.tts.vm.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Beállítások: katalógus és könyvmappa, megjelenés, nyelvek, felolvasás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
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

    LaunchedEffect(cacheReload) {
        cacheSize = withContext(Dispatchers.IO) { TextExtractor.cachedSizeBytes(context) }
    }

    // A rendszer TTS motorjától lekérdezzük, milyen nyelvek érhetők el
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
            // ---------------------------------------------------------- könyvmappa
            SettingsCard(stringResource(R.string.set_root_title)) {
                Text(
                    text = ui.rootPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = onPickRoot) { Text(stringResource(R.string.set_root_pick)) }
            }

            // ---------------------------------------------------------- katalógus
            SettingsCard(stringResource(R.string.catalog_title)) {
                Text(
                    text = stringResource(R.string.set_scan_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.catalog_stats, ui.catalogBooks, ui.catalogFiles
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        R.string.catalog_file_hint, Catalog.file().absolutePath
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = includePdf,
                        onCheckedChange = { includePdf = it },
                        enabled = !ui.scan.running
                    )
                    Text(
                        stringResource(R.string.set_build_pdf),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (ui.scan.running) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.set_build_progress,
                            ui.scan.scanned, ui.scan.added, ui.scan.skipped
                        ),
                        style = MaterialTheme.typography.labelSmall
                    )
                    OutlinedButton(onClick = { vm.cancelScan() }) {
                        Text(stringResource(R.string.common_abort))
                    }
                } else {
                    Button(onClick = { vm.startScan(includePdf) }) {
                        Text(stringResource(R.string.setup_scan_button))
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = {
                        vm.removeMissing { removed ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.remove_missing_done, removed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) { Text(stringResource(R.string.remove_missing)) }
                }
            }

            // ---------------------------------------------------------- megjelenés
            SettingsCard(stringResource(R.string.set_look_title)) {
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
                Text(
                    stringResource(R.string.set_color_scheme),
                    style = MaterialTheme.typography.labelSmall
                )
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

            // ---------------------------------------------------------- felület nyelve
            SettingsCard(stringResource(R.string.set_ui_lang_title)) {
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

            // ---------------------------------------------------------- felolvasás nyelve
            SettingsCard(stringResource(R.string.set_tts_lang_title)) {
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

            // ---------------------------------------------------------- hangjelzések
            SettingsCard(stringResource(R.string.set_cue_title)) {
                Text(
                    text = stringResource(R.string.set_cue_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            // ---------------------------------------------------------- olvasás és vezérlés
            SettingsCard(stringResource(R.string.set_reading_title)) {
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

            // ---------------------------------------------------------- gyorsítótár
            SettingsCard(stringResource(R.string.set_cache_title)) {
                Text(
                    text = stringResource(R.string.set_cache_text, fmtSize(cacheSize)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    Thread { TextExtractor.clearCache(context) }.start()
                    cacheReload++
                    Toast.makeText(
                        context,
                        context.getString(R.string.set_cache_text_cleared),
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text(stringResource(R.string.set_cache_clear_text)) }
            }

            // ---------------------------------------------------------- TTS motor
            SettingsCard(stringResource(R.string.set_tts_title)) {
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

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------------------------------------------------------- felület nyelve
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

    // ---------------------------------------------------------------- felolvasás nyelve
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

    // ---------------------------------------------------------------- beolvasás eredménye
    val scan = ui.scan
    if (!scan.running && (scan.done || scan.cancelled || scan.error != null)) {
        AlertDialog(
            onDismissRequest = { vm.clearScanResult() },
            title = {
                Text(
                    when {
                        scan.error != null -> stringResource(R.string.common_error)
                        scan.cancelled -> stringResource(R.string.build_cancelled_title)
                        else -> stringResource(R.string.build_done_title)
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                if (scan.error != null) {
                    Text(scan.error!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = stringResource(
                            R.string.build_result,
                            scan.scanned, scan.added, scan.newBooks, scan.skipped
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearScanResult() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }
}

/** Egységes kártya a beállítások szakaszaihoz. */
@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
    Spacer(Modifier.height(8.dp))
}
