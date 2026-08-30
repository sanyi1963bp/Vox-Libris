package hu.konyvtar.tts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R

/** A szövegben keresés állapota a felső sáv számára. */
data class ReaderSearch(
    val open: Boolean,
    val query: String,
    val running: Boolean,
    val hitCount: Int,
    /** Hányadik találatnál állunk; -1, ha egyiknél sem. */
    val hitPos: Int
)

/**
 * Az olvasó felső sávja: vissza, cím és szerző, keresés, beállítások, és a
 * „továbbiak" menü (könyvjelző, könyvjelzők, adatlap, felolvasás leállítása).
 *
 * A menü nyitott állapota tisztán a sáv magánügye, ezért itt is marad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    title: String,
    author: String,
    bookmarkCount: Int,
    /** Szól-e éppen ez a könyv — csak ilyenkor van értelme leállítani. */
    narrating: Boolean,
    search: ReaderSearch,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onJumpMatch: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onBookmarkHere: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenInfo: () -> Unit,
    onStopNarration: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (author.isNotEmpty()) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            },
            actions = {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (search.open) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.reader_search_in_text)
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.common_settings)
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.reader_more_actions)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_bookmark_here)) },
                            leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) },
                            onClick = { menuOpen = false; onBookmarkHere() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.reader_bookmarks_n, bookmarkCount))
                            },
                            leadingIcon = { Icon(Icons.Filled.Bookmarks, null) },
                            onClick = { menuOpen = false; onOpenBookmarks() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.row_info)) },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menuOpen = false; onOpenInfo() }
                        )
                        if (narrating) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reader_stop_narration)) },
                                leadingIcon = { Icon(Icons.Filled.Stop, null) },
                                onClick = { menuOpen = false; onStopNarration() }
                            )
                        }
                    }
                }
            }
        )
        if (search.open) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            stringResource(R.string.reader_search_hint),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
                Text(
                    text = when {
                        search.running -> "…"
                        search.hitCount == 0 && search.query.trim().length >= 2 -> "0"
                        search.hitCount > 0 -> "${search.hitPos + 1}/${search.hitCount}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                IconButton(onClick = { onJumpMatch(-1) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.reader_prev_hit)
                    )
                }
                IconButton(onClick = { onJumpMatch(1) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.reader_next_hit)
                    )
                }
            }
        }
    }
}
