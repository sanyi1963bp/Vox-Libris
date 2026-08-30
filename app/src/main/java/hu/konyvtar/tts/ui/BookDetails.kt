package hu.konyvtar.tts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.AppDb
import hu.konyvtar.tts.data.BookMeta
import hu.konyvtar.tts.data.Catalog
import hu.konyvtar.tts.data.MetadataExtractor
import hu.konyvtar.tts.data.Normalizer
import hu.konyvtar.tts.model.CatalogBook
import hu.konyvtar.tts.model.FINISHED_PERCENT
import hu.konyvtar.tts.model.displayPercent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Egy könyv azonosítása: ennyit tud róla minden képernyő. */
data class BookRef(
    val path: String,
    val title: String,
    val author: String,
    /** A katalógusbeli azonosító, ha ismert. Ha nincs, útvonal alapján keressük. */
    val bookId: Long? = null
)

/**
 * A könyv adatlapja — egyetlen példányban, a listának, a fájlböngészőnek és
 * az olvasónak egyaránt.
 *
 * Korábban három, betű szerint azonos másolat élt belőle, és emiatt fordult
 * elő, hogy egy javítás csak kettőben landolt. Az adatokat maga tölti be:
 * előbb a katalógusból, és ha ott nincs semmi, magából a fájlból — így a
 * katalóguson kívüli könyvek is megmutatják, amit tudnak magukról.
 *
 * @param onOpen ha meg van adva, megjelenik a „megnyitás” gomb is
 * @param extraLines képernyőnként eltérő sorok (az olvasó pl. a bekezdések
 *   és fejezetek számát teszi ide)
 */
@Composable
fun BookDetailsDialog(
    book: BookRef,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)? = null,
    extraLines: List<Pair<String, String?>> = emptyList()
) {
    val context = LocalContext.current
    var entry by remember(book.path) { mutableStateOf<CatalogBook?>(null) }
    var fileMeta by remember(book.path) { mutableStateOf<BookMeta?>(null) }
    var percent by remember(book.path) { mutableStateOf<Double?>(null) }

    LaunchedEffect(book.path) {
        withContext(Dispatchers.IO) {
            val found = book.bookId?.let { Catalog.bookById(it) } ?: Catalog.bookForPath(book.path)
            entry = found
            percent = AppDb.progressFor(book.path)?.displayPercent()
            // Csak akkor nyúlunk a fájlhoz, ha a katalógus nem tud róla
            if (found == null) {
                fileMeta = MetadataExtractor.extract(context, File(book.path), true)
            }
        }
    }

    val f = File(book.path)
    val ext = f.name.substringAfterLast('.', "").lowercase()
    val shownTitle = entry?.cim?.clean() ?: fileMeta?.title?.clean() ?: book.title
    val shownAuthor = entry?.szerzo?.clean() ?: fileMeta?.author?.clean() ?: book.author

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BookCover(
                    title = shownTitle,
                    author = shownAuthor,
                    modifier = Modifier
                        .width(104.dp)
                        .aspectRatio(2f / 3f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = shownTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (shownAuthor.isNotBlank()) {
                    Text(
                        text = shownAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Formátum és ami belőle következik a felolvasásra
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormatBadge(ext)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(formatNoteRes(ext)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))

                percent?.takeIf { it > 0.05 }?.let { p ->
                    val done = p >= FINISHED_PERCENT
                    Text(
                        text = if (done) stringResource(R.string.info_finished)
                        else stringResource(
                            R.string.info_progress,
                            String.format(Locale.getDefault(), "%.1f", p)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { (p / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = progressBarColor(done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                DetailLine(
                    stringResource(R.string.info_publisher),
                    entry?.kiado ?: fileMeta?.publisher
                )
                DetailLine(
                    stringResource(R.string.info_year),
                    entry?.kiadasEve ?: fileMeta?.year
                )
                DetailLine(stringResource(R.string.info_isbn), entry?.isbn ?: fileMeta?.isbn)
                DetailLine(
                    stringResource(R.string.info_series),
                    seriesLine(
                        entry?.sorozat ?: fileMeta?.series,
                        entry?.sorozatSzama ?: fileMeta?.seriesIndex
                    )
                )
                DetailLine(stringResource(R.string.info_tags), entry?.cimkek ?: fileMeta?.tags)
                DetailLine(stringResource(R.string.info_file), f.name)
                DetailLine(stringResource(R.string.info_size), fmtSize(f.length()))
                DetailLine(stringResource(R.string.info_modified), fmtDate(f.lastModified()))
                DetailLine(
                    stringResource(R.string.info_folder),
                    book.path.substringBeforeLast('/')
                )
                for ((label, value) in extraLines) DetailLine(label, value)

                val desc = (entry?.leiras ?: fileMeta?.description)?.clean()
                if (!desc.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.info_description),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (onOpen != null) {
                TextButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    Text("  " + stringResource(R.string.stats_read_screen))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        },
        dismissButton = if (onOpen != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
        } else null
    )
}

/** Egy címke-érték sor; az üres és az „N/A” értékek kimaradnak. */
@Composable
fun DetailLine(label: String, value: String?) {
    val v = value?.clean()
    if (v.isNullOrBlank()) return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = v, style = MaterialTheme.typography.bodySmall)
    }
}

/** Sorozat és kötetszám egy sorban: „Dűne #2”. */
private fun seriesLine(series: String?, index: String?): String? = listOfNotNull(
    series?.clean()?.takeIf { it.isNotBlank() },
    index?.clean()?.takeIf { it.isNotBlank() }
).joinToString(" #").ifBlank { null }

/** Láthatatlan karakterek és a katalógusban használt „N/A” kiszűrése. */
private fun String.clean(): String? {
    val t = Normalizer.stripInvisible(this).trim()
    return if (t.isEmpty() || t == "N/A") null else t
}
