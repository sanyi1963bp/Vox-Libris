package hu.konyvtar.tts.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import hu.konyvtar.tts.data.LibraryScanner
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.ui.theme.APP_SCHEMES
import hu.konyvtar.tts.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * A beállítások kártyái. Mindegyik a saját állapotát viszi — a beállítások
 * úgyis a SharedPreferences-ben laknak, nincs mit fentebb emelni. Korábban
 * mind a húsz állapotváltozó egyetlen 607 soros composable tetején állt.
 */

/** Egységes kártya a beállítások szakaszaihoz. */
@Composable
fun SettingsCard(title: String, content: @Composable () -> Unit) {
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

// ---------------------------------------------------------------- könyvmappa

@Composable
fun RootFolderCard(rootPath: String, onPickRoot: () -> Unit) {
    SettingsCard(stringResource(R.string.set_root_title)) {
        Text(
            text = rootPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = onPickRoot) { Text(stringResource(R.string.set_root_pick)) }
    }
}

// ---------------------------------------------------------------- katalógus

@Composable
fun CatalogCard(
    books: Int,
    files: Int,
    scan: LibraryScanner.Progress,
    onScan: (includePdf: Boolean) -> Unit,
    onCancel: () -> Unit,
    onRemoveMissing: () -> Unit
) {
    var includePdf by remember { mutableStateOf(true) }

    SettingsCard(stringResource(R.string.catalog_title)) {
        Text(
            text = stringResource(R.string.set_scan_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.catalog_stats, books, files),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.catalog_file_hint, Catalog.file().absolutePath),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = includePdf,
                onCheckedChange = { includePdf = it },
                enabled = !scan.running
            )
            Text(
                stringResource(R.string.set_build_pdf),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(6.dp))
        if (scan.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.set_build_progress, scan.scanned, scan.added, scan.skipped
                ),
                style = MaterialTheme.typography.labelSmall
            )
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.common_abort))
            }
        } else {
            Button(onClick = { onScan(includePdf) }) {
                Text(stringResource(R.string.setup_scan_button))
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onRemoveMissing) {
                Text(stringResource(R.string.remove_missing))
            }
        }
    }
}

// ---------------------------------------------------------------- megjelenés

@Composable
fun AppearanceCard() {
    val context = LocalContext.current
    var uiScale by remember { mutableFloatStateOf(Prefs.uiScale(context)) }

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
                    label = { Text(pair.second, style = MaterialTheme.typography.labelSmall) }
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
}

// ---------------------------------------------------------------- felület nyelve

