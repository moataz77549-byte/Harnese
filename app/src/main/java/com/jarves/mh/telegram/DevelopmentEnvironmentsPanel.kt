package com.jarves.mh.telegram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jarves.mh.BuildConfig
import com.jarves.mh.R
import com.jarves.mh.model.DevStack
import com.jarves.mh.runtime.RuntimeInstaller
import com.jarves.mh.ui.HarneseRuntimeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun DevelopmentEnvironmentsPanel(runtime: HarneseRuntimeController) {
    val context = LocalContext.current
    val state by runtime.state.collectAsState()
    var projectName by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, state.devStackInstalling) {
        report = withContext(Dispatchers.IO) {
            val installer = RuntimeInstaller(context)
            val embedded = if (BuildConfig.OFFLINE_RUNTIME_BUNDLES) runCatching {
                val bundles = JSONObject(context.assets.open("runtime/offline-manifest.json").bufferedReader().use { it.readText() }).getJSONObject("bundles")
                bundles.keys().asSequence().filter { it != "agy" }.joinToString("\n") { key ->
                    val item = bundles.getJSONObject(key)
                    "$key ${item.getString("version")}: ${item.getLong("compressedBytes")} / ${item.getLong("uncompressedBytes")} bytes"
                }
            }.getOrElse { "Embedded manifest missing" } else "Online edition / تنزيل عند الطلب"
            val free = android.text.format.Formatter.formatFileSize(context, context.filesDir.usableSpace)
            val stacks = installer.installedStacks().joinToString(", ") { it.name }.ifBlank { "—" }
            val runtimeStatus = context.getString(if (installer.isInstalled()) R.string.runtime_setup_status_ready else R.string.vm_linux_not_ready)
            "$embedded\nUbuntu / PRoot · $runtimeStatus\n${context.getString(R.string.nova_free_space, free)}\n$stacks"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.hub_environments))
        Text(report)
        OutlinedTextField(value = projectName, onValueChange = { projectName = it }, label = { Text("Project / المشروع") })
        OutlinedButton(onClick = { runtime.createDevelopmentProject(projectName, true) }, enabled = projectName.isNotBlank()) { Text("TypeScript — Offline template") }
        OutlinedButton(onClick = { runtime.createDevelopmentProject(projectName, false) }, enabled = projectName.isNotBlank()) { Text("Node.js — Offline template") }
        for (name in listOf("python", "node", "typescript", "androidsdk", "runtimehealth", "disk")) {
            OutlinedButton(onClick = { runtime.requestEnvironmentProbe(name) }, enabled = !state.runtimeProbeRunning && !state.isRunning && !state.androidBuildRunning) { Text("$name — ${stringResource(R.string.hub_probe)}") }
        }
        if (BuildConfig.OFFLINE_RUNTIME_BUNDLES) for (name in listOf("python", "node", "typescript", "androidsdk")) {
            OutlinedButton(onClick = { runtime.repairDevelopmentEnvironment(name) }, enabled = !state.runtimeProbeRunning && !state.isRunning && !state.androidBuildRunning) { Text("$name — ${stringResource(R.string.hub_repair)}") }
        }
        state.pendingRuntimeInstall?.let { name ->
            Text("${stringResource(R.string.hub_optional_install)}: $name")
            OutlinedButton(onClick = { runtime.installRequestedRuntime() }) { Text(stringResource(R.string.hub_confirm_download)) }
        }
        Text(state.agentMessage.orEmpty())
        Text(state.devStackMessage.orEmpty())
        Text(state.agentUpdateMessage.orEmpty())
        OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.hub_refresh)) }
    }
}
