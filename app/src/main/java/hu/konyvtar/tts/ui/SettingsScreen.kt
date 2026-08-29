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
                title = { Text("Beállítások", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
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
                        "Katalógus-adatbázis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ui.db.path ?: "Nincs kiválasztva (másold a telefonra a ncore_konyvtar.db fájlt)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (ui.db.opened)
                            "Megnyitva — ${ui.db.bookCount} könyv"
                        else
                            "Nincs megnyitva",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ui.db.opened) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onPickDb) { Text("Adatbázisfájl kiválasztása…") }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Gyökérmappa
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Könyvek gyökérmappája",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ui.currentDir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = onPickRoot) { Text("Gyökérmappa kiválasztása…") }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Katalógus építése a könyvek saját metaadataiból
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Katalógus építése a könyvekből",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ha nincs kész katalógusod, az app a könyvfájlok saját " +
                            "metaadataiból (cím, szerző, fülszöveg) készít egyet — internet nélkül. " +
                            "Újrafuttatva a meglévő bejegyzéseket békén hagyja, csak az új " +
                            "könyveket veszi fel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Forrás: ${ui.currentDir}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "Cél: ${CatalogBuilder.defaultDbFile().absolutePath}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    builtStats?.let { (books, files, rich) ->
                        Text(
                            text = "Jelenleg: $books könyv, $files fájl (ebből $rich valódi metaadattal)",
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
                            text = "  PDF-ek metaadata is (lassabb)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (ui.build.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${ui.build.scanned} fájl • ${ui.build.added} új bejegyzés • " +
                                "${ui.build.skipped} kihagyva",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = ui.build.currentFile,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = { vm.cancelBuild() }) { Text("Megszakítás") }
                    } else {
                        Button(onClick = { vm.buildCatalog(includePdf) }) {
                            Text(if (builtStats == null) "Katalógus építése" else "Frissítés az új könyvekkel")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Gyorsítótárak
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Gyorsítótárak",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Szkennelt fájlok: ${ui.cachedTotal} (${ui.cachedMatched} párosítva)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Kinyert szövegek: ${fmtSize(cacheSize)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        OutlinedButton(onClick = {
                            Thread {
                                hu.konyvtar.tts.data.AppDb.clearScanCache()
                            }.start()
                            Toast.makeText(context, "Szkennelési gyorsítótár törölve.", Toast.LENGTH_SHORT).show()
                            vm.refresh()
                        }) { Text("Szkennelés törlése") }
                        Spacer(Modifier.height(0.dp))
                        Spacer(modifier = Modifier.padding(4.dp))
                        OutlinedButton(onClick = {
                            Thread {
                                TextExtractor.clearCache(context)
                            }.start()
                            cacheReload++
                            Toast.makeText(context, "Szöveg-gyorsítótár törölve.", Toast.LENGTH_SHORT).show()
                        }) { Text("Szövegek törlése") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Megjelenes
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Megjelenés",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Téma", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "system" to "Rendszer szerint",
                            "light" to "Világos",
                            "dark" to "Sötét"
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
                    Text("Színséma", style = MaterialTheme.typography.labelSmall)
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
                                    Text(sch.name, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "A kezelőfelület betűmérete: " + (uiScale * 100).toInt() + "%",
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
                        text = "A könyv szövegének betűmérete külön állítható az olvasóban, " +
                            "a hangoló gomb alatti A- és A+ gombokkal.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Felolvasas nyelve
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Felolvasás nyelve",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (ttsLangTag.isBlank()) {
                            "Automatikus (magyar, ha elérhető)"
                        } else {
                            val l = Locale.forLanguageTag(ttsLangTag)
                            l.getDisplayName(l)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (ttsLangs.isEmpty()) {
                            "A telepített hangok lekérdezése…"
                        } else {
                            ttsLangs.size.toString() + " nyelv érhető el a telepített TTS motorral."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { langDialogOpen = true }) { Text("Nyelv választása…") }
                        OutlinedButton(onClick = {
                            try {
                                val i = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(i)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "A hangletöltő nem érhető el. Nyisd meg a rendszer TTS beállításait.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text("Hangok letöltése") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Szkenneles
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Könyvtár szkennelése",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Végigjárja a gyökérmappát, és párosítja a fájlokat a " +
                            "katalógussal. Sosem indul magától — mindig te kéred.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Eddig " + ui.cachedTotal + " fájl (" + ui.cachedMatched + " párosítva)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (ui.scan.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = ui.scan.filesFound.toString() + " fájl • " +
                                ui.scan.matched + " párosítva",
                            style = MaterialTheme.typography.labelSmall
                        )
                        OutlinedButton(onClick = { vm.cancelScan() }) { Text("Megszakítás") }
                    } else {
                        Button(onClick = { vm.startScan() }) { Text("Szkennelés indítása") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Hangjelzések
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Hangjelzések",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mélyebb, kettős hang jelzi minden új fejezet kezdetét, hogy " +
                            "hallás után is tudd, hol tartasz a könyvben.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = cueChapter, onCheckedChange = {
                            cueChapter = it; Prefs.setCueChapter(context, it)
                        })
                        Text("  Mélyebb, kettős jelzés fejezet előtt", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hangerő", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(74.dp))
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
                        "Olvasás és vezérlés",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = readerFollow, onCheckedChange = {
                            readerFollow = it; Prefs.setReaderFollow(context, it)
                        })
                        Text("  A szöveg kövesse a felolvasást", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = keepScreen, onCheckedChange = {
                            keepScreen = it; Prefs.setKeepScreenOn(context, it)
                        })
                        Text("  A képernyő maradjon ébren olvasás közben", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Fülhallgató dupla nyomás: ${rewindSec.toInt()} másodperc vissza",
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
                        "Szövegfelolvasó (TTS)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Az app a rendszer TTS motorját használja (pl. Google Szövegfelolvasó, magyar hanggal). A motort és a hangot a rendszerbeállításokban tudod cserélni.",
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
                                "Nem sikerült megnyitni a TTS beállításokat.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("Rendszer TTS beállítások…") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------------------------------------------------------- nyelvvalaszto
    if (langDialogOpen) {
        AlertDialog(
            onDismissRequest = { langDialogOpen = false },
            title = { Text("Felolvasás nyelve", style = MaterialTheme.typography.titleMedium) },
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
                    }) { Text("Automatikus (magyar, ha elérhető)") }
                    if (ttsLangs.isEmpty()) {
                        Text(
                            "Nem sikerült nyelveket lekérdezni. Telepíts szövegfelolvasó motort " +
                                "(pl. Google Szövegfelolvasó), majd nyisd meg újra ezt az ablakot.",
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
                TextButton(onClick = { langDialogOpen = false }) { Text("Bezárás") }
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
                        build.error != null -> "Hiba"
                        build.cancelled -> "Megszakítva"
                        else -> "Katalógus kész"
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
                            text = "${build.scanned} fájl átnézve\n" +
                                "${build.added} új fájl bejegyezve\n" +
                                "${build.newBooks} új könyv a katalógusban\n" +
                                "${build.skipped} korábbi bejegyzés érintetlenül hagyva",
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
                    }) { Text("Használatba veszem") }
                } else {
                    TextButton(onClick = { vm.clearBuildResult() }) { Text("Rendben") }
                }
            },
            dismissButton = {
                if (build.error == null && build.dbPath != null) {
                    TextButton(onClick = { vm.clearBuildResult() }) { Text("Bezárás") }
                }
            }
        )
    }
}
