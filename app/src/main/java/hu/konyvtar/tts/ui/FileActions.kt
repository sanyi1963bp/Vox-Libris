package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.FileOps
import hu.konyvtar.tts.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Melyik fájlművelet ablaka van éppen nyitva. */
private enum class Op { NONE, RENAME, MOVE, COPY, DELETE, NOTE }

/**
 * Fájlműveletek egy könyvön: átnevezés, áthelyezés, másolás, törlés, jegyzet.
 *
 * Bárhonnan ugyanez a menü nyílik — a könyvtárlistából, a polcról és a
 * fájlböngészőből is —, mert a művelet ugyanaz. A törlés mindig rákérdez, és
 * a fájl nevét is megmutatja: az egyetlen visszafordíthatatlan művelet.
 *
 * @param onDone a művelet után hívjuk; a paraméter a fájl új útvonala,
 *   törlésnél `null`. A hívó ebből tudja, kell-e frissítenie a listát.
 */
@Composable
fun FileActionsMenu(
    path: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDone: (newPath: String?) -> Unit,
    /**
     * Az olvasóban `false`: a könyvet, amit épp olvasol, nem nevezzük át és
     * nem töröljük ki magad alól — jegyzetet viszont ott is írhatsz hozzá.
     */
    allowFileOps: Boolean = true
) {
    val context = LocalContext.current
    var op by remember { mutableStateOf(Op.NONE) }

    fun finish(result: FileOps.Result, doneRes: Int, deleted: Boolean = false) {
        when (result) {
            is FileOps.Result.Ok -> {
                Toast.makeText(context, context.getString(doneRes), Toast.LENGTH_SHORT).show()
                onDone(if (deleted) null else result.path)
            }
            is FileOps.Result.Error ->
                Toast.makeText(
                    context, context.getString(result.messageRes), Toast.LENGTH_LONG
                ).show()
        }
        op = Op.NONE
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.fileops_title),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
        )
        HorizontalDivider()
        MenuAction(R.string.note_edit, Icons.Filled.DriveFileRenameOutline) {
            onDismiss(); op = Op.NOTE
        }
        if (allowFileOps) {
            MenuAction(R.string.fileops_rename, Icons.Filled.DriveFileRenameOutline) {
                onDismiss(); op = Op.RENAME
            }
            MenuAction(R.string.fileops_move, Icons.AutoMirrored.Filled.DriveFileMove) {
                onDismiss(); op = Op.MOVE
            }
            MenuAction(R.string.fileops_copy, Icons.Filled.ContentCopy) {
                onDismiss(); op = Op.COPY
            }
            HorizontalDivider()
            MenuAction(R.string.fileops_delete, Icons.Filled.Delete, danger = true) {
                onDismiss(); op = Op.DELETE
            }
        }
    }

    when (op) {
        Op.RENAME -> RenameDialog(
            currentName = File(path).name,
            onDismiss = { op = Op.NONE },
            onConfirm = { name ->
                finish(FileOps.rename(context, path, name), R.string.fileops_done_rename)
            }
        )
        Op.MOVE -> FolderPickerDialog(
            startDir = File(path).parent ?: Prefs.rootPath(context),
            onDismiss = { op = Op.NONE },
            onPicked = { dir ->
                finish(FileOps.move(context, path, dir), R.string.fileops_done_move)
            }
        )
        Op.COPY -> FolderPickerDialog(
            startDir = File(path).parent ?: Prefs.rootPath(context),
            onDismiss = { op = Op.NONE },
            onPicked = { dir ->
                finish(FileOps.copy(context, path, dir), R.string.fileops_done_copy)
            }
        )
        Op.DELETE -> DeleteDialog(
            fileName = File(path).name,
            onDismiss = { op = Op.NONE },
            onConfirm = {
                finish(FileOps.delete(context, path), R.string.fileops_done_delete, deleted = true)
            }
        )
        Op.NOTE -> NoteDialog(
            path = path,
            onDismiss = { op = Op.NONE },
            onSaved = { op = Op.NONE; onDone(path) }
        )
        Op.NONE -> {}
    }
}

@Composable
private fun MenuAction(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick
    )
}

// ---------------------------------------------------------------- átnevezés

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    val valid = FileOps.isValidName(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.fileops_rename_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = !valid,
                    placeholder = { Text(stringResource(R.string.fileops_rename_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) {
                    Text(
                        text = stringResource(R.string.fileops_err_name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

// ---------------------------------------------------------------- mappaválasztás

/**
 * Kis mappaböngésző az áthelyezéshez és a másoláshoz. Csak mappákat mutat,
 * mert csak azok közül lehet választani.
 */
@Composable
private fun FolderPickerDialog(
    startDir: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit
) {
    var dir by remember { mutableStateOf(startDir) }
    var subDirs by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(dir) {
        subDirs = withContext(Dispatchers.IO) {
            File(dir).listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(R.string.fileops_pick_folder),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dir,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column {
                val parent = File(dir).parentFile
                if (parent != null && parent.absolutePath.startsWith("/storage")) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(dir) {
                                detectTapGestures(onTap = { dir = parent.absolutePath })
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(0.dp))
                        Text("  ..", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(subDirs, key = { it.absolutePath }) { f ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(f.absolutePath) {
                                    detectTapGestures(onTap = { dir = f.absolutePath })
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "  " + f.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(dir) }) {
                Text(stringResource(R.string.fileops_here))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

// ---------------------------------------------------------------- törlés

@Composable
private fun DeleteDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.fileops_delete_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                stringResource(R.string.fileops_delete_text, fileName),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.fileops_delete),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

// ---------------------------------------------------------------- jegyzet

/** Saját jegyzet a könyvhöz. Üresen mentve törlődik. */
@Composable
private fun NoteDialog(
    path: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var text by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        text = withContext(Dispatchers.IO) { AppDb.noteFor(path) ?: "" }
    }

    val current = text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.note_title), style = MaterialTheme.typography.titleMedium)
        },
        text = {
            OutlinedTextField(
                value = current ?: "",
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = current != null,
                onClick = {
                    AppDb.setNote(path, current ?: "")
                    onSaved()
                }
            ) { Text(stringResource(R.string.note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** Nyitógomb a menühöz — az adatlapon és a fájlböngésző sorain. */
@Composable
fun FileActionsButton(
    path: String,
    onDone: (newPath: String?) -> Unit,
    modifier: Modifier = Modifier,
    allowFileOps: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = modifier) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.fileops_title)
            )
        }
        FileActionsMenu(
            path = path,
            expanded = open,
            onDismiss = { open = false },
            onDone = onDone,
            allowFileOps = allowFileOps
        )
    }
}
