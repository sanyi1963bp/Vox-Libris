package hu.konyvtar.tts.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Exporter
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.ProgressRow
import hu.konyvtar.tts.model.displayPercent
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Olvasási lista + statisztika: külön kategóriában az elolvasott
 * és a folyamatban lévő könyvek, haladással és hallgatási idővel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenReader: (ProgressRow) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ProgressRow>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }
    var exportResult by remember { mutableStateOf<Exporter.Result?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }

    /** Kimentés a Letöltések mappába; [share] esetén utána megosztás is. */
    fun doExport(share: Boolean) {
        if (exporting) return
        exporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Exporter.exportAll(context)
                } catch (e: Exception) {
                    exportError = e.message ?: "Az exportálás nem sikerült."
                    null
                }
            }
            exporting = false
            if (result == null) return@launch
            if (share) {
                try {
                    val uris = ArrayList<Uri>()
                    for (f in result.files) {
                        if (f.extension.lowercase() != "csv") continue
                        uris.add(
                            FileProvider.getUriForFile(
                                context, context.packageName + ".fileprovider", f
                            )
                        )
                    }
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "text/csv"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_SUBJECT, "Könyvtár TTS — olvasási nyilvántartás")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Küldés a számítógépre"))
                } catch (e: Exception) {
                    exportError = "A megosztás nem sikerült: ${e.message ?: "ismeretlen hiba"}"
                }
            } else {
                exportResult = result
            }
        }
    }

    LaunchedEffect(reloadKey) {
        rows = withContext(Dispatchers.IO) { AppDb.allProgress() }
    }

    val finished = rows.filter { it.displayPercent() >= FINISHED_PERCENT }
    val inProgress = rows.filter { it.displayPercent() < FINISHED_PERCENT }
    val totalListened = rows.sumOf { it.listenedMs }

    fun playRow(p: ProgressRow) {
        val f = File(p.path)
        if (!f.exists()) {
            Toast.makeText(context, "A fájl már nem található: ${p.path}", Toast.LENGTH_LONG).show()
            return
        }
        if (!TextExtractor.isSupported(f.extension)) {
            Toast.makeText(context, TextExtractor.unsupportedHint(f.extension), Toast.LENGTH_LONG).show()
            return
        }
        TtsService.playFile(
            context = context,
            path = p.path,
            title = p.title,
            author = p.author,
            konyvId = p.konyvId
        )
        onOpenReader(p)
    }

    fun deleteRow(p: ProgressRow) {
        Thread { AppDb.deleteProgress(p.path) }.start()
        rows = rows.filter { it.path != p.path }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Olvasási lista", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                },
                actions = {
                    IconButton(onClick = { doExport(share = false) }, enabled = !exporting) {
                        Icon(Icons.Filled.Save, contentDescription = "Mentés a Letöltések mappába")
                    }
                    IconButton(onClick = { doExport(share = true) }, enabled = !exporting) {
                        Icon(Icons.Filled.Share, contentDescription = "Küldés a számítógépre")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("Elkezdett", rows.size.toString())
                    StatCell("Elolvasott", finished.size.toString())
                    StatCell("Folyamatban", inProgress.size.toString())
                    StatCell("Hallgatás", fmtDuration(totalListened))
                }
            }
            Spacer(Modifier.height(8.dp))

            if (rows.isEmpty()) {
                Text(
                    text = "Még nincs elkezdett könyv.\nDupla koppintás egy fájlra a böngészőben, és indul a felolvasás — vagy hosszú nyomás az olvasáshoz!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (inProgress.isNotEmpty()) {
                    item(key = "hdr_progress") {
                        SectionHeader("📖 Folyamatban (${inProgress.size})")
                    }
                    items(inProgress, key = { it.path }) { p ->
                        BookRow(
                            p = p,
                            onPlay = { playRow(p) },
                            onRead = { onOpenReader(p) },
                            onDelete = { deleteRow(p) }
                        )
                    }
                }
                if (finished.isNotEmpty()) {
                    item(key = "hdr_finished") {
                        SectionHeader("✔ Elolvasott (${finished.size})")
                    }
                    items(finished, key = { it.path }) { p ->
                        BookRow(
                            p = p,
                            onPlay = { playRow(p) },
                            onRead = { onOpenReader(p) },
                            onDelete = { deleteRow(p) }
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- export visszajelzés
    exportResult?.let { r ->
        AlertDialog(
            onDismissRequest = { exportResult = null },
            title = { Text("Mentés kész", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        text = "${r.bookCount} könyv (ebből ${r.finishedCount} elolvasott) és " +
                            "${r.bookmarkCount} könyvjelző kimentve ide:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = r.dir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    for (f in r.files) {
                        Text(
                            text = "• ${f.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "USB-kábellel csatlakoztatva a telefont a PC-n a Letöltések (Download) mappában találod meg.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exportResult = null }) { Text("Rendben") }
            }
        )
    }

    exportError?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text("Hiba", style = MaterialTheme.typography.titleMedium) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text("Rendben") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 5.dp)
    )
}

@Composable
private fun BookRow(
    p: ProgressRow,
    onPlay: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = p.displayPercent()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (p.author.isNotEmpty()) {
                    Text(
                        text = p.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LinearProgressIndicator(
                    progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp, bottom = 2.dp)
                )
                Text(
                    text = String.format(
                        Locale.getDefault(),
                        "%.1f%% • %s • utoljára: %s",
                        pct,
                        fmtDuration(p.listenedMs),
                        fmtDate(p.lastAccess)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Felolvasás folytatása")
            }
            IconButton(onClick = onRead, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Olvasás képernyőn")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Törlés a listából",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
