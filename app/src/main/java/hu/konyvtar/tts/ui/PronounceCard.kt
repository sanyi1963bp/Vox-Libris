package hu.konyvtar.tts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Pronounce
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kiejtési szótár a beállításokban: a szabályok áttekintése, felvétele,
 * törlése.
 *
 * A napi használatban nem innen veszel fel szabályt, hanem olvasás közben:
 * hosszú nyomás a mondaton → Kiejtés. Ez a kártya arra való, hogy lásd,
 * mi van már beállítva, és hogy egy elrontott átírást ki tudj venni.
 */
@Composable
fun PronounceCard() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf<List<Pronounce.Rule>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        rules = withContext(Dispatchers.IO) { AppDb.pronounceRules() }
    }

    /** A szótár változott: a felolvasó a következő mondattól már az újat használja. */
    fun applied() {
        Pronounce.invalidate()
        if (TtsService.state.value.path != null) {
            TtsService.send(context, TtsService.ACTION_PRONOUNCE_CHANGED)
        }
        reload++
    }

    SettingsCard(stringResource(R.string.set_pron_title)) {
        Text(
            stringResource(R.string.set_pron_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        if (rules.isEmpty()) {
            Text(
                stringResource(R.string.set_pron_empty),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            for (r in rules) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.set_pron_rule, r.pattern, r.sayAs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(
                        onClick = {
                            AppDb.deletePronounce(r.id)
                            applied()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        TextButton(onClick = { addOpen = true }) {
            Text(stringResource(R.string.set_pron_add))
        }
    }

    if (addOpen) {
        AddRuleDialog(
            onSave = { pattern, sayAs ->
                AppDb.setPronounce(pattern, sayAs)
                applied()
                addOpen = false
            },
            onDismiss = { addOpen = false }
        )
    }
}

@Composable
private fun AddRuleDialog(
    onSave: (pattern: String, sayAs: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf("") }
    var sayAs by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.set_pron_add),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.pron_word)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sayAs,
                    onValueChange = { sayAs = it },
                    label = { Text(stringResource(R.string.pron_say_as)) },
                    placeholder = { Text(stringResource(R.string.pron_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && sayAs.isNotBlank(),
                onClick = { onSave(pattern, sayAs) }
            ) { Text(stringResource(R.string.pron_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}
