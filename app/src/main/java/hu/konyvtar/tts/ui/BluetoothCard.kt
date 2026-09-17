package hu.konyvtar.tts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.tts.TtsService

/**
 * Mennyit mondjunk a csatlakozó Bluetooth-eszközöknek.
 *
 * Az autós fejegységek AVRCP-megvalósítása nagyon egyenetlen. A fülhallgatók
 * mindent elviselnek, amit küldünk; egy olcsó fejegység viszont elnémulhat
 * attól, ha többet kap, mint amennyit kezelni tud.
 *
 * Három fokozat, mert a „minden vagy semmi" pazarlás volt: a borítókép a fő
 * gyanúsított, a fejezetfelirat és a haladásjelző viszont pár bájt. A középső
 * fokozat ezt a kettőt szétválasztja — így a kocsi is működhet úgy, hogy a
 * hasznos kijelzés megmarad.
 */
@Composable
fun BluetoothCard() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(Prefs.btMode(context)) }

    fun choose(value: String) {
        mode = value
        Prefs.setBtMode(context, value)
        // Azonnal újra bemutatkozunk: ne kelljen a felolvasást leállítani és
        // újraindítani a váltáshoz.
        TtsService.send(context, TtsService.ACTION_BT_MODE_CHANGED)
    }

    SettingsCard(stringResource(R.string.set_bt_title)) {
        Text(
            text = stringResource(R.string.set_bt_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        BtOption(
            selected = mode == Prefs.BT_FULL,
            label = stringResource(R.string.set_bt_full),
            hint = stringResource(R.string.set_bt_full_hint),
            onClick = { choose(Prefs.BT_FULL) }
        )
        BtOption(
            selected = mode == Prefs.BT_NO_COVER,
            label = stringResource(R.string.set_bt_nocover),
            hint = stringResource(R.string.set_bt_nocover_hint),
            onClick = { choose(Prefs.BT_NO_COVER) }
        )
        BtOption(
            selected = mode == Prefs.BT_SIMPLE,
            label = stringResource(R.string.set_bt_simple),
            hint = stringResource(R.string.set_bt_simple_hint),
            onClick = { choose(Prefs.BT_SIMPLE) }
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.set_bt_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Egy fokozat: rádiógomb, felirat, alatta egy mondat arról, mit jelent.
 *
 * A magyarázat azért van itt és nem súgóban, mert ezt a beállítást az ember
 * akkor nyitja meg, amikor valami nem működik — olyankor nincs kedve keresgélni.
 */
@Composable
private fun BtOption(
    selected: Boolean,
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
