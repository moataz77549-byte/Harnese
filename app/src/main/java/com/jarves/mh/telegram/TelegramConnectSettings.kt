package com.jarves.mh.telegram

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarves.mh.R

/** Telegram gateway can only be started explicitly from the owner's phone. */
@Composable
fun TelegramConnectSettings() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember(context) { TelegramSecretStore(context.applicationContext) }
    var tokenEntry by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(store.hasToken()) }
    var linkedId by remember { mutableStateOf(store.linkedUserId()) }
    var pairCode by remember { mutableStateOf<String?>(null) }
    var enablePrompts by remember { mutableStateOf(store.remoteTasksEnabled()) }
    val journal = remember(context) { com.jarves.mh.connect.HarneseEventBus.get(context) }
    var streamContent by remember { mutableStateOf(journal.streamingEnabled()) }
    var syncContent by remember { mutableStateOf(journal.contentEnabled()) }
    var mute by remember { mutableStateOf(journal.muted()) }
    var showHub by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val connection by TelegramPollingService.connection.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(stringResource(R.string.tg_intro), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = tokenEntry, onValueChange = { tokenEntry = it }, singleLine = true,
            label = { Text(stringResource(R.string.tg_bot_token)) },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            runCatching {
                context.stopService(Intent(context, TelegramPollingService::class.java))
                store.saveToken(tokenEntry)
            }.onSuccess {
                tokenEntry = ""
                configured = true
                linkedId = 0L
                pairCode = null
                message = context.getString(R.string.tg_token_saved)
            }.onFailure { message = it.message ?: "Could not save token" }
        }, enabled = tokenEntry.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tg_save_token))
        }
        Text(if (configured) stringResource(R.string.tg_token_configured)
             else stringResource(R.string.tg_token_missing), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.tg_linked_account, if (linkedId == 0L) "—" else linkedId.toString()))
        if (configured) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                    runCatching { store.generatePairCode() }
                        .onSuccess { pairCode = it; message = context.getString(R.string.tg_pair_instructions) }
                        .onFailure { message = it.message ?: "Pairing failed" }
                }) { Text(stringResource(R.string.tg_pair_code_button)) }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                    store.unlink()
                    linkedId = 0L
                    pairCode = null
                    message = context.getString(R.string.tg_unlinked)
                }) { Text(stringResource(R.string.tg_unlink)) }
            }
            pairCode?.let { code ->
                Text(stringResource(R.string.tg_pair_code_display, code), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString("/pair $code")) }) {
                    Text(stringResource(R.string.tg_copy_pair))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.tg_allow_prompts), modifier = Modifier.weight(1f))
                Switch(checked = enablePrompts, onCheckedChange = {
                    enablePrompts = it
                    store.setRemoteTasksEnabled(it)
                })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hub_sync_content), modifier = Modifier.weight(1f))
                Switch(checked = syncContent, onCheckedChange = { syncContent = it; journal.setContent(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hub_stream), modifier = Modifier.weight(1f))
                Switch(checked = streamContent, enabled = syncContent, onCheckedChange = { streamContent = it; journal.setStreaming(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hub_mute), modifier = Modifier.weight(1f))
                Switch(checked = mute, onCheckedChange = { mute = it; journal.setMuted(it) })
            }
            OutlinedButton(onClick = { showHub = !showHub }) { Text("Agent Hub") }
            if (showHub) AgentHubPanel()
            Text(stringResource(R.string.tg_security_notice), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    runCatching {
                        ContextCompat.startForegroundService(context,
                            Intent(context, TelegramPollingService::class.java))
                    }.onFailure { message = it.message ?: "Unable to start service" }
                }) { Text(stringResource(R.string.tg_start)) }
                OutlinedButton(onClick = {
                    context.stopService(Intent(context, TelegramPollingService::class.java))
                    message = context.getString(R.string.tg_stopped)
                }) { Text(stringResource(R.string.tg_stop)) }
            }
            Text(stringResource(R.string.tg_connection_status, connection),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { linkedId = store.linkedUserId() }) {
                Text(stringResource(R.string.tg_refresh_link))
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}
