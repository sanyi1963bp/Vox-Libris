package hu.konyvtar.tts.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.CatalogHolder
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.model.CatalogBook
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.ProgressRow
import hu.konyvtar.tts.reader.ExtractException
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Szöveges részletező: teljes metaadat + tartalmi előnézet, felolvasás-indítás. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    row: FileRow,
    onBack: () -> Unit,
    onOpenReader: () -> Unit
) {
    val context = LocalContext.current

    var book by remember { mutableStateOf<CatalogBook?>(null) }
    var progress by remember { mutableStateOf<ProgressRow?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var loadingPreview by remember { mutableStateOf(true) }

    LaunchedEffect(row.path) {
        withContext(Dispatchers.IO) {
            book = row.konyvId?.let { CatalogHolder.get(context)?.bookById(it) }
            progress = AppDb.progressFor(row.path)
        }
        withContext(Dispatchers.IO) {
            try {
                preview = TextExtractor.preview(context, File(row.path))
            } catch (e: ExtractException) {
                previewError = e.message
            } catch (e: Exception) {
                previewError = "Hiba az előnézet betöltésekor: ${e.message ?: "ismeretlen"}"
            }
            loadingPreview = false
        }
    }

    val title = book?.cim?.let { Normalizer.stripInvisible(it) }
        ?: row.cim ?: row.name.substringBeforeLast('.')
    val author = book?.szerzo ?: row.szerzo ?: ""

    fun play(restart: Boolean) {
        if (!TextExtractor.isSupported(row.ext)) {
            Toast.makeText(context, TextExtractor.unsupportedHint(row.ext), Toast.LENGTH_LONG).show()
            return
        }
        TtsService.playFile(
            context = context,
            path = row.path,
            title = title,
            author = author,
            konyvId = row.konyvId,
            restart = restart
        )
        onOpenReader()
    }

    fun shareToExternalTts() {
        Thread {
            try {
                val txt = TextExtractor.exportTxt(context, File(row.path))
                val uri = FileProvider.getUriForFile(
                    context, context.packageName + ".fileprovider", txt
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Szöveg küldése TTS appnak"))
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        e.message ?: "A megosztás nem sikerült.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
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
            // Cím + szerző
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (author.isNotEmpty()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))

            // Műveletek
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { play(restart = false) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(if (progress != null) " Folytatás" else " Felolvasás")
                }
                OutlinedButton(onClick = { play(restart = true) }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Text(" Elölről")
                }
                OutlinedButton(onClick = { shareToExternalTts() }) {
                    Icon(Icons.Filled.Share, contentDescription = "Megosztás külső TTS-sel")
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    if (TextExtractor.isSupported(row.ext)) {
                        onOpenReader()
                    } else {
                        Toast.makeText(context, TextExtractor.unsupportedHint(row.ext), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Text(" Olvasás képernyőn")
            }
            Spacer(Modifier.height(8.dp))

            // Haladás
            progress?.let { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Haladás",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%.1f%% • %d/%d bekezdés",
                                p.percent, p.paraIndex + 1, p.totalParas
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Hallgatva: ${fmtDuration(p.listenedMs)} • Utoljára: ${fmtDate(p.lastAccess)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Metaadatok
            val b = book
            if (b != null) {
                MetaRow("Kiadó", b.kiado)
                MetaRow("Kiadás éve", b.kiadasEve)
                MetaRow("ISBN", b.isbn)
                MetaRow(
                    "Sorozat",
                    listOfNotNull(
                        b.sorozat?.takeIf { it.isNotBlank() && it != "N/A" },
                        b.sorozatSzama?.takeIf { it.isNotBlank() && it != "N/A" }
                    ).joinToString(" #").ifBlank { null }
                )
                MetaRow("Címkék", b.cimkek)
                MetaRow("Formátum (nCore)", b.formatum)
                MetaRow("Méret (nCore)", b.meret)
            } else if (row.konyvId == null) {
                Text(
                    text = "Ehhez a fájlhoz nincs katalógus-találat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MetaRow("Fájl", row.name)
            MetaRow("Útvonal", row.path)
            MetaRow("Fájlméret", fmtSize(row.size))
            MetaRow("Módosítva", fmtDate(row.mtime))
            row.matchMode?.let { MetaRow("Párosítás módja", it) }

            // Leírás
            val desc = b?.leiras?.let { Normalizer.stripInvisible(it).trim() }
            if (!desc.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Leírás",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SelectionContainer {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Szöveg-előnézet
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tartalom-előnézet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            when {
                loadingPreview -> Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text("Szöveg kinyerése…", style = MaterialTheme.typography.bodySmall)
                }
                previewError != null -> Text(
                    text = previewError ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                else -> SelectionContainer {
                    Text(
                        text = preview ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String?) {
    val v = value?.trim()
    if (v.isNullOrBlank() || v == "N/A") return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = Normalizer.stripInvisible(v),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
