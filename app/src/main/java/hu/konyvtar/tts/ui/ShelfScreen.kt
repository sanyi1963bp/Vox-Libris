package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.ShelfBook
import hu.konyvtar.tts.vm.LibraryViewModel
import java.io.File

/**
 * A polc: az app nyitóképernyője. A könyvek borítóit lapozgatva nézheted
 * végig, mint a könyvtárban a polc előtt. A borító alatt a haladás-csík
 * mutatja, hol tartasz az adott könyvben.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    vm: LibraryViewModel,
    onOpenBook: (FileRow) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickRoot: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    var infoBook by remember { mutableStateOf<ShelfBook?>(null) }

    LaunchedEffect(Unit) { vm.refreshCounters() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.shelf_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenFiles) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = stringResource(R.string.shelf_files)
                            )
                        }
                        IconButton(onClick = onOpenStats) {
                            Icon(
                                Icons.Filled.BarChart,
                                contentDescription = stringResource(R.string.explorer_stats)
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.common_settings)
                            )
                        }
                    }
                )
                // Olvasási számlálók — koppintásra megnyílik a lista
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = onOpenStats,
                        label = {
                            Text(
                                stringResource(R.string.counter_finished, ui.finishedCount),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    AssistChip(
                        onClick = onOpenStats,
                        label = {
                            Text(
                                stringResource(R.string.counter_inprogress, ui.inProgressCount),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    if (ui.catalogBooks > 0) {
                        Text(
                            text = stringResource(
                                R.string.catalog_stats, ui.catalogBooks, ui.catalogFiles
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                // --- indulási varázsló: mappa
                ui.setup == LibraryViewModel.Setup.PICK_ROOT -> SetupCard(
                    title = stringResource(R.string.setup_root_title),
                    text = stringResource(R.string.setup_root_text),
                    button = stringResource(R.string.setup_root_button),
                    onAction = onPickRoot,
                    onLater = { vm.dismissSetup() },
                    modifier = Modifier.align(Alignment.Center)
                )
                // --- indulási varázsló: beolvasás
                ui.setup == LibraryViewModel.Setup.OFFER_SCAN && !ui.scan.running -> SetupCard(
                    title = stringResource(R.string.setup_scan_title),
                    text = stringResource(R.string.setup_scan_text),
                    button = stringResource(R.string.setup_scan_button),
                    onAction = { vm.startScan() },
                    onLater = { vm.dismissSetup() },
                    modifier = Modifier.align(Alignment.Center)
                )
                // --- beolvasás közben
                ui.scan.running -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.set_build_progress,
                            ui.scan.scanned, ui.scan.added, ui.scan.skipped
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = ui.scan.currentFile,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.cancelScan() }) {
                        Text(stringResource(R.string.common_abort))
                    }
                }
                // --- üres polc
                ui.shelf.isEmpty() && !ui.shelfLoading -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.shelf_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.startScan() }) {
                        Text(stringResource(R.string.setup_scan_button))
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onOpenFiles) {
                        Text(stringResource(R.string.shelf_files))
                    }
                }
                ui.shelfLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                // --- a polc
                else -> {
                    val pagerState = rememberPagerState(pageCount = { ui.shelf.size })
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            pageSpacing = 16.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 44.dp
                            )
                        ) { page ->
                            val book = ui.shelf[page]
                            ShelfPage(
                                book = book,
                                percent = ui.progress[book.path],
                                onOpen = { onOpenBook(book.toFileRow()) },
                                onInfo = { infoBook = book }
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.shelf_position,
                                pagerState.currentPage + 1, ui.shelf.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    infoBook?.let { b ->
        ShelfInfoDialog(
            book = b,
            percent = ui.progress[b.path],
            onDismiss = { infoBook = null }
        )
    }
}

/** Egy könyv a polcon: borító, cím, szerző és a haladás-csík. */
@Composable
private fun ShelfPage(
    book: ShelfBook,
    percent: Double?,
    onOpen: () -> Unit,
    onInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(2f / 3f)
                .pointerInput(book.id) {
                    detectTapGestures(
                        onTap = { onOpen() },
                        onLongPress = { onInfo() }
                    )
                }
        ) {
            BookCover(
                title = book.title,
                author = book.author,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        // Haladás-csík: ha el sem kezdted, nincs csík
        if (percent != null && percent > 0.05) {
            val done = percent >= FINISHED_PERCENT
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = progressBarColor(done),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(6.dp)
            )
            Text(
                text = if (done) stringResource(R.string.info_finished)
                else String.format(java.util.Locale.getDefault(), "%.0f%%", percent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Text("  " + stringResource(R.string.stats_read_screen))
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = stringResource(R.string.row_info),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    text: String,
    button: String,
    onAction: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onAction) { Text(button) }
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.setup_later))
            }
        }
    }
}

/** A ShelfBook átalakítása a megnyitáshoz használt sorrá. */
private fun ShelfBook.toFileRow(): FileRow {
    val f = File(path)
    return FileRow(
        path = path,
        name = f.name,
        ext = f.extension.lowercase(),
        isDir = false,
        size = f.length(),
        mtime = f.lastModified(),
        konyvId = id,
        cim = title,
        szerzo = author
    )
}

/** A polcról nyíló könyvadatlap. */
@Composable
private fun ShelfInfoDialog(
    book: ShelfBook,
    percent: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var full by remember(book.id) { mutableStateOf<hu.konyvtar.tts.model.CatalogBook?>(null) }
    LaunchedEffect(book.id) {
        full = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            hu.konyvtar.tts.data.Catalog.bookById(book.id)
        }
    }
    val f = File(book.path)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (percent != null && percent > 0.05) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (percent >= FINISHED_PERCENT) stringResource(R.string.info_finished)
                        else stringResource(
                            R.string.info_progress,
                            String.format(java.util.Locale.getDefault(), "%.1f", percent)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = progressBarColor(percent >= FINISHED_PERCENT),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ShelfInfoLine(stringResource(R.string.info_publisher), full?.kiado)
                ShelfInfoLine(stringResource(R.string.info_year), full?.kiadasEve)
                ShelfInfoLine(stringResource(R.string.info_isbn), full?.isbn)
                ShelfInfoLine(stringResource(R.string.info_tags), full?.cimkek)
                ShelfInfoLine(stringResource(R.string.info_file), f.name)
                ShelfInfoLine(stringResource(R.string.info_size), fmtSize(f.length()))
                val desc = full?.leiras?.trim()
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.info_description),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@Composable
private fun ShelfInfoLine(label: String, value: String?) {
    val v = value?.trim()
    if (v.isNullOrBlank() || v == "N/A") return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = label + ": ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = v, style = MaterialTheme.typography.bodySmall)
    }
}
