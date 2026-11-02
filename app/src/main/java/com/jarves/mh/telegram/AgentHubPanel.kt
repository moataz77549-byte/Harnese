package com.jarves.mh.telegram

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jarves.mh.R
import com.jarves.mh.connect.HarneseEventBus
import com.jarves.mh.ui.HarneseRuntimeOwner

@Composable
fun AgentHubPanel() {
    val context = LocalContext.current
    val runtime = remember { HarneseRuntimeOwner.get(context.applicationContext as Application) }
    val state by runtime.state.collectAsState()
    val journal = remember { HarneseEventBus.get(context) }
    var events by remember { mutableStateOf(journal.recentEvents()) }
    var pending by remember { mutableStateOf(journal.pendingCount()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.activeProject?.name ?: "—")
        Text("${state.activeChatId.orEmpty()} / ${state.agentKind.name}")
        Text(if (state.agentKind == com.jarves.mh.model.AgentKind.ANTIGRAVITY) state.antigravityModel else state.provider.model)
        Text(stringResource(R.string.hub_tasks, state.isRunning.toString(), state.androidBuildRunning.toString()))
        Text(state.androidBuildMessage.orEmpty())
        state.pendingApproval?.let { Text("${it.toolName}: ${it.risk}") }
        Text(stringResource(R.string.hub_pending, pending))
        state.activeProject?.let { project ->
            OutlinedButton(onClick = { journal.setSubscription(project.id, true) }) { Text(stringResource(R.string.hub_subscribe)) }
            OutlinedButton(onClick = { journal.setSubscription(project.id, false) }) { Text(stringResource(R.string.hub_unsubscribe)) }
        }
        OutlinedButton(onClick = { events = journal.recentEvents(); pending = journal.pendingCount() }) { Text(stringResource(R.string.hub_refresh)) }
        events.forEach { Text(it) }
    }
}