@Composable
fun UiLanguageCard() {
    val context = LocalContext.current
    var tag by remember { mutableStateOf(Prefs.uiLanguage(context)) }
    var dialogOpen by remember { mutableStateOf(false) }

    SettingsCard(stringResource(R.string.set_ui_lang_title)) {
        Text(
            text = if (tag.isBlank()) stringResource(R.string.set_ui_lang_system)
            else AppLanguages.nameOf(tag),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.set_ui_lang_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = { dialogOpen = true }) {
            Text(stringResource(R.string.set_ui_lang_pick))
        }
    }

    if (dialogOpen) {
        // A nyelvváltás után újra kell építeni az activityt, hogy a már
        // megjelenített szövegek is az új nyelven jöjjenek vissza
        val activity = context as? android.app.Activity
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                        tag = ""
                        Prefs.setUiLanguage(context, "")
                        dialogOpen = false
                        activity?.recreate()
                    }) { Text(stringResource(R.string.set_ui_lang_system)) }
                    AppLanguages.ALL.forEach { lang ->
                        TextButton(onClick = {
                            tag = lang.tag
                            Prefs.setUiLanguage(context, lang.tag)
                            dialogOpen = false
                            activity?.recreate()
                        }) { Text(lang.name, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- felolvasás nyelve

@Composable
fun NarrationLanguageCard() {
    val context = LocalContext.current
    var languages by remember { mutableStateOf<List<Locale>>(emptyList()) }
    var tag by remember { mutableStateOf(Prefs.ttsLanguage(context)) }
    var dialogOpen by remember { mutableStateOf(false) }

    // A rendszer TTS motorjától kérdezzük meg, milyen nyelvek érhetők el
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                languages = try {
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

    SettingsCard(stringResource(R.string.set_tts_lang_title)) {
        Text(
            text = if (tag.isBlank()) stringResource(R.string.set_tts_lang_auto)
            else Locale.forLanguageTag(tag).let { it.getDisplayName(it) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (languages.isEmpty()) stringResource(R.string.set_tts_lang_loading)
            else stringResource(R.string.set_tts_lang_count, languages.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dialogOpen = true }) {
                Text(stringResource(R.string.set_tts_lang_pick))
            }
            OutlinedButton(onClick = {
                openSystem(
                    context,
                    Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                    R.string.set_tts_download_failed
                )
            }) { Text(stringResource(R.string.set_tts_lang_download)) }
        }
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
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
                        tag = ""
                        Prefs.setTtsLanguage(context, "")
                        TtsService.send(context, TtsService.ACTION_SET_LANGUAGE)
                        dialogOpen = false
                    }) { Text(stringResource(R.string.set_tts_lang_auto)) }
                    if (languages.isEmpty()) {
                        Text(
                            stringResource(R.string.set_tts_lang_none),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    languages.forEach { loc ->
                        TextButton(onClick = {
                            tag = loc.toLanguageTag()
                            Prefs.setTtsLanguage(context, tag)
                            TtsService.send(context, TtsService.ACTION_SET_LANGUAGE)
                            dialogOpen = false
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
                TextButton(onClick = { dialogOpen = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- hangjelzések

@Composable
fun AudioCuesCard() {
    val context = LocalContext.current
    var cueChapter by remember { mutableStateOf(Prefs.cueChapter(context)) }
    var cueVolume by remember { mutableFloatStateOf(Prefs.cueVolume(context)) }

    SettingsCard(stringResource(R.string.set_cue_title)) {
        Text(
            text = stringResource(R.string.set_cue_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = cueChapter, onCheckedChange = {
                cueChapter = it
                Prefs.setCueChapter(context, it)
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

// ---------------------------------------------------------------- olvasás és vezérlés

@Composable
fun ReadingCard() {
    val context = LocalContext.current
    var follow by remember { mutableStateOf(Prefs.readerFollow(context)) }
    var keepScreen by remember { mutableStateOf(Prefs.keepScreenOn(context)) }
    var rewindSec by remember { mutableFloatStateOf(Prefs.rewindSeconds(context).toFloat()) }

    SettingsCard(stringResource(R.string.set_reading_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = follow, onCheckedChange = {
                follow = it
                Prefs.setReaderFollow(context, it)
            })
            Text(
                stringResource(R.string.set_reading_follow),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = keepScreen, onCheckedChange = {
                keepScreen = it
                Prefs.setKeepScreenOn(context, it)
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

// ---------------------------------------------------------------- gyorsítótár

@Composable
fun CacheCard() {
    val context = LocalContext.current
    var size by remember { mutableLongStateOf(0L) }
    var reload by remember { mutableLongStateOf(0L) }

    LaunchedEffect(reload) {
        size = withContext(Dispatchers.IO) { TextExtractor.cachedSizeBytes(context) }
    }

    SettingsCard(stringResource(R.string.set_cache_title)) {
        Text(
            text = stringResource(R.string.set_cache_text, fmtSize(size)),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            Thread { TextExtractor.clearCache(context) }.start()
            reload++
            Toast.makeText(
                context,
                context.getString(R.string.set_cache_text_cleared),
                Toast.LENGTH_SHORT
            ).show()
        }) { Text(stringResource(R.string.set_cache_clear_text)) }
    }
}

// ---------------------------------------------------------------- TTS motor

@Composable
fun TtsEngineCard() {
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.set_tts_title)) {
        Text(
            text = stringResource(R.string.set_tts_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            openSystem(
                context,
                Intent("com.android.settings.TTS_SETTINGS"),
                R.string.set_tts_open_failed
            )
        }) { Text(stringResource(R.string.set_tts_open)) }
    }
}

/** Rendszerképernyő megnyitása; ha nincs ilyen a készüléken, szólunk róla. */
private fun openSystem(context: android.content.Context, intent: Intent, failedRes: Int) {
    try {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(failedRes), Toast.LENGTH_LONG).show()
    }
}
