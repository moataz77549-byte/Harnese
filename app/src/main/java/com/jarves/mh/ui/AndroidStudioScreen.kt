package com.jarves.mh.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.model.DevStack
import com.jarves.mh.runtime.AndroidAction
import com.jarves.mh.runtime.AndroidBuildIssue
import com.jarves.mh.runtime.AndroidBuildPhase
import com.jarves.mh.runtime.AndroidBuildStage
import com.jarves.mh.runtime.AndroidHealthCheck
import com.jarves.mh.runtime.AndroidHealthFix
import com.jarves.mh.runtime.AndroidHealthStatus
import com.jarves.mh.runtime.AndroidLogLevel
import com.jarves.mh.runtime.AndroidLogLine
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import androidx.compose.ui.res.stringResource
import com.jarves.mh.R

private enum class AndroidDashboardSection { OVERVIEW, BUILD, LOGCAT, HEALTH }

@Composable
private fun androidStageLabel(stage: AndroidBuildStage): String = stringResource(
    when (stage) {
        AndroidBuildStage.IDLE -> R.string.android_stage_idle
        AndroidBuildStage.PREPARING -> R.string.android_stage_preparing
        AndroidBuildStage.RESOLVING -> R.string.android_stage_resolving
        AndroidBuildStage.COMPILING -> R.string.android_stage_compiling
        AndroidBuildStage.RESOURCES -> R.string.android_stage_resources
        AndroidBuildStage.PACKAGING -> R.string.android_stage_packaging
        AndroidBuildStage.FINDING_APK -> R.string.android_stage_finding_apk
        AndroidBuildStage.INSTALLING -> R.string.android_stage_installing
        AndroidBuildStage.LAUNCHING -> R.string.android_stage_launching
        AndroidBuildStage.COMPLETE -> R.string.android_stage_complete
        AndroidBuildStage.FAILED -> R.string.android_stage_failed
        AndroidBuildStage.CANCELLING -> R.string.android_stage_cancelling
        AndroidBuildStage.CANCELLED -> R.string.android_stage_cancelled
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AndroidDashboardScreen(
    state: AppUiState,
    onClose: () -> Unit,
    onAction: (AndroidAction) -> Unit,
    onCancelBuild: () -> Unit,
    onRefresh: () -> Unit,
    onInstallTools: () -> Unit,
    onShareApk: () -> Unit,
    onSaveApk: () -> Unit,
    onSaveRelease: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onUninstallApp: () -> Unit,
    onOpenIssue: (AndroidBuildIssue) -> Unit,
    onHealthFix: (AndroidHealthFix) -> Unit,
    onRefreshHealth: () -> Unit,
    onStartLogcat: () -> Unit,
    onStopLogcat: () -> Unit,
    onToggleLogcatPause: () -> Unit,
    onClearLogcat: () -> Unit,
    onLogcatQuery: (String) -> Unit,
    onLogcatLevel: (AndroidLogLevel) -> Unit,
    onOpenLogLine: (AndroidLogLine) -> Unit,
) {
    BackHandler(onBack = onClose)
    val clipboard = LocalClipboardManager.current
    var section by rememberSaveable { mutableStateOf(AndroidDashboardSection.OVERVIEW) }
    var showBuildMenu by remember { mutableStateOf(false) }
    var showRawLog by rememberSaveable { mutableStateOf(false) }
    val liveElapsedSeconds = state.androidBuildStartedAtMillis?.let { rememberLiveElapsedSeconds(it) }
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(section) { onStopLogcat() }
    DisposableEffect(Unit) { onDispose(onStopLogcat) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.android_screen_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close)) }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    Box {
                        IconButton(onClick = { showBuildMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = showBuildMenu, onDismissRequest = { showBuildMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.force_rebuild)) },
                                leadingIcon = { Icon(Icons.Default.Build, null) },
                                enabled = !state.androidBuildRunning,
                                onClick = { showBuildMenu = false; onAction(AndroidAction.FORCE_BUILD) },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.nova_build_release_apk)) },
                                enabled = !state.androidBuildRunning,
                                onClick = { showBuildMenu = false; onAction(AndroidAction.RELEASE_APK) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.nova_build_bundle)) },
                                enabled = !state.androidBuildRunning,
                                onClick = { showBuildMenu = false; onAction(AndroidAction.RELEASE_BUNDLE) })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clean_project)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                enabled = !state.androidBuildRunning,
                                onClick = { showBuildMenu = false; onAction(AndroidAction.CLEAN) },
                            )
                            if (state.androidApkInfo != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.install)) },
                                    leadingIcon = { Icon(Icons.Default.InstallMobile, null) },
                                    enabled = !state.androidBuildRunning,
                                    onClick = { showBuildMenu = false; onAction(AndroidAction.INSTALL) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share)) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { showBuildMenu = false; onShareApk() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.save_copy)) },
                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                    onClick = { showBuildMenu = false; onSaveApk() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_path)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = {
                                        state.androidApkInfo?.path?.let { clipboard.setText(AnnotatedString(it)) }
                                        showBuildMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.app_info)) },
                                    leadingIcon = { Icon(Icons.Default.Info, null) },
                                    enabled = state.androidApkInfo?.installed == true,
                                    onClick = { showBuildMenu = false; onOpenAppDetails() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.uninstall)) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    enabled = state.androidApkInfo?.installed == true,
                                    onClick = { showBuildMenu = false; onUninstallApp() },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = if (section == AndroidDashboardSection.HEALTH) 1 else 0) {
                Tab(
                    selected = section != AndroidDashboardSection.HEALTH,
                    onClick = { section = AndroidDashboardSection.OVERVIEW },
                    text = { Text(stringResource(R.string.android_build_tab)) },
                )
                Tab(
                    selected = section == AndroidDashboardSection.HEALTH,
                    onClick = { section = AndroidDashboardSection.HEALTH },
                    text = { Text(stringResource(R.string.android_health_tab)) },
                )
            }

            when (section) {
                AndroidDashboardSection.OVERVIEW -> ProfessionalAndroidOverview(
                    state = state,
                    liveElapsedSeconds = liveElapsedSeconds,
                    showRawLog = showRawLog,
                    onToggleRawLog = { showRawLog = !showRawLog },
                    onAction = onAction,
                    onCancelBuild = onCancelBuild,
                    onInstallTools = onInstallTools,
                    onOpenIssue = onOpenIssue,
                )
                AndroidDashboardSection.BUILD -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Android, null, tint = PocketGreen)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.build_progress), fontWeight = FontWeight.SemiBold)
                                            Text(androidStageLabel(state.androidBuildStage), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            liveElapsedSeconds?.let { live ->
                                                val seconds = if (state.androidBuildRunning) live.toLong() else {
                                                    ((state.androidBuildFinishedAtMillis ?: System.currentTimeMillis()) -
                                                        (state.androidBuildStartedAtMillis ?: System.currentTimeMillis())) / 1_000L
                                                }.coerceAtLeast(0L)
                                                Text(formatDuration(seconds), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (state.androidBuildRunning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                    if (state.androidBuildRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            AndroidBuildStage.PREPARING, AndroidBuildStage.RESOLVING, AndroidBuildStage.COMPILING,
                                            AndroidBuildStage.RESOURCES, AndroidBuildStage.PACKAGING, AndroidBuildStage.INSTALLING,
                                            AndroidBuildStage.LAUNCHING, AndroidBuildStage.COMPLETE,
                                        ).forEach { stage ->
                                            val active = stage == state.androidBuildStage
                                            Surface(
                                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(50),
                                            ) { Text(androidStageLabel(stage), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp) }
                                        }
                                    }
                                }
                            }
                        }
                        if (DevStack.ANDROID !in state.installedDevStacks) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.android_tools_required), color = MaterialTheme.colorScheme.onErrorContainer)
                                        Button(onClick = onInstallTools) { Text(stringResource(R.string.install_android_tools)) }
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onAction(AndroidAction.BUILD) },
                                        enabled = !state.androidBuildRunning && DevStack.ANDROID in state.installedDevStacks,
                                        modifier = Modifier.weight(1f),
                                    ) { Icon(Icons.Default.Build, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build)) }
                                    OutlinedButton(
                                        onClick = { onAction(AndroidAction.INSTALL) }, enabled = !state.androidBuildRunning && state.androidApkInfo != null,
                                        modifier = Modifier.weight(1f),
                                    ) { Icon(Icons.Default.InstallMobile, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.install)) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onAction(AndroidAction.RUN) }, enabled = !state.androidBuildRunning && state.androidApkInfo != null,
                                        modifier = Modifier.weight(1f),
                                    ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.run)) }
                                    Button(
                                        onClick = { onAction(AndroidAction.BUILD_AND_RUN) },
                                        enabled = !state.androidBuildRunning && DevStack.ANDROID in state.installedDevStacks,
                                        modifier = Modifier.weight(1f),
                                    ) { Icon(Icons.Default.Launch, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.build_and_run)) }
                                }
                                if (state.androidBuildRunning) {
                                    OutlinedButton(onClick = onCancelBuild, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cancel_operation))
                                    }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.nova_release_artifact), fontWeight = FontWeight.SemiBold)
                                    OutlinedButton(enabled = !state.androidBuildRunning, onClick = { onAction(AndroidAction.RELEASE_APK) },
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nova_build_release_apk)) }
                                    OutlinedButton(enabled = !state.androidBuildRunning, onClick = { onAction(AndroidAction.RELEASE_BUNDLE) },
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nova_build_bundle)) }
                                }
                            }
                        }
                        if (state.androidBuildIssues.isNotEmpty()) {
                            item { Text(stringResource(R.string.build_issues), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            items(state.androidBuildIssues) { rawIssue ->
                                val issue = rawIssue.localized(LocalContext.current)
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = issue.filePath != null) { onOpenIssue(issue) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(issue.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                        Text(issue.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        issue.suggestion?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)) }
                                        if (issue.filePath != null) Text("${issue.filePath}${issue.line?.let { ":$it" }.orEmpty()}", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        if (state.androidBuildPhase == AndroidBuildPhase.SUCCEEDED && !state.androidBuildRunning &&
                            state.androidBuildAction in setOf(AndroidAction.RELEASE_APK, AndroidAction.RELEASE_BUNDLE)) {
                            state.androidBuildApkPath?.let { path ->
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(stringResource(R.string.nova_release_artifact), fontWeight = FontWeight.Bold)
                                            Text(path.substringAfterLast('/'), fontFamily = FontFamily.Monospace)
                                            Text(formatFileSize(state.androidBuildApkSizeBytes ?: 0L))
                                            Text(stringResource(R.string.nova_release_signing_detail), fontSize = 12.sp)
                                            val artifact = state.releaseArtifactInfo
                                            if (artifact != null && artifact.path == path) {
                                                TextButton(onClick = { clipboard.setText(AnnotatedString(artifact.sha256)) }) {
                                                    Text("SHA-256 · ${artifact.sha256.take(12)}…")
                                                }
                                                Text(artifact.certificateSha256?.let {
                                                    stringResource(R.string.nova_release_cert, it.take(16) + "…")
                                                } ?: stringResource(R.string.nova_release_cert_unknown), fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = onSaveRelease, modifier = Modifier.fillMaxWidth()) {
                                                Text(stringResource(R.string.save_copy))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.androidApkInfo?.let { apk ->
                            item {
                                Card {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Android, null, tint = PocketGreen)
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(apk.fileName, fontWeight = FontWeight.Bold)
                                                Text("${formatFileSize(apk.sizeBytes)} · ${apk.packageName ?: stringResource(R.string.unknown_package)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(if (apk.installedMatches) stringResource(R.string.installed) else stringResource(R.string.not_installed), fontSize = 10.sp)
                                        }
                                        if (apk.sha256.isNotBlank()) {
                                            TextButton(onClick = { clipboard.setText(AnnotatedString(apk.sha256)) }) {
                                                Icon(Icons.Default.ContentCopy, null)
                                                Text("SHA-256 · ${apk.sha256.take(12)}…")
                                            }
                                        }
                                        if (apk.stale) {
                                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                                                Text(stringResource(R.string.stale_apk_warning), Modifier.fillMaxWidth().padding(9.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                        Text(
                                            stringResource(R.string.apk_version_info, apk.versionName ?: "—", apk.versionCode?.toString() ?: "—", apk.minSdk?.toString() ?: "—", apk.targetSdk?.toString() ?: "—"),
                                            fontSize = 11.sp,
                                        )
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = onShareApk) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.share)) }
                                            TextButton(onClick = onSaveApk) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.save_copy)) }
                                            TextButton(onClick = { clipboard.setText(AnnotatedString(apk.path)) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.copy_path)) }
                                            TextButton(onClick = onOpenAppDetails, enabled = apk.installed) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.app_info)) }
                                            TextButton(onClick = onUninstallApp, enabled = apk.installed) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.uninstall)) }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.androidBuildLog.isNotBlank()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { showRawLog = !showRawLog }, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(if (showRawLog) R.string.hide_build_details else R.string.show_build_details))
                                    }
                                    TextButton(onClick = { clipboard.setText(AnnotatedString(state.androidBuildLog)) }) {
                                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.copy_log))
                                    }
                                }
                                if (showRawLog) Surface(color = Color(0xFF11141A), shape = RoundedCornerShape(12.dp)) {
                                    SelectionContainer {
                                        Text(
                                            state.androidBuildLog,
                                            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                                            color = Color(0xFFD7DEE9), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                AndroidDashboardSection.LOGCAT -> {
                    val logcat = state.androidLogcat
                    val filtered = remember(logcat.lines, logcat.minimumLevel, logcat.query) {
                        logcat.lines.filter { line ->
                            line.level.ordinal >= logcat.minimumLevel.ordinal &&
                                (logcat.query.isBlank() || line.text.contains(logcat.query, ignoreCase = true))
                        }
                    }
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!logcat.available) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.logcat_access_required), fontWeight = FontWeight.Bold)
                                    Text(logcat.message ?: stringResource(R.string.logcat_access_description), fontSize = 12.sp)
                                    val command = MainViewModel.LOGCAT_GRANT_COMMAND
                                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
                                        Text(command, Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(command)) }.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }
                                    Button(onClick = { clipboard.setText(AnnotatedString(command)) }) { Text(stringResource(R.string.copy_command)) }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = logcat.query, onValueChange = onLogcatQuery,
                                    modifier = Modifier.weight(1f), singleLine = true,
                                    label = { Text(stringResource(R.string.search_logs)) },
                                )
                                IconButton(onClick = onToggleLogcatPause) { Icon(if (logcat.paused) Icons.Default.PlayArrow else Icons.Default.Pause, stringResource(if (logcat.paused) R.string.resume else R.string.pause)) }
                                IconButton(onClick = { clipboard.setText(AnnotatedString(filtered.joinToString("\n") { it.text })) }) {
                                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_log))
                                }
                                IconButton(onClick = onClearLogcat) { Icon(Icons.Default.ClearAll, stringResource(R.string.clear)) }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AndroidLogLevel.entries.forEach { level ->
                                    FilterChip(selected = logcat.minimumLevel == level, onClick = { onLogcatLevel(level) }, label = { Text(level.name.take(1)) })
                                }
                            }
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(filtered, key = { it.id }) { line ->
                                    Text(
                                        line.text,
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = line.filePath != null) { onOpenLogLine(line) }.padding(vertical = 2.dp),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = when (line.level) {
                                            AndroidLogLevel.ERROR, AndroidLogLevel.FATAL -> MaterialTheme.colorScheme.error
                                            AndroidLogLevel.WARNING -> PocketOrange
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                AndroidDashboardSection.HEALTH -> {
                    LazyColumn(
                        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.project_health), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = onRefreshHealth, enabled = !state.androidHealthRunning) {
                                    if (state.androidHealthRunning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                                }
                            }
                        }
                        items(state.androidHealthChecks, key = { it.id }) { check ->
                            AndroidHealthCheckCard(check, clipboard, onHealthFix)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfessionalAndroidOverview(
    state: AppUiState,
    liveElapsedSeconds: Int?,
    showRawLog: Boolean,
    onToggleRawLog: () -> Unit,
    onAction: (AndroidAction) -> Unit,
    onCancelBuild: () -> Unit,
    onInstallTools: () -> Unit,
    onOpenIssue: (AndroidBuildIssue) -> Unit,
) {
    val apk = state.androidApkInfo
    val toolsInstalled = DevStack.ANDROID in state.installedDevStacks
    val primaryAction = when {
        apk == null -> AndroidAction.BUILD
        apk.stale -> AndroidAction.BUILD_AND_RUN
        apk.installedMatches -> AndroidAction.RUN
        else -> AndroidAction.INSTALL
    }
    val primaryLabel = when (primaryAction) {
        AndroidAction.BUILD -> R.string.build
        AndroidAction.INSTALL -> R.string.install
        AndroidAction.RUN -> R.string.run_app
        else -> R.string.build_and_run
    }
    val statusTitle = when {
        state.androidBuildRunning -> androidStageLabel(state.androidBuildStage)
        state.androidBuildStage == AndroidBuildStage.FAILED -> stringResource(R.string.android_build_failed_title)
        state.androidBuildPhase == AndroidBuildPhase.SUCCEEDED && state.androidBuildAction in setOf(AndroidAction.RELEASE_APK, AndroidAction.RELEASE_BUNDLE) ->
            stringResource(R.string.nova_release_built)
        apk == null -> stringResource(R.string.android_ready_to_build)
        apk.stale -> stringResource(R.string.android_ready_to_rebuild)
        apk.installedMatches -> stringResource(R.string.android_ready_to_run)
        else -> stringResource(R.string.android_ready_to_install)
    }
    val statusDescription = when {
        state.androidBuildRunning -> state.androidBuildMessage ?: androidStageLabel(state.androidBuildStage)
        apk == null -> stringResource(R.string.android_no_apk_description)
        apk.stale -> stringResource(R.string.android_source_changed_short)
        apk.installedMatches -> stringResource(R.string.android_build_current_installed)
        else -> stringResource(R.string.android_build_current_not_installed)
    }
    val elapsedSeconds = state.androidBuildStartedAtMillis?.let { start ->
        if (state.androidBuildRunning) liveElapsedSeconds?.toLong()
        else ((state.androidBuildFinishedAtMillis ?: start) - start).coerceAtLeast(0L) / 1_000L
    }
    val expectedBuildSeconds = state.androidBuildRecentDurationsMillis
        .sorted()
        .takeIf { it.isNotEmpty() }
        ?.let { durations -> durations[durations.size / 2] / 1_000L }
        ?.coerceAtLeast(1L)
    val estimateText = when {
        state.androidBuildRunning && expectedBuildSeconds != null -> {
            val remaining = (expectedBuildSeconds - (elapsedSeconds ?: 0L)).coerceAtLeast(0L)
            if (remaining > 0L) stringResource(R.string.android_estimated_remaining, formatDuration(remaining))
            else stringResource(R.string.android_finishing_build)
        }
        state.androidBuildRunning -> stringResource(R.string.android_estimating_build)
        primaryAction in setOf(AndroidAction.BUILD, AndroidAction.BUILD_AND_RUN) && expectedBuildSeconds != null ->
            stringResource(R.string.android_usually_takes, formatDuration(expectedBuildSeconds))
        else -> null
    }
    val buildSummary = when {
        state.androidBuildRunning -> stringResource(R.string.android_build_in_progress, formatDuration(elapsedSeconds ?: 0L))
        state.androidBuildStage == AndroidBuildStage.FAILED -> state.androidBuildMessage ?: stringResource(R.string.android_build_failed_title)
        state.androidBuildFinishedAtMillis != null -> stringResource(R.string.android_completed_in, formatDuration(elapsedSeconds ?: 0L))
        else -> stringResource(R.string.android_no_build_yet)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = when {
                                state.androidBuildStage == AndroidBuildStage.FAILED -> MaterialTheme.colorScheme.errorContainer
                                apk?.stale == true -> PocketOrange.copy(alpha = 0.18f)
                                else -> PocketGreen.copy(alpha = 0.18f)
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.androidBuildRunning) {
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                                } else {
                                    Icon(
                                        if (state.androidBuildStage == AndroidBuildStage.FAILED || apk?.stale == true) Icons.Default.Warning else Icons.Default.Check,
                                        null,
                                        tint = if (state.androidBuildStage == AndroidBuildStage.FAILED) MaterialTheme.colorScheme.error else if (apk?.stale == true) PocketOrange else PocketGreen,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(statusTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(statusDescription, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            apk?.let {
                                Text(
                                    "${it.fileName} · ${formatFileSize(it.sizeBytes)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    fontSize = 11.sp,
                                )
                            }
                            estimateText?.let {
                                Text(it, color = PocketOrange, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (state.androidBuildRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!toolsInstalled) {
                        Button(onClick = onInstallTools, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Text(stringResource(R.string.install_android_tools))
                        }
                    } else if (state.androidBuildRunning) {
                        OutlinedButton(onClick = onCancelBuild, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Icon(Icons.Default.Stop, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cancel_operation))
                        }
                    } else {
                        Button(
                            onClick = { onAction(primaryAction) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(primaryLabel), fontWeight = FontWeight.SemiBold)
                        }
                        if (apk != null) {
                            TextButton(onClick = { onAction(AndroidAction.FORCE_BUILD) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text(stringResource(R.string.android_build_again))
                            }
                        }
                    }
                }
            }
        }

        if (state.androidBuildIssues.isNotEmpty()) {
            item { Text(stringResource(R.string.build_issues), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(state.androidBuildIssues) { rawIssue ->
                val issue = rawIssue.localized(LocalContext.current)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = issue.filePath != null) { onOpenIssue(issue) },
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(issue.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(issue.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        issue.suggestion?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)) }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                AndroidDashboardSummaryRow(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.android_build_output),
                    subtitle = buildSummary,
                    trailing = stringResource(R.string.android_view),
                    tint = PocketOrange,
                    onClick = onToggleRawLog,
                )
            }
        }

        if (showRawLog) {
            item {
                Surface(color = Color(0xFF11141A), shape = RoundedCornerShape(16.dp)) {
                    SelectionContainer {
                        Text(
                            state.androidBuildLog.ifBlank { stringResource(R.string.android_no_build_output) },
                            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                            color = Color(0xFFD7DEE9),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidDashboardSummaryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        trailing?.let {
            Text(it, color = PocketOrange, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AndroidHealthCheckCard(
    check: AndroidHealthCheck,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onFix: (AndroidHealthFix) -> Unit,
) {
    val tint = when (check.status) {
        AndroidHealthStatus.PASSED -> PocketGreen
        AndroidHealthStatus.WARNING -> PocketOrange
        AndroidHealthStatus.FAILED -> MaterialTheme.colorScheme.error
        AndroidHealthStatus.RUNNING -> MaterialTheme.colorScheme.primary
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (check.status == AndroidHealthStatus.PASSED) Icons.Default.Check else Icons.Default.Warning, null, tint = tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(check.title, fontWeight = FontWeight.SemiBold)
                Text(check.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (check.fix != AndroidHealthFix.NONE) {
                TextButton(onClick = {
                    if (check.fix == AndroidHealthFix.COPY_LOGCAT_COMMAND) clipboard.setText(AnnotatedString(MainViewModel.LOGCAT_GRANT_COMMAND))
                    else onFix(check.fix)
                }) { Text(stringResource(if (check.fix == AndroidHealthFix.COPY_LOGCAT_COMMAND) R.string.copy_command else R.string.fix)) }
            }
        }
    }
}

