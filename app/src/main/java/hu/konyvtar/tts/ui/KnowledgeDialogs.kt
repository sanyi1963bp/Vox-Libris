package hu.konyvtar.tts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.reader.Characters
import hu.konyvtar.tts.reader.Recap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * „Hol voltam?" — a legutóbb hallgatott rész legjellemzőbb mondatai.
 *
 * Nem összefoglaló: a könyv saját mondatai, eredeti sorrendben. És sosem néz
 * túl azon, ameddig eljutottál.
 */
@Composable
fun RecapDialog(
    paragraphs: List<String>,
    toPara: Int,
    toChar: Int,
    onDismiss: () -> Unit
) {
    var lines by remember { mutableStateOf<List<Recap.Line>?>(null) }

    LaunchedEffect(toPara, toChar) {
        lines = withContext(Dispatchers.Default) {
            Recap.of(paragraphs, toPara, toChar, count = 4)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.recap_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            val result = lines
            when {
                result == null -> Working()
                result.isEmpty() -> Text(
                    stringResource(R.string.recap_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (l in result) {
                        Text(
                            text = l.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.recap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/**
 * Karakternévtár: kik szerepelnek eddig — és hol tűntek fel először.
 *
 * Egy névre koppintva odaugrik az első előfordulásához. Ez nem spoiler: az
 * a rész már mögötted van.
 */
@Composable
fun CharactersDialog(
    paragraphs: List<String>,
    toPara: Int,
    toChar: Int,
    onGoTo: (paraIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var people by remember { mutableStateOf<List<Characters.Person>?>(null) }

    LaunchedEffect(toPara, toChar) {
        people = withContext(Dispatchers.Default) {
            Characters.find(paragraphs, toPara, toChar)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.chars_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            val result = people
            when {
                result == null -> Working()
                result.isEmpty() -> Text(
                    stringResource(R.string.chars_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Column {
                    Text(
                        stringResource(R.string.chars_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(result) { p ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onGoTo(p.firstParaIndex) }
                                    .padding(vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.chars_count, p.count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (p.firstSentence.isNotBlank()) {
                                    Text(
                                        text = p.firstSentence,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** Mindkét számolás végigmegy az eddig olvasott szövegen — ez eltarthat. */
@Composable
private fun Working() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.knowledge_working),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
