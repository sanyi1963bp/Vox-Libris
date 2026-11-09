package hu.konyvtar.tts.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import hu.konyvtar.tts.R
import hu.konyvtar.tts.ai.AiClient
import hu.konyvtar.tts.ai.AiError
import hu.konyvtar.tts.ai.AiProvider
import hu.konyvtar.tts.ai.AiSettings
import hu.konyvtar.tts.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Egy AI-hiba a felhasználó nyelvén. */
fun aiErrorText(context: Context, e: Throwable): String = when (e) {
    is AiError.NoKey -> context.getString(R.string.ai_err_no_key)
    is AiError.BadKey -> context.getString(R.string.ai_err_bad_key)
    is AiError.Quota -> context.getString(R.string.ai_err_quota, e.message ?: "")
    is AiError.BadModel -> context.getString(R.string.ai_err_model, e.message ?: "")
    is AiError.Blocked -> context.getString(R.string.ai_err_blocked)
    is AiError.Network -> context.getString(R.string.ai_err_network)
    else -> context.getString(R.string.ai_err_other, e.message ?: "")
}

/** A megszemélyesítés neve. */
@Composable
fun personaLabel(id: String): String = stringResource(
    when (id) {
        AiSettings.PERSONA_TEACHER -> R.string.ai_persona_teacher
        AiSettings.PERSONA_AUTHOR -> R.string.ai_persona_author
        AiSettings.PERSONA_CHARACTER -> R.string.ai_persona_character
        AiSettings.PERSONA_LIBRARIAN -> R.string.ai_persona_librarian
        AiSettings.PERSONA_CUSTOM -> R.string.ai_persona_custom
        else -> R.string.ai_persona_companion
    }
)

/**
 * Beállítások → AI-beszélgetés.
 *
 * A „bejelentkezés" a szolgáltató saját API-kulcsa: a felhasználó egyszer
 * megszerzi (a gomb elviszi a megfelelő oldalra), bemásolja, és kész.
 */
