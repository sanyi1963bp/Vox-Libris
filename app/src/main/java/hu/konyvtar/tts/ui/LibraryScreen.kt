package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.ShelfBook
import hu.konyvtar.tts.model.SortKey
import hu.konyvtar.tts.model.toFileRow
import hu.konyvtar.tts.reader.TextExtractor
import hu.konyvtar.tts.tts.TtsService
import hu.konyvtar.tts.vm.LibraryViewModel
import java.util.Locale

/**
 * A könyvtár listája: az app nyitóképernyője.
 *
 * Több ezer könyvnél a lapozgatás reménytelen, ezért itt a keresés a fő
 * eszköz: a mező a címben, a szerzőben ÉS a fájlnévben is keres, a betűsáv
 * pedig egy koppintással a kezdőbetűre ugrik. A sáv csak azokat a betűket
 * mutatja, amikhez tényleg van könyv — nincsenek üresbe vezető gombok.
 *
 * Koppintások: egy = kijelölés, kettő = megnyitás és felolvasás,
 * hosszú nyomás = adatlap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenBook: (FileRow) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenShelf: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickRoot: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var selected by remember { mutableStateOf<String?>(null) }
    var infoBook by remember { mutableStateOf<ShelfBook?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var formatMenu by remember { mutableStateOf(false) }
    var hintSeen by remember { mutableStateOf(Prefs.gestureHintSeen(context)) }
    val showCovers = Prefs.coversInList(context)

    // A felolvasóból visszatérve frissüljön a haladás és a számlálók.
    // Navigációnál a képernyő saját életciklust kap, ezért ez itt működik.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshCounters()
                vm.refreshProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Szűrés vagy rendezés után mindig a lista elejére ugrunk — betűre
    // koppintva ez a lényeg: rögtön az adott betűnél kezdődjön a lista.
    LaunchedEffect(ui.libQuery, ui.libLetter, ui.libFormat, ui.libSort, ui.libAsc) {
        listState.scrollToItem(0)
    }

    /** Megnyitás felolvasással — ez a dupla koppintás. */
    fun openAndPlay(book: ShelfBook) {
        val row = book.toFileRow()
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
        onOpenBook(row)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.lib_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenShelf) {
                            Icon(
                                Icons.Filled.ViewCarousel,
                                contentDescription = stringResource(R.string.lib_shelf)
                            )
                        }
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

                // --- kereső + rendezés + formátumszűrő
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ui.libQuery,
                        onValueChange = { vm.setLibQuery(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        placeholder = {
                            Text(
                                stringResource(R.string.lib_search_hint),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (ui.libQuery.isNotEmpty()) {
                                IconButton(onClick = { vm.setLibQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.common_close),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )

                    // Rendezés
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.lib_sort)
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false }
                        ) {
                            SortItem(R.string.lib_sort_title, SortKey.TITLE, ui, vm) {
                                sortMenu = false
                            }
                            SortItem(R.string.lib_sort_author, SortKey.AUTHOR, ui, vm) {
                                sortMenu = false
                            }
                            SortItem(R.string.lib_sort_format, SortKey.FORMAT, ui, vm) {
                                sortMenu = false
                            }
                        }
                    }

                    // Formátumok: itt látszik, miből mennyi van
                    Box {
                        IconButton(onClick = { formatMenu = true }) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = stringResource(R.string.lib_format),
                                tint = if (ui.libFormat.isEmpty())
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = formatMenu,
                            onDismissRequest = { formatMenu = false }
                        ) {
                            Text(
                                text = stringResource(R.string.lib_formats),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
                            )
                            HorizontalDivider()
                            FormatItem(
                                label = stringResource(R.string.lib_all),
                                ext = "",
                                count = ui.books.size,
                                active = ui.libFormat.isEmpty()
                            ) {
                                if (ui.libFormat.isNotEmpty()) vm.setLibFormat(ui.libFormat)
                                formatMenu = false
                            }
                            for (f in ui.libFormats) {
                                FormatItem(
                                    label = f.ext.uppercase().ifEmpty { "?" },
                                    ext = f.ext,
                                    count = f.count,
                                    active = ui.libFormat == f.ext
                                ) {
                                    vm.setLibFormat(f.ext)
                                    formatMenu = false
                                }
                            }
                        }
                    }
                }

                // --- betűsáv
                if (ui.libLetters.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LetterButton(
                            text = stringResource(R.string.lib_all),
                            active = ui.libLetter.isEmpty(),
                            wide = true
                        ) {
                            if (ui.libLetter.isNotEmpty()) vm.setLibLetter(ui.libLetter)
                        }
                        for (letter in ui.libLetters) {
                            LetterButton(
                                text = letter,
                                active = ui.libLetter == letter
                            ) { vm.setLibLetter(letter) }
                        }
                    }
                }

                // --- állapotsor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.lib_count, ui.libRows.size, ui.books.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.counter_finished, ui.finishedCount) +
                            "  ·  " +
                            stringResource(R.string.counter_inprogress, ui.inProgressCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onTap = { onOpenStats() })
                        }
                    )
                }
                if (!hintSeen) {
                    Text(
                        text = stringResource(R.string.lib_hint_gestures),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 1.dp)
                    )
                }
                HorizontalDivider()
            }
        },
        bottomBar = { NowPlayingBar(onOpen = onOpenNowPlaying) }
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
                ui.libLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                // --- üres katalógus
                ui.books.isEmpty() -> Column(
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
                // --- a szűrés semmit sem hagyott
                ui.libRows.isEmpty() -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.lib_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { vm.clearLibFilters() }) {
                        Text(stringResource(R.string.lib_all))
                    }
                }
                // --- a lista
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = ui.libRows,
                            key = { _, b -> b.path }
                        ) { index, book ->
                            LibraryRow(
                                book = book,
                                showCover = showCovers,
                                stripe = index % 2 == 1,
                                selected = selected == book.path,
                                percent = ui.progress[book.path],
                                onSingleTap = { selected = book.path },
                                onDoubleTap = { openAndPlay(book) },
                                onLongPress = {
                                    infoBook = book
                                    if (!hintSeen) {
                                        Prefs.setGestureHintSeen(context)
                                        hintSeen = true
                                    }
                                }
                            )
                        }
                    }
                    FastScrollbar(
                        listState = listState,
                        itemCount = ui.libRows.size,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }
    }

    infoBook?.let { b ->
        BookDetailsDialog(
            book = BookRef(b.path, b.title, b.author, b.id),
            onOpen = {
                infoBook = null
                openAndPlay(b)
            },
            onDismiss = { infoBook = null }
        )
    }
}

