package com.jarves.mh.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jarves.mh.R
import com.jarves.mh.model.Project
import com.jarves.mh.runtime.CodexCliController
import com.jarves.mh.runtime.CodexDeviceChallenge
import com.jarves.mh.runtime.CodexModelOption
import com.jarves.mh.runtime.CodexModelSettings
import com.jarves.mh.runtime.RuntimeInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CodexToolsPanel(projects: List<Project>, projectBusy: Boolean) {
    val context = LocalContext.current
    val installer = remember(context.applicationContext) { RuntimeInstaller(context.applicationContext) }
    val controller = remember(installer) { CodexCliController(context, installer) }
    val modelSettings = remember(context.applicationContext) { CodexModelSettings(context.applicationContext) }
    var modelOptions by remember { mutableStateOf<List<CodexModelOption>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var activeModel by remember { mutableStateOf(modelSettings.model) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(installer.installedCodexVersion()) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var challenge by remember { mutableStateOf<CodexDeviceChallenge?>(null) }
    var confirmRemoval by remember { mutableStateOf(false) }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var projectMenu by remember { mutableStateOf(false) }
    var confirmReview by remember { mutableStateOf<Project?>(null) }
    var reviewText by remember { mutableStateOf<String?>(null) }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    var operation by remember { mutableStateOf<Job?>(null) }
    val enabled = operation == null

    fun run(body: suspend () -> Unit) {
        if (operation != null) return
        message = null
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                body()
            } catch (_: TimeoutCancellationException) {
                message = context.getString(R.string.codex_operation_timed_out)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = context.getString(R.string.codex_operation_failed) + " (" + error.javaClass.simpleName + ")"
            } finally {
                challenge = null
                progress = null
                operation = null
            }
        }
        operation = job
        job.start()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(version?.let { stringResource(R.string.codex_installed_version, it) }
            ?: stringResource(R.string.codex_missing))
        OutlinedButton(enabled = enabled, onClick = {
            run {
                withContext(Dispatchers.IO) {
                    installer.installCodex { event ->
                        withContext(Dispatchers.Main) {
                            message = event.message
                            progress = event.fraction.coerceIn(0f, 1f)
                        }
                    }
                }
                version = installer.installedCodexVersion()
                message = context.getString(R.string.codex_ready)
            }
        }) { Text(stringResource(if (version == null) R.string.codex_install else R.string.codex_update)) }
        TextButton(enabled = enabled, onClick = {
            run {
                val latest = withContext(Dispatchers.IO) { installer.latestCodexVersion() }
                message = context.getString(R.string.codex_available_version, latest)
            }
        }) { Text(stringResource(R.string.codex_check_updates)) }
        if (version != null) {
            TextButton(enabled = enabled, onClick = {
                run {
                    withContext(Dispatchers.IO) {
                        installer.restoreBundledCodex { event ->
                            withContext(Dispatchers.Main) { message = event.message; progress = event.fraction.coerceIn(0f, 1f) }
                        }
                    }
                    version = installer.installedCodexVersion()
                    message = context.getString(R.string.codex_ready)
                }
            }) { Text(stringResource(R.string.codex_restore_bundled)) }
            TextButton(enabled = enabled, onClick = {
                run {
                    message = context.getString(if (controller.signedIn()) R.string.codex_signed_in else R.string.codex_signed_out)
                }
            }) { Text(stringResource(R.string.codex_account_status)) }
            TextButton(enabled = enabled, onClick = {
                run {
                    val signedIn = controller.signIn { next -> withContext(Dispatchers.Main) { challenge = next } }
                    message = context.getString(if (signedIn) R.string.codex_signed_in else R.string.codex_device_failed)
                }
            }) { Text(stringResource(R.string.codex_sign_in)) }
            TextButton(enabled = enabled, onClick = {
                run {
                    val signedOut = controller.signOut()
                    if (signedOut) { activeModel = ""; modelOptions = emptyList() }
                    message = context.getString(if (signedOut) R.string.codex_signed_out else R.string.codex_operation_failed)
                }
            }) { Text(stringResource(R.string.codex_sign_out)) }
            TextButton(enabled = enabled, onClick = {
                run {
                    message = context.getString(if (controller.sandboxAvailable()) R.string.codex_sandbox_available else R.string.codex_sandbox_blocked)
                }
            }) { Text(stringResource(R.string.codex_check_sandbox)) }
            TextButton(enabled = enabled, onClick = { confirmRemoval = true }) { Text(stringResource(R.string.action_remove)) }
            TextButton(enabled = enabled, onClick = {
                run { modelOptions = controller.models(); modelMenu = true }
            }) { Text(stringResource(R.string.codex_refresh_models)) }
            Box {
                OutlinedButton(enabled = enabled, onClick = { modelMenu = true }) {
                    Text(activeModel.ifBlank { stringResource(R.string.codex_default_model) })
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.codex_default_model)) }, onClick = {
                        activeModel = ""; modelSettings.model = ""; modelMenu = false
                    })
                    modelOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = {
                            activeModel = option.id; modelSettings.model = option.id; modelMenu = false
                        })
                    }
                }
            }
            Text(stringResource(R.string.codex_model_catalog_hint), style = MaterialTheme.typography.bodySmall)
            if (projects.isNotEmpty()) {
                Text(stringResource(R.string.codex_review_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.codex_review_detail), style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(enabled = enabled, onClick = { projectMenu = true }) {
                        Text(selectedProject?.name ?: stringResource(R.string.codex_select_project))
                    }
                    DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(text = { Text(project.name) }, onClick = {
                                selectedProjectId = project.id
                                projectMenu = false
                                reviewText = null
                            })
                        }
                    }
                }
                OutlinedButton(enabled = enabled && !projectBusy && selectedProject != null,
                    onClick = { confirmReview = selectedProject }) {
                    Text(stringResource(R.string.codex_start_review))
                }
                reviewText?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        challenge?.let { current ->
            SelectionContainer { Text(stringResource(R.string.codex_device_code, current.code)) }
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.url)))
                } catch (_: ActivityNotFoundException) {
                    message = context.getString(R.string.codex_browser_missing)
                }
            }) { Text(stringResource(R.string.codex_open_browser)) }
        }
        if (!enabled) {
            TextButton(onClick = { operation?.cancel() }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
    confirmReview?.let { project ->
        AlertDialog(
            onDismissRequest = { confirmReview = null },
            title = { Text(stringResource(R.string.codex_review_title)) },
            text = { Text(stringResource(R.string.codex_review_confirm, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReview = null
                    reviewText = null
                    run {
                        message = context.getString(R.string.codex_review_running)
                        reviewText = controller.reviewProject(project)
                        message = context.getString(R.string.codex_review_complete)
                    }
                }) { Text(stringResource(R.string.codex_start_review)) }
            },
            dismissButton = { TextButton(onClick = { confirmReview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text(stringResource(R.string.codex_remove_title)) },
            text = { Text(stringResource(R.string.codex_remove_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoval = false
                    run {
                        withContext(Dispatchers.IO) { installer.removeCodex() }
                        version = null
                    }
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
