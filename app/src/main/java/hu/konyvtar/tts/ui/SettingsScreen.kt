package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.vm.LibraryViewModel

/**
 * A beállítások: kártyák egymás alatt.
 *
 * Ez a fájl csak a keret és a sorrend; minden kártya külön composable a
 * [SettingsCards] fájlban, a saját állapotával együtt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPickRoot: () -> Unit
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()

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
                },
            )
        },
        bottomBar = { NowPlayingBar(onOpen = onOpenNowPlaying) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            RootFolderCard(rootPath = ui.rootPath, onPickRoot = onPickRoot)

            CatalogCard(
                books = ui.catalogBooks,
                files = ui.catalogFiles,
                scan = ui.scan,
                onScan = { includePdf -> vm.startScan(includePdf) },
                onCancel = { vm.cancelScan() },
                onRemoveMissing = {
                    vm.removeMissing { removed ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.remove_missing_done, removed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )

            CoversCard(
                covers = ui.covers,
                onLoad = { vm.startCoverScan() },
                onCancel = { vm.cancelCoverScan() }
            )

            AppearanceCard()
            UiLanguageCard()
            NarrationLanguageCard()
            AudioCuesCard()
            ReadingCard()
            CacheCard()
            TtsEngineCard()

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------------------------------------------------------- borítók eredménye
    val covers = ui.covers
    if (!covers.running && covers.done) {
        AlertDialog(
            onDismissRequest = { vm.clearCoverResult() },
            title = {
                Text(
                    stringResource(R.string.covers_done_title),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    stringResource(R.string.covers_done, covers.found, covers.total),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearCoverResult() }) {
                    Text(stringResource(R.string.common_ok))
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
