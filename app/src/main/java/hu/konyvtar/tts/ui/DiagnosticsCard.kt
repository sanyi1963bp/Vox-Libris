package hu.konyvtar.tts.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.EventLog
import hu.konyvtar.tts.data.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A kívülről érkező gombparancsok naplója.
 *
 * Azért van a beállítások közt, mert nem mindennapi funkció: akkor kell elővenni,
 * ha valami külső eszköz — fülhallgató, autós fejegység — nem úgy viselkedik,
 * ahogy kellene. A napló a telefonon marad; a „Mentés" gomb átteszi a Letöltések
 * mappába, hogy kábelen el lehessen hozni.
 */
@Composable
fun DiagnosticsCard() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }

    // A naplót minden nyitáskor újraolvassuk: közben érkezhettek bejegyzések.
    LaunchedEffect(open, reload) {
        val res = withContext(Dispatchers.IO) {
            EventLog.lineCount(context) to if (open) EventLog.read(context) else ""
        }
        count = res.first
        text = res.second
    }

    SettingsCard(stringResource(R.string.set_diag_title)) {
        Text(
            text = stringResource(R.string.set_diag_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.heightIn(min = 6.dp))
        Text(
            text = if (count == 0) stringResource(R.string.set_diag_empty)
            else stringResource(R.string.set_diag_count, count),
            style = MaterialTheme.typography.bodySmall
        )

        if (open && text.isNotEmpty()) {
            Spacer(Modifier.heightIn(min = 6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Két irányban görgethető: a sorok hosszúak, és a napló is az.
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        }

        Spacer(Modifier.heightIn(min = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = !open }) {
                Text(
                    stringResource(
                        if (open) R.string.set_diag_hide else R.string.set_diag_show
                    )
                )
            }
            OutlinedButton(
                enabled = count > 0,
                onClick = {
                    val out = EventLog.exportTo(context, Exporter.targetDir())
                    Toast.makeText(
                        context,
                        if (out == null) context.getString(R.string.set_diag_export_failed)
                        else context.getString(R.string.set_diag_exported, out.name),
                        Toast.LENGTH_LONG
                    ).show()
                }
            ) { Text(stringResource(R.string.set_diag_export)) }
            OutlinedButton(
                enabled = count > 0,
                onClick = {
                    EventLog.clear(context)
                    reload++
                }
            ) { Text(stringResource(R.string.set_diag_clear)) }
        }
    }
}