// ---------------------------------------------------------------- lista sor

/**
 * Egy könyv a listában: formátumjelvény, cím, szerző és fájlnév, alatta a
 * haladás-csík, ha már elkezdted. Szándékosan sűrű — így fér el sok könyv
 * egy képernyőn.
 */
@Composable
private fun LibraryRow(
    book: ShelfBook,
    showCover: Boolean,
    stripe: Boolean,
    selected: Boolean,
    percent: Double?,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        stripe -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .pointerInput(book.path) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(start = 8.dp, end = 26.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bélyegkép csak kérésre: magasabb sorokba kevesebb könyv fér
        if (showCover) {
            BookCover(
                title = book.title,
                author = book.author,
                path = book.path,
                compact = true,
                modifier = Modifier
                    .width(30.dp)
                    .aspectRatio(2f / 3f)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormatBadge(book.ext)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (book.author.isNotBlank()) book.author + "  ·  " + book.fileName
                else book.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 44.dp)
            )
            if (percent != null && percent > 0.05) {
                val done = percent >= FINISHED_PERCENT
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 44.dp, top = 2.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = progressBarColor(done),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
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
    }
}

// ---------------------------------------------------------------- formátumok

/**
 * A fájl típusa egy pillantásra. A szürke jelvény azt jelenti, hogy ebből a
 * formátumból nem tudunk szöveget kinyerni — így a listában is látszik,
 * melyik könyvvel mire számíthatsz.
 */
@Composable
fun FormatBadge(ext: String) {
    val supported = TextExtractor.isSupported(ext)
    val color = if (supported) formatColor(ext) else Color(0xFF9E9E9E)
    Box(
        modifier = Modifier
            .width(38.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.9f))
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ext.uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun formatColor(ext: String): Color = when (ext.lowercase()) {
    "epub" -> Color(0xFF2E7D32)
    "pdf" -> Color(0xFFC62828)
    "mobi", "azw", "azw3", "prc" -> Color(0xFFEF6C00)
    "txt" -> Color(0xFF546E7A)
    "fb2" -> Color(0xFF00838F)
    "rtf", "doc", "docx" -> Color(0xFF4527A0)
    "htm", "html" -> Color(0xFF00695C)
    else -> Color(0xFF6D4C41)
}

/** Mire számíthatsz ettől a formátumtól a felolvasásnál. */
fun formatNoteRes(ext: String): Int = when (ext.lowercase()) {
    "epub", "fb2", "mobi", "azw", "azw3", "prc", "docx", "htm", "html" -> R.string.fmt_note_good
    "txt", "rtf" -> R.string.fmt_note_plain
    "pdf" -> R.string.fmt_note_pdf
    else -> R.string.fmt_note_none
}

// ---------------------------------------------------------------- apró elemek

/** Egy betű a betűsávban. */
@Composable
private fun LetterButton(
    text: String,
    active: Boolean,
    wide: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .width(if (wide) 46.dp else 28.dp)
            .height(26.dp)
            .pointerInput(text) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SortItem(
    labelRes: Int,
    key: SortKey,
    ui: LibraryViewModel.UiState,
    vm: LibraryViewModel,
    onDone: () -> Unit
) {
    val active = ui.libSort == key
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes) +
                    if (active) (if (ui.libAsc) "  ▲" else "  ▼") else ""
            )
        },
        onClick = {
            vm.setLibSort(key)
            onDone()
        },
        leadingIcon = {
            if (active) Icon(Icons.Filled.Check, contentDescription = null)
            else Spacer(Modifier.width(24.dp))
        }
    )
}

@Composable
private fun FormatItem(
    label: String,
    ext: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.width(150.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            if (active) Icon(Icons.Filled.Check, contentDescription = null)
            else if (ext.isEmpty()) Spacer(Modifier.width(24.dp))
            else FormatBadge(ext)
        }
    )
}

/** Az indulási varázsló kártyája: mappa, majd beolvasás. */
@Composable
fun SetupCard(
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
