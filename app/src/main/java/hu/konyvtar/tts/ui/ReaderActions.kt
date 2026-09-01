package hu.konyvtar.tts.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.data.Pronounce
import hu.konyvtar.tts.data.QuoteCard
import hu.konyvtar.tts.reader.Sentences
import hu.konyvtar.tts.tts.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

/**
 * A könyv szövegének műveletmenüje: a megérintett mondattal dolgozik.
 *
 * Miért mondat, és miért nem kijelölés? Mert a szövegkijelölés harcolna a
 * dupla koppintással, ami itt a felolvasás indítása, nagy betűmérettel pedig
 * egy kézzel amúgy is kínlódás. A mondat viszont az a darab, amivel a
 * felolvasó is dolgozik — így az, amit a menü mutat, pontosan az, amit épp
 * hallasz.
 *
 * Ahol mégis szó kell (kiejtés, Wikipédia), a mondat szavai koppintható
 * jelvényként jelennek meg. Kijelölgetés nélkül.
 */
@Composable
fun ReaderActionsDialog(
    sentence: String,
    title: String,
    author: String,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // A menü két továbbablakot nyithat: szóválasztót, majd kiejtés-szerkesztőt.
    var pickFor by remember { mutableStateOf<WordPurpose?>(null) }
    var pronounceWord by remember { mutableStateOf<String?>(null) }

    val quoteColors = QuoteCard.Colors(
        background = MaterialTheme.colorScheme.surface.toArgb(),
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
        muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        accent = MaterialTheme.colorScheme.primary.toArgb()
    )

    val word = pronounceWord
    if (word != null) {
        PronounceDialog(
            word = word,
            onDismiss = { pronounceWord = null; onDismiss() }
        )
        return
    }

    val purpose = pickFor
    if (purpose != null) {
        WordPickerDialog(
            sentence = sentence,
            onPick = { picked ->
                when (purpose) {
                    WordPurpose.PRONOUNCE -> {
                        pickFor = null
                        pronounceWord = picked
                    }
                    WordPurpose.WIKIPEDIA -> {
                        openWikipedia(context, picked)
                        onDismiss()
                    }
                }
            },
            onDismiss = { pickFor = null }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                // Amin a művelet dolgozni fog — hogy ne kelljen találgatni.
                Text(
                    text = sentence.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                )
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    ActionRow(stringResource(R.string.action_bookmark)) {
                        onBookmark()
                        onDismiss()
                    }
                    ActionRow(stringResource(R.string.action_pronounce)) {
                        pickFor = WordPurpose.PRONOUNCE
                    }
                    ActionRow(stringResource(R.string.action_wikipedia)) {
                        pickFor = WordPurpose.WIKIPEDIA
                    }
                    ActionRow(stringResource(R.string.action_quote_card)) {
                        val ok = QuoteCard.share(
                            context, sentence.trim(), title, author, quoteColors
                        )
                        if (!ok) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.action_quote_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onDismiss()
                    }
                    ActionRow(stringResource(R.string.action_copy)) {
                        clipboard.setText(AnnotatedString(sentence.trim()))
                        Toast.makeText(
                            context,
                            context.getString(R.string.action_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                        onDismiss()
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

/** Mire kell a szó: kiejtésre vagy keresésre. */
private enum class WordPurpose { PRONOUNCE, WIKIPEDIA }

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A mondat szavai jelvényként. Két betűnél rövidebbeket nem kínálunk fel — a
 * névelőkre és a kötőszavakra sem kiejtési szabály, sem Wikipédia-cikk nem
 * kell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordPickerDialog(
    sentence: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val words = remember(sentence) { Sentences.words(sentence) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.action_pick_word),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (w in words) {
                    SuggestionChip(onClick = { onPick(w) }, label = { Text(w) })
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
 * Kiejtési szabály szerkesztése egy szóhoz.
 *
 * Ha a szóra már van szabály, azt tölti be — így egy elrontott átírás
 * javítható, nem csak új vehető fel.
 */
@Composable
private fun PronounceDialog(word: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sayAs by remember(word) { mutableStateOf<String?>(null) }

    LaunchedEffect(word) {
        sayAs = withContext(Dispatchers.IO) { AppDb.pronounceFor(word) ?: "" }
    }

    val current = sayAs
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.pron_title, word),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.pron_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = current ?: "",
                    onValueChange = { sayAs = it },
                    placeholder = { Text(stringResource(R.string.pron_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !current.isNullOrBlank(),
                onClick = {
                    AppDb.setPronounce(word, current ?: "")
                    applyPronounceChange(context)
                    Toast.makeText(
                        context,
                        context.getString(R.string.pron_saved, word, (current ?: "").trim()),
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            ) { Text(stringResource(R.string.pron_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

// ---------------------------------------------------------------- segédek

/**
 * A szótár változott. A gyorsítótárat itt helyben ürítjük (a felolvasó
 * ugyanabban a folyamatban fut), a szolgáltatásnak pedig csak akkor szólunk,
 * ha épp szól valami — így az imént kijavított nevet azonnal újra is mondja.
 */
private fun applyPronounceChange(context: Context) {
    Pronounce.invalidate()
    if (TtsService.state.value.path != null) {
        TtsService.send(context, TtsService.ACTION_PRONOUNCE_CHANGED)
    }
}

/**
 * A szó átadása a böngészőnek. **Az app nem tölt le semmit** — csak megkéri a
 * rendszert, hogy nyissa meg a címet. Ezért nincs és nem is kell
 * internet-engedély a manifestben.
 */
private fun openWikipedia(context: Context, word: String) {
    val lang = Prefs.uiLanguage(context).takeIf { it.isNotBlank() }?.substringBefore('-')
        ?: Locale.getDefault().language.ifBlank { "en" }
    val url = "https://$lang.wikipedia.org/wiki/Special:Search?search=" +
        URLEncoder.encode(word, "UTF-8")
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        // Nincs böngésző a készüléken — ritka, de nem omlunk össze tőle.
        Toast.makeText(
            context, context.getString(R.string.action_no_browser), Toast.LENGTH_SHORT
        ).show()
    }
}