@Composable
fun AiSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(AiSettings.enabled(context)) }
    var provider by remember { mutableStateOf(AiSettings.provider(context)) }
    var keyText by remember(provider) { mutableStateOf(AiSettings.apiKey(context, provider)) }
    var keyVisible by remember { mutableStateOf(false) }
    var model by remember(provider) { mutableStateOf(AiSettings.model(context, provider)) }
    var userName by remember { mutableStateOf(AiSettings.userName(context)) }
    var persona by remember { mutableStateOf(AiSettings.persona(context)) }
    var characterName by remember { mutableStateOf(AiSettings.characterName(context)) }
    var customPersona by remember { mutableStateOf(AiSettings.customPersona(context)) }
    var length by remember { mutableStateOf(AiSettings.length(context)) }
    var contextSize by remember { mutableStateOf(AiSettings.contextSize(context)) }
    var guard by remember { mutableStateOf(AiSettings.spoilerGuard(context)) }
    var speak by remember { mutableStateOf(AiSettings.speakAnswers(context)) }
    var extra by remember { mutableStateOf(AiSettings.extraInstructions(context)) }

    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>?>(null) }
    var loadingModels by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }

    fun saveKey() = AiSettings.setApiKey(context, provider, keyText)

    SettingsCard(stringResource(R.string.ai_title)) {
        Text(
            stringResource(R.string.ai_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = enabled, onCheckedChange = {
                enabled = it
                AiSettings.setEnabled(context, it)
            })
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.ai_enable), style = MaterialTheme.typography.bodyMedium)
        }
        if (!enabled) return@SettingsCard

        // ------------------------------------------------------------ szolgáltató
        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_provider))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (p in AiProvider.entries) {
                FilterChip(
                    selected = provider == p,
                    onClick = {
                        saveKey()
                        provider = p
                        AiSettings.setProvider(context, p)
                        testResult = null
                        models = null
                    },
                    label = {
                        Text(
                            if (p.free) p.label + " · " + stringResource(R.string.ai_free) else p.label
                        )
                    }
                )
            }
        }

        // ------------------------------------------------------------ kulcs
        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_key))
        Text(
            stringResource(
                if (provider == AiProvider.GEMINI) R.string.ai_key_help_gemini else R.string.ai_key_help_paid
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { guideOpen = true }) {
            Text(stringResource(R.string.ai_guide_button))
        }
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it.trim(); testResult = null },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ai_key_hint)) },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                }
            }
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }) { Text(stringResource(R.string.ai_get_key)) }
            Button(
                enabled = keyText.isNotBlank() && !testing,
                onClick = {
                    saveKey()
                    testing = true
                    testResult = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            try {
                                AiClient.chat(
                                    provider, keyText, model,
                                    "You are a connection test. Reply with the single word: OK",
                                    listOf(ChatMessage(true, "Test")), 50
                                )
                                null
                            } catch (e: Exception) {
                                e
                            }
                        }
                        testing = false
                        testOk = r == null
                        testResult = if (r == null) context.getString(R.string.ai_test_ok, model)
                        else aiErrorText(context, r)
                    }
                }
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.ai_save_test))
            }
        }
        testResult?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        // ------------------------------------------------------------ modell
        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_model))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it.trim()
                    AiSettings.setModel(context, provider, model)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(provider.defaultModel) }
            )
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                enabled = keyText.isNotBlank() && !loadingModels,
                onClick = {
                    saveKey()
                    loadingModels = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            try {
                                Result.success(AiClient.listModels(provider, keyText))
                            } catch (e: Exception) {
                                Result.failure<List<String>>(e)
                            }
                        }
                        loadingModels = false
                        r.onSuccess { models = it }
                            .onFailure { testOk = false; testResult = aiErrorText(context, it) }
                    }
                }
            ) {
                if (loadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.ai_models_list))
            }
        }
        TextButton(onClick = {
            model = provider.defaultModel
            AiSettings.setModel(context, provider, model)
        }) { Text(stringResource(R.string.ai_model_default, provider.defaultModel)) }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        // ------------------------------------------------------------ jellemzők
        Label(stringResource(R.string.ai_user_name))
        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it; AiSettings.setUserName(context, it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ai_user_name_hint)) }
        )

        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_persona))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (p in AiSettings.PERSONAS) {
                FilterChip(
                    selected = persona == p,
                    onClick = { persona = p; AiSettings.setPersona(context, p) },
                    label = { Text(personaLabel(p)) }
                )
            }
        }
        if (persona == AiSettings.PERSONA_CHARACTER) {
            OutlinedTextField(
                value = characterName,
                onValueChange = { characterName = it; AiSettings.setCharacterName(context, it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.ai_character_hint)) }
            )
        }
        if (persona == AiSettings.PERSONA_CUSTOM) {
            OutlinedTextField(
                value = customPersona,
                onValueChange = { customPersona = it; AiSettings.setCustomPersona(context, it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                placeholder = { Text(stringResource(R.string.ai_custom_hint)) }
            )
        }

        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_length))
        ChipRow(
            options = listOf(
                AiSettings.LENGTH_SHORT to stringResource(R.string.ai_length_short),
                AiSettings.LENGTH_MEDIUM to stringResource(R.string.ai_length_medium),
                AiSettings.LENGTH_LONG to stringResource(R.string.ai_length_long)
            ),
            selected = length,
            onSelect = { length = it; AiSettings.setLength(context, it) }
        )

        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_context))
        Text(
            stringResource(R.string.ai_context_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChipRow(
            options = listOf(
                AiSettings.CONTEXT_SMALL to stringResource(R.string.ai_context_small),
                AiSettings.CONTEXT_MEDIUM to stringResource(R.string.ai_context_medium),
                AiSettings.CONTEXT_LARGE to stringResource(R.string.ai_context_large)
            ),
            selected = contextSize,
            onSelect = { contextSize = it; AiSettings.setContextSize(context, it) }
        )

        Spacer(Modifier.height(6.dp))
        SwitchLine(
            checked = guard,
            title = stringResource(R.string.ai_spoiler),
            desc = stringResource(R.string.ai_spoiler_desc),
            onChange = { guard = it; AiSettings.setSpoilerGuard(context, it) }
        )
        SwitchLine(
            checked = speak,
            title = stringResource(R.string.ai_speak),
            desc = stringResource(R.string.ai_speak_desc),
            onChange = { speak = it; AiSettings.setSpeakAnswers(context, it) }
        )

        Spacer(Modifier.height(6.dp))
        Label(stringResource(R.string.ai_extra))
        OutlinedTextField(
            value = extra,
            onValueChange = { extra = it; AiSettings.setExtraInstructions(context, it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
            placeholder = { Text(stringResource(R.string.ai_extra_hint)) }
        )
    }

    // ------------------------------------------------------------ részletes útmutató
    if (guideOpen) {
        val gemini = stringResource(R.string.ai_guide_gemini)
        val paid = stringResource(R.string.ai_guide_paid)
        AlertDialog(
            onDismissRequest = { guideOpen = false },
            title = { Text(stringResource(R.string.ai_guide_title)) },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    // A választott szolgáltató útmutatója kerül előre
                    val first = if (provider == AiProvider.GEMINI) gemini else paid
                    val second = if (provider == AiProvider.GEMINI) paid else gemini
                    Text(first, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(second, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    guideOpen = false
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }) { Text(stringResource(R.string.ai_get_key)) }
            },
            dismissButton = {
                TextButton(onClick = { guideOpen = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    // ------------------------------------------------------------ modellválasztó
    val list = models
    if (list != null) {
        AlertDialog(
            onDismissRequest = { models = null },
            title = { Text(stringResource(R.string.ai_models_title)) },
            text = {
                if (list.isEmpty()) {
                    Text(stringResource(R.string.ai_models_empty))
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list) { m ->
                            Text(
                                m,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (m == model) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        model = m
                                        AiSettings.setModel(context, provider, m)
                                        models = null
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { models = null }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((id, label) in options) {
            FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchLine(checked: Boolean, title: String, desc: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
