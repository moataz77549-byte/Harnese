package com.jarves.mh.telegram

import android.app.Application
import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val reviews = remember { com.jarves.mh.connect.SecureReviewApprovalService(java.io.File(context.filesDir, "secure-reviews")) }
    var reviewList by remember { mutableStateOf(reviews.records()) }
    var selectedReview by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf("") }
    var reviewMessage by remember { mutableStateOf("") }
    var rollback by remember { mutableStateOf(false) }
    val authenticate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) selectedReview?.let { id ->
            runCatching { if (rollback) reviews.rollbackLocally(id) else reviews.applyLocally(id) }
                .onSuccess { reviewMessage = "✓"; reviewList = reviews.records(); runtime.refreshProjectFiles() }
                .onFailure { reviewMessage = it.message.orEmpty() }
        }
        selectedReview = null
    }
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
        Text(stringResource(R.string.hub_uncertain, journal.uncertainCount()))
        state.activeProject?.let { project ->
            OutlinedButton(onClick = { journal.setSubscription(project.id, true) }) { Text(stringResource(R.string.hub_subscribe)) }
            OutlinedButton(onClick = { journal.setSubscription(project.id, false) }) { Text(stringResource(R.string.hub_unsubscribe)) }
        }
        OutlinedButton(onClick = { events = journal.recentEvents(); pending = journal.pendingCount(); reviewList = reviews.records() }) { Text(stringResource(R.string.hub_refresh)) }
        reviewList.filter { it.state in setOf("pending", "applied", "applying") }.forEach { review ->
            Text("${review.id}: ${review.state} (${review.entries.size})")
            OutlinedButton(onClick = { preview = reviews.preview(review.id); selectedReview = review.id }) { Text(stringResource(R.string.hub_review_preview)) }
            if (selectedReview == review.id && preview.isNotEmpty()) {
                Text(preview)
                OutlinedButton(onClick = {
                    val manager = context.getSystemService(KeyguardManager::class.java)
                    val intent = manager.createConfirmDeviceCredentialIntent("Harnese Nova", review.id)
                    if (intent != null) { rollback = review.state != "pending"; authenticate.launch(intent) }
                    else reviewMessage = context.getString(R.string.hub_lock_required)
                }) { Text(stringResource(if (review.state == "pending") R.string.hub_review_apply else R.string.hub_review_rollback)) }
                if (review.state == "pending") OutlinedButton(onClick = { reviews.reject(review.id); reviewList = reviews.records(); selectedReview = null }) { Text(stringResource(R.string.hub_review_reject)) }
            }
        }
        if (reviewMessage.isNotBlank()) Text(reviewMessage)
        com.jarves.mh.mcp.McpSettingsPanel(state.activeProject?.id, state.agentKind.stableId)
        events.forEach { Text(it) }
    }
}
