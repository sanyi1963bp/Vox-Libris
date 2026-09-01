package hu.konyvtar.tts.ui

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.FileRow
import hu.konyvtar.tts.model.ShelfBook
import hu.konyvtar.tts.model.toFileRow
import hu.konyvtar.tts.vm.LibraryViewModel

/**
 * A polc: a borítókat lapozgatva nézheted végig a könyveket, mint a
 * könyvtárban a polc előtt. Ugyanazt mutatja, amit a lista éppen mutat —
 * ha ott rákerestél vagy leszűkítetted egy betűre, itt is csak azok
 * a könyvek lapozhatók. A borító alatt a haladás-csík.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    vm: LibraryViewModel,
    onOpenBook: (FileRow) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    var infoBook by remember { mutableStateOf<ShelfBook?>(null) }

    LaunchedEffect(Unit) { vm.refreshCounters() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.shelf_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
            )
        },
        bottomBar = { NowPlayingBar(onOpen = onOpenNowPlaying) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                ui.libLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                ui.libRows.isEmpty() -> Text(
                    text = stringResource(R.string.shelf_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp)
                )
                else -> {
                    val pagerState = rememberPagerState(pageCount = { ui.libRows.size })
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
                            val book = ui.libRows[page]
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
                                pagerState.currentPage + 1, ui.libRows.size
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
        BookDetailsDialog(
            book = BookRef(b.path, b.title, b.author, b.id),
            onOpen = {
                infoBook = null
                onOpenBook(b.toFileRow())
            },
            onFileChanged = { newPath ->
                if (newPath == null) infoBook = null
                vm.reloadAfterFileChange()
            },
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
                path = book.path,
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
