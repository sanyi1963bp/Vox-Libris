package hu.konyvtar.tts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import hu.konyvtar.tts.data.Prefs
import hu.konyvtar.tts.tts.TtsService

/**
 * Visszakapcsolás a Bluetooth kötelező alapjára.
 *
 * Az autós fejegységek AVRCP-megvalósítása nagyon egyenetlen. A drágább
 * fülhallgatók mindent elviselnek, amit küldünk; egy olcsó fejegység viszont
 * elnémulhat attól, ha saját gombokat, tekerhető idővonalat vagy borítóképet
 * kap. Ez a kapcsoló mindezt leveszi, és csak azt hagyja meg, amit a szabvány
 * kötelezővé tesz.
 *
 * Nem gyógyszer, hanem próba: ha ettől megjavul, tudjuk, hogy a ráadás volt a
 * baj — és onnan lehet visszafelé haladva megkeresni, melyik.
 */
@Composable
fun BluetoothCard() {
    val context = LocalContext.current
    var simple by remember { mutableStateOf(Prefs.simpleBluetooth(context)) }

    SettingsCard(stringResource(R.string.set_bt_title)) {
        Text(
            text = stringResource(R.string.set_bt_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = simple,
                onCheckedChange = {
                    simple = it
                    Prefs.setSimpleBluetooth(context, it)
                    // Azonnal újra bemutatkozunk: ne kelljen a felolvasást
                    // leállítani és újraindítani a váltáshoz.
                    TtsService.send(context, TtsService.ACTION_BT_MODE_CHANGED)
                }
            )
            Column {
                Text(
                    stringResource(R.string.set_bt_switch),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.set_bt_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
