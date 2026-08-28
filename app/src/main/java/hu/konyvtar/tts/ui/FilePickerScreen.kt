package hu.konyvtar.tts.ui

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.io.File

/**
 * Egyszerű beépített fájl/mappa-választó.
 * [pickDirectory] = true: mappát választunk ("Ezt a mappát választom" gombbal).
 * [pickDirectory] = false: .db fájlt választunk (koppintással).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
    title: String,
    pickDirectory: Boolean,
    extensionFilter: Set<String> = emptySet(),
    onPicked: (String) -> Unit,
    onBack: () -> Unit
) {
    var currentDir by remember {
        mutableStateOf(Environment.getExternalStorageDirectory().absolutePath)
    }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var storageMenuOpen by remember { mutableStateOf(false) }
    val volumes = remember {
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                val label = if (v.isPrimary) "Belső tároló"
                else (v.getDescription(context) ?: "SD-kártya")
                Pair(label, dir.absolutePath)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(currentDir) {
        val dir = File(currentDir)
        val children = try {
            dir.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val dirs = children.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
        val files = if (pickDirectory) emptyList() else {
            children.filter { f ->
                f.isFile && (extensionFilter.isEmpty() ||
                    f.extension.lowercase() in extensionFilter)
            }.sortedBy { it.name.lowercase() }
        }
        entries = dirs + files
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza")
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { storageMenuOpen = true }) {
                            Icon(Icons.Filled.SdStorage, contentDescription = "Tároló váltása")
                        }
                        DropdownMenu(
                            expanded = storageMenuOpen,
                            onDismissRequest = { storageMenuOpen = false }
                        ) {
                            volumes.forEach { (name, path) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                path,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        storageMenuOpen = false
                                        currentDir = path
                                    }
                                )
                            }
                            if (volumes.size < 2) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Nincs másik tároló (SD-kártya) csatolva",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    onClick = { storageMenuOpen = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        val parent = File(currentDir).parentFile
                        if (parent != null && parent.absolutePath.startsWith("/storage")) {
                            currentDir = parent.absolutePath
                        }
                    }) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Fel")
                    }
                }
                Text(
                    text = currentDir,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                HorizontalDivider()
            }
        },
        bottomBar = {
            if (pickDirectory) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Button(
                        onClick = { onPicked(currentDir) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text("Ezt a mappát választom")
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
            if (entries.isEmpty()) {
                Text(
                    text = if (pickDirectory) "Nincs almappa." else "Nincs ide illő fájl ebben a mappában.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(entries, key = { _, f -> f.absolutePath }) { index, f ->
                    val stripe = index % 2 == 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (stripe) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .pointerInput(f.absolutePath) {
                                detectTapGestures(onTap = {
                                    if (f.isDirectory) {
                                        currentDir = f.absolutePath
                                    } else {
                                        onPicked(f.absolutePath)
                                    }
                                })
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = f.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (f.isDirectory) FontWeight.Bold else FontWeight.Normal,
                            color = if (f.isDirectory) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (f.isDirectory) "<DIR>" else fmtSize(f.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.width(70.dp)
                        )
                    }
                }
            }
            FastScrollbar(
                listState = listState,
                itemCount = entries.size,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
