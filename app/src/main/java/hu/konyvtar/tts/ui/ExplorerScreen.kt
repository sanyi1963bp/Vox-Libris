package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.data.CatalogHolder
import hu.konyvtar.tts.data.MetadataExtractor
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.model.CatalogBook
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.SortKey
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.vm.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import androidx.compose.ui.res.stringResource
import hu.konyvtar.tts.R

/**
 * Total Commander stílusú, sűrű fájlböngésző.
 * Szimpla koppintás: részletek. Dupla koppintás: felolvasás azonnal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    vm: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReader: (FileRow) -> Unit
) {
    val ui by vm.ui.collectAsState()
    val player by TtsService.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var storageMenuOpen by remember { mutableStateOf(false) }
    var infoRow by remember { mutableStateOf<FileRow?>(null) }
    var infoBook by remember { mutableStateOf<CatalogBook?>(null) }
    var infoMeta by remember { mutableStateOf<String?>(null) }

    // Az info gombra: előbb a katalógusból, ha nincs találat, a fájl saját metaadatából
    LaunchedEffect(infoRow) {
        infoBook = null
        infoMeta = null
        val r = infoRow ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val id = r.konyvId
            if (id != null) {
                infoBook = CatalogHolder.get(context)?.bookById(id)
            }
            if (infoBook == null) {
                val m = MetadataExtractor.extract(context, File(r.path), true)
                fun line(res: Int, v: String?) = v?.let { context.getString(res) + ": " + it }
                infoMeta = listOfNotNull(
                    line(R.string.info_title, m.title),
                    line(R.string.info_author, m.author),
                    line(R.string.info_publisher, m.publisher),
                    line(R.string.info_year, m.year),
                    line(R.string.info_series, m.series),
                    line(R.string.info_tags, m.tags),
                    m.description
                ).joinToString("\n")
            }
        }
    }

    // Üzenetek (pl. adatbázis megnyitva) toastként
    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    // Lista tetejére ugrunk, ha mappát váltunk
    LaunchedEffect(ui.currentDir) {
        listState.scrollToItem(0)
    }

    fun playRow(row: FileRow) {
        if (!TextExtractor.isSupported(row.ext)) {
            Toast.makeText(
            context,
            TextExtractor.unsupportedHint(context, row.ext),
            Toast.LENGTH_LONG
        ).show()
            return
        }
        TtsService.playFile(
            context = context,
            path = row.path,
            title = row.cim ?: row.name.substringBeforeLast('.'),
            author = row.szerzo ?: "",
            konyvId = row.konyvId
        )
        onOpenReader(row)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                // Felső sor: cím + műveletgombok
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (ui.db.opened) stringResource(R.string.explorer_books_count, ui.db.bookCount)
                        else stringResource(R.string.explorer_no_db),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ui.db.opened) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = { vm.startScan() }) {
                        Icon(
                            Icons.Filled.Radar,
                            contentDescription = stringResource(R.string.explorer_scan)
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
                // Kereső + nézetváltó
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = { vm.setQuery(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = {
                            Text(
                                stringResource(R.string.explorer_search_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default,
                        trailingIcon = {
                            if (ui.query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.explorer_clear)
                                    )
                                }
                            }
                        }
                    )
                    FilterChip(
                        selected = ui.recursiveSearch,
                        onClick = { vm.setRecursiveSearch(!ui.recursiveSearch) },
                        label = { Text(stringResource(R.string.explorer_subfolders)) }
                    )
                }
                if (ui.searchingDeep) {
                    Text(
                        text = stringResource(R.string.explorer_deep_hits, ui.entries.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
                run {
                    // Útvonal-sor: tárolóváltó + felfelé gomb + útvonal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { storageMenuOpen = true },
                                modifier = Modifier.width(34.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SdStorage,
                                    contentDescription = stringResource(R.string.explorer_storage_switch)
                                )
                            }
                            DropdownMenu(
                                expanded = storageMenuOpen,
                                onDismissRequest = { storageMenuOpen = false }
                            ) {
                                ui.volumes.forEach { v ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(v.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    v.path,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            storageMenuOpen = false
                                            vm.navigateTo(v.path)
                                        }
                                    )
                                }
                                if (ui.volumes.size < 2) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.explorer_no_other_storage),
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        onClick = { storageMenuOpen = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { vm.up() }, modifier = Modifier.width(34.dp)) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = stringResource(R.string.common_up)
                            )
                        }
                        Text(
                            text = ui.currentDir,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
                // Szkennelési sáv
                if (ui.scan.running) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.explorer_scanning, ui.scan.filesFound, ui.scan.matched
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.cancelScan() }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    }
                }
                // Oszlopfejléc (rendezés)
                HeaderRow(sortKey = ui.sortKey, sortAsc = ui.sortAsc, onSort = { vm.setSort(it) })
                HorizontalDivider()
            }
        },
        bottomBar = {
            if (player.path != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onOpenPlayer() })
                            }
                            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = player.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.1f%%", player.percent) +
                                    if (player.preparing) stringResource(R.string.explorer_preparing) else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { TtsService.send(context, TtsService.ACTION_TOGGLE) }) {
                            Icon(
                                if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (player.playing) R.string.common_pause else R.string.common_play
                                )
                            )
                        }
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
            if (ui.entries.isEmpty() && !ui.loading) {
                Text(
                    text = stringResource(R.string.explorer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = ui.entries,
                    key = { _, row -> row.path }
                ) { index, row ->
                    FileRowItem(
                        row = row,
                        stripe = index % 2 == 1,
                        percent = ui.progress[row.path],
                        onInfo = { infoRow = row },
                        onSingleTap = {
                            if (row.isDir) {
                                vm.navigateTo(row.path)
                            } else if (TextExtractor.isSupported(row.ext)) {
                                onOpenReader(row)
                            } else {
                                Toast.makeText(
                                    context,
                                    TextExtractor.unsupportedHint(context, row.ext),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onDoubleTap = {
                            if (!row.isDir) playRow(row)
                        },
                        onLongPress = {
                            if (!row.isDir) {
                                if (TextExtractor.isSupported(row.ext)) {
                                    onOpenReader(row)
                                } else {
                                    Toast.makeText(
                                        context,
                                        TextExtractor.unsupportedHint(context, row.ext),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
            FastScrollbar(
                listState = listState,
                itemCount = ui.entries.size,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    infoRow?.let { r ->
        BookInfoDialog(
            row = r,
            book = infoBook,
            fallback = infoMeta,
            percent = ui.progress[r.path],
            onDismiss = { infoRow = null }
        )
    }
}

@Composable
private fun BookInfoDialog(
    row: FileRow,
    book: CatalogBook?,
    fallback: String?,
    percent: Double?,
    onDismiss: () -> Unit
) {
    val f = File(row.path)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = book?.cim ?: row.cim ?: row.name.substringBeforeLast('.'),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val author = book?.szerzo ?: row.szerzo
                if (!author.isNullOrBlank()) {
                    Text(
                        text = Normalizer.stripInvisible(author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (percent != null && percent > 0.05) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (percent >= 98.0) stringResource(R.string.info_finished)
                        else stringResource(
                            R.string.info_progress,
                            String.format(Locale.getDefault(), "%.1f", percent)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (book != null) {
                    InfoRow(stringResource(R.string.info_publisher), book.kiado)
                    InfoRow(stringResource(R.string.info_year), book.kiadasEve)
                    InfoRow(stringResource(R.string.info_isbn), book.isbn)
                    InfoRow(
                        stringResource(R.string.info_series),
                        listOfNotNull(
                            book.sorozat?.takeIf { it.isNotBlank() && it != "N/A" },
                            book.sorozatSzama?.takeIf { it.isNotBlank() && it != "N/A" }
                        ).joinToString(" #").ifBlank { null }
                    )
                    InfoRow(stringResource(R.string.info_tags), book.cimkek)
                }
                InfoRow(stringResource(R.string.info_file), f.name)
                InfoRow(stringResource(R.string.info_size), fmtSize(f.length()))
                InfoRow(stringResource(R.string.info_modified), fmtDate(row.mtime))

                val desc = book?.leiras?.let { Normalizer.stripInvisible(it).trim() }
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.info_description),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = desc, style = MaterialTheme.typography.bodySmall)
                } else if (!fallback.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.info_from_file),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = fallback, style = MaterialTheme.typography.bodySmall)
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.info_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

@Composable
private fun InfoRow(label: String, value: String?) {
    val v = value?.trim()
    if (v.isNullOrBlank() || v == "N/A") return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = label + ": ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = Normalizer.stripInvisible(v), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HeaderRow(sortKey: SortKey, sortAsc: Boolean, onSort: (SortKey) -> Unit) {
    val arrow = if (sortAsc) "▲" else "▼"

    @Composable
    fun cell(label: String, key: SortKey, modifier: Modifier, align: TextAlign = TextAlign.Left) {
        Text(
            text = if (sortKey == key) "$label $arrow" else label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = align,
            maxLines = 1,
            color = if (sortKey == key) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.pointerInput(key) {
                detectTapGestures(onTap = { onSort(key) })
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cell(stringResource(R.string.col_name), SortKey.NAME, Modifier.weight(1f))
        cell(stringResource(R.string.col_author), SortKey.AUTHOR, Modifier.width(70.dp))
        cell(stringResource(R.string.col_size), SortKey.SIZE, Modifier.width(62.dp), TextAlign.Right)
        cell(stringResource(R.string.col_date), SortKey.DATE, Modifier.width(72.dp), TextAlign.Right)
    }
}

@Composable
private fun FileRowItem(
    row: FileRow,
    stripe: Boolean,
    percent: Double?,
    onInfo: () -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = if (stripe) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .pointerInput(row.path) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { onDoubleTap() },
                        onLongPress = { onLongPress() }
                    )
                }
                .padding(start = 8.dp, top = 3.dp, bottom = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.isDir) FontWeight.Bold else FontWeight.Normal,
                    color = if (row.isDir) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (row.isDir) "<DIR>" else fmtSize(row.size),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(62.dp)
                )
                Text(
                    text = fmtDate(row.mtime),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
            }
            if (!row.isDir && (row.cim != null || row.szerzo != null)) {
                Text(
                    text = listOfNotNull(row.szerzo, row.cim).joinToString(": "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Olvasottsági csík: csak akkor, ha már elkezdted a könyvet
            if (!row.isDir && percent != null && percent > 0.05) {
                val done = percent >= FINISHED_PERCENT
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, bottom = 1.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                        color = if (done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = if (done) "  " + stringResource(R.string.row_done)
                        else String.format(Locale.getDefault(), "  %.0f%%", percent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!row.isDir) {
            IconButton(onClick = onInfo, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = stringResource(R.string.row_info),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}
