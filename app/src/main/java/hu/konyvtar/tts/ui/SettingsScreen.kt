package hu.konyvtar.tts.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.vm.LibraryViewModel
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

    LaunchedEffect(cacheReload) {
        cacheSize = withContext(Dispatchers.IO) { TextExtractor.cachedSizeBytes(context) }
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
}
