package com.jarves.mh.ui

import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.Project
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProjectType
import com.jarves.mh.model.AndroidTemplate
import com.jarves.mh.model.projectSlug
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jarves.mh.network.GitHubRepository
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.jarves.mh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectsScreen(
    state: AppUiState,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    onOpen: (Project) -> Unit,
    onCreate: (String) -> Unit,
    onCreateAndroid: (String, AndroidTemplate) -> Unit,
    onCreateQuickProject: () -> Unit,
    onImportZip: (Uri) -> Unit,
    onCloneGit: (String) -> Unit,
    onStartGitHubLogin: () -> Unit,
    onGenerateNewGitHubCode: () -> Unit,
    onRefreshGitHub: () -> Unit,
    onDisconnectGitHub: () -> Unit,
    onCloneGitHub: (GitHubRepository) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onSettings: () -> Unit,
    onPing: () -> Unit,
    onToggleTheme: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var showGitDialog by rememberSaveable { mutableStateOf(false) }
    var showGitHubDialog by rememberSaveable { mutableStateOf(false) }
    var importExpanded by rememberSaveable { mutableStateOf(false) }
    var gitUrl by rememberSaveable { mutableStateOf("") }
    var repositorySearch by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var androidTemplate by rememberSaveable { mutableStateOf<AndroidTemplate?>(null) }
    var projectSearch by rememberSaveable { mutableStateOf("") }
    var androidOnly by rememberSaveable { mutableStateOf(false) }
    val projects = remember(state.projects, projectSearch, androidOnly) {
        state.projects.filter { project ->
            (!androidOnly || project.type == ProjectType.ANDROID) &&
                (projectSearch.isBlank() || listOf(project.name, project.language, project.description).any { it.contains(projectSearch.trim(), true) })
        }.sortedByDescending { it.updatedAtMillis }
    }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onInstallUpdate()
    }
    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportZip(uri)
    }
    LaunchedEffect(state.appUpdate?.versionCode) {
        if (state.appUpdate != null) showUpdateDialog = true
    }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(top = 8.dp),
                title = { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(compact = true); Spacer(Modifier.width(9.dp)); Text("Harnes Plus", fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.projects_hero_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.projects_hero_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.plus_workspace_status), fontWeight = FontWeight.SemiBold)
                        Text(state.agentKind.localizedTitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        ApiStatusChip(state, onSettings, onPing)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onCreateQuickProject,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.projects_quick_project),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    OutlinedButton(
                        onClick = { showCreate = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.projects_new_project),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val isImportExpanded = importExpanded || state.projectImporting || state.gitCloneRunning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { importExpanded = !importExpanded }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.projects_bring_existing), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(if (isImportExpanded) R.string.projects_import_expanded else R.string.projects_import_sources),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = if (isImportExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isImportExpanded) stringResource(R.string.ws_collapse) else stringResource(R.string.ws_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        AnimatedVisibility(visible = isImportExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    ImportSourceButton(
                                        icon = Icons.Default.Download,
                                        title = stringResource(if (state.projectImporting) R.string.projects_importing else R.string.projects_zip_file),
                                        enabled = !state.projectImporting && !state.gitCloneRunning,
                                        modifier = Modifier.weight(1f),
                                        onClick = { importZipLauncher.launch("*/*") },
                                        loading = state.projectImporting,
                                    )
                                    ImportSourceButton(
                                        icon = Icons.Default.Code,
                                        title = stringResource(if (state.gitCloneRunning) R.string.projects_cloning else R.string.projects_git_url),
                                        enabled = !state.projectImporting && !state.gitCloneRunning,
                                        modifier = Modifier.weight(1f),
                                        onClick = { showGitDialog = true },
                                        loading = state.gitCloneRunning,
                                    )
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.gitCloneRunning) {
                                        showGitHubDialog = true
                                        if (state.githubAuthStatus == GitHubAuthStatus.CONNECTED && state.githubRepositories.isEmpty()) onRefreshGitHub()
                                    },
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(13.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Code, null, tint = PocketOrange, modifier = Modifier.size(19.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                state.githubLogin?.let { stringResource(R.string.projects_github_account, it) } ?: stringResource(R.string.projects_connect_github),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                stringResource(if (state.githubLogin != null) R.string.projects_browse_repositories else R.string.projects_sign_in_repositories),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                (state.projectImportMessage ?: state.gitCloneMessage)?.let { message ->
                                    Text(message, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            state.appUpdate?.let { update ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { showUpdateDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = PocketOrange.copy(alpha = 0.11f),
                        border = BorderStroke(1.dp, PocketOrange.copy(alpha = 0.45f)),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = PocketOrange.copy(alpha = 0.18f), modifier = Modifier.size(46.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = PocketOrange) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Harnes Plus ${update.versionName}", fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.ws_update_ready), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.ws_update), color = PocketOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.projects_your_projects), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (state.projects.isNotEmpty()) {
                item {
                    OutlinedTextField(value = projectSearch, onValueChange = { projectSearch = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.nova_project_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !androidOnly, onClick = { androidOnly = false },
                            label = { Text(stringResource(R.string.nova_all_projects)) })
                        FilterChip(selected = androidOnly, onClick = { androidOnly = true }, label = { Text("Android") })
                        Text(stringResource(R.string.nova_project_count, projects.size), fontSize = 12.sp)
                    }
                }
            }
            if (projects.isEmpty() && state.projects.isNotEmpty()) {
                item { Text(stringResource(R.string.nova_project_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (projects.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PocketOrange.copy(alpha = 0.15f),
                                modifier = Modifier.size(56.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = PocketOrange,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.projects_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.projects_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        taskRunning = state.isRunning && state.activeProject?.id == project.id,
                        terminalRunning = state.projectTerminalRunning && state.activeProject?.id == project.id,
                        buildRunning = state.androidBuildRunning && state.activeProject?.id == project.id,
                        onOpen = { onOpen(project) },
                        onRename = { onRenameProject(project.id, it) },
                        onDelete = { onDeleteProject(project.id) },
                    )
                }
            }
        }
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text(stringResource(R.string.an_create_project)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ws_project_name)) }, singleLine = true)
                Text(stringResource(R.string.project_type), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = { androidTemplate = null },
                        label = { Text(stringResource(R.string.project_type_general)) },
                        leadingIcon = { if (androidTemplate == null) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = { androidTemplate = AndroidTemplate.COMPOSE },
                        label = { Text(stringResource(R.string.project_type_compose)) },
                        leadingIcon = { if (androidTemplate == AndroidTemplate.COMPOSE) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = { androidTemplate = AndroidTemplate.XML },
                        label = { Text(stringResource(R.string.project_type_xml)) },
                        leadingIcon = { if (androidTemplate == AndroidTemplate.XML) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) },
                    )
                }
                Text(
                    stringResource(
                        when (androidTemplate) {
                            AndroidTemplate.COMPOSE -> R.string.project_type_compose_desc
                            AndroidTemplate.XML -> R.string.project_type_xml_desc
                            null -> R.string.project_type_general_desc
                        },
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (androidTemplate != null) {
                    Text(
                        stringResource(R.string.an_template_info, 28, 36),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (name.isNotBlank()) {
                    Text(
                        stringResource(R.string.ws_terminal_folder, "/workspace/${projectSlug(name)}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    androidTemplate?.let { onCreateAndroid(name, it) } ?: onCreate(name)
                    showCreate = false
                    name = ""
                    androidTemplate = null
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.ws_create)) }
        },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (showGitDialog) AlertDialog(
        onDismissRequest = { if (!state.gitCloneRunning) showGitDialog = false },
        icon = { Icon(Icons.Default.Code, null, tint = PocketOrange) },
        title = { Text(stringResource(R.string.ws_clone_git_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ws_clone_git_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                OutlinedTextField(
                    value = gitUrl,
                    onValueChange = { gitUrl = it },
                    label = { Text(stringResource(R.string.ws_https_git_url)) },
                    placeholder = { Text("https://github.com/owner/repository.git") },
                    singleLine = true,
                )
                state.gitCloneMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = gitUrl.isNotBlank() && !state.gitCloneRunning,
                onClick = { onCloneGit(gitUrl); showGitDialog = false; gitUrl = "" },
            ) { Text(if (state.gitCloneRunning) stringResource(R.string.projects_cloning) else stringResource(R.string.ws_clone_project)) }
        },
        dismissButton = { TextButton(onClick = { showGitDialog = false }, enabled = !state.gitCloneRunning) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (showGitHubDialog) {
        val clipboard = LocalClipboardManager.current
        val filteredRepositories = state.githubRepositories.filter { repository ->
            repositorySearch.isBlank() || repository.fullName.contains(repositorySearch, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { if (!state.gitCloneRunning) showGitHubDialog = false },
            icon = { Icon(Icons.Default.Code, null, tint = PocketOrange) },
            title = { Text(state.githubLogin?.let { stringResource(R.string.projects_github_account, it) } ?: stringResource(R.string.projects_connect_github)) },
            text = {
                when (state.githubAuthStatus) {
                    GitHubAuthStatus.DISCONNECTED, GitHubAuthStatus.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            state.githubMessage ?: stringResource(R.string.ws_github_signin_desc),
                            color = if (state.githubAuthStatus == GitHubAuthStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Button(onClick = onStartGitHubLogin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ws_github_signin)) }
                    }
                    GitHubAuthStatus.STARTING -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(state.githubMessage ?: stringResource(R.string.ws_github_starting))
                    }
                    GitHubAuthStatus.AWAITING_USER -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ws_github_enter_code), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { state.githubUserCode?.let { clipboard.setText(AnnotatedString(it)) } },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                state.githubUserCode.orEmpty(),
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                letterSpacing = 2.sp,
                            )
                        }
                        Text(stringResource(R.string.ws_github_tap_code), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = onGenerateNewGitHubCode,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ws_github_new_code))
                        }
                    }
                    GitHubAuthStatus.CONNECTED -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.githubMessage ?: stringResource(R.string.ws_github_select_repo), modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = onRefreshGitHub, enabled = !state.githubRepositoriesLoading) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.ws_github_refresh_repos))
                            }
                        }
                        OutlinedTextField(
                            value = repositorySearch,
                            onValueChange = { repositorySearch = it },
                            placeholder = { Text(stringResource(R.string.ws_github_search_repos)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.githubRepositoriesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(filteredRepositories, key = { it.fullName }) { repository ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.gitCloneRunning) {
                                        showGitHubDialog = false
                                        onCloneGitHub(repository)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                ) {
                                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (repository.private) Icons.Default.Key else Icons.Default.Code, null, modifier = Modifier.size(17.dp), tint = PocketOrange)
                                        Spacer(Modifier.width(9.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(repository.fullName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${if (repository.private) stringResource(R.string.ws_repo_private) else stringResource(R.string.ws_repo_public)} · ${repository.defaultBranch}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (state.githubAuthStatus == GitHubAuthStatus.CONNECTED) {
                    TextButton(onClick = { showGitHubDialog = false }) { Text(stringResource(R.string.action_close)) }
                }
            },
            dismissButton = {
                if (state.githubAuthStatus == GitHubAuthStatus.CONNECTED) {
                    TextButton(onClick = { onDisconnectGitHub(); showGitHubDialog = false }) { Text(stringResource(R.string.ws_disconnect)) }
                } else if (state.githubAuthStatus != GitHubAuthStatus.STARTING) {
                    TextButton(onClick = { showGitHubDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
    val update = state.appUpdate
    if (showUpdateDialog && update != null) {
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
        val downloading = state.appUpdateStatus == AppUpdateStatus.DOWNLOADING
        val installing = state.appUpdateStatus == AppUpdateStatus.INSTALLING
        val total = state.appUpdateTotalBytes
        val downloaded = state.appUpdateDownloadedBytes
        val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
        AlertDialog(
            onDismissRequest = { if (!installing) showUpdateDialog = false },
            icon = { Icon(Icons.Default.Download, null, tint = PocketOrange, modifier = Modifier.size(34.dp)) },
            title = { Text(stringResource(R.string.ws_update_to, update.versionName), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(update.notes.ifBlank { stringResource(R.string.ws_update_default_notes) })
                    if (update.sizeBytes > 0) Text(stringResource(R.string.ws_update_download_size, formatMegabytes(update.sizeBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (!canInstall) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ws_update_allow_unknown_apps), fontSize = 13.sp)
                            }
                        }
                    }
                    if (downloading) {
                        if (total > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            if (total > 0) stringResource(R.string.ws_update_downloading_progress, formatMegabytes(downloaded), formatMegabytes(total), (progress * 100).toInt()) else stringResource(R.string.ws_update_downloading, formatMegabytes(downloaded)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (installing) Text(stringResource(R.string.ws_update_verified_installing), color = PocketGreen, fontSize = 13.sp)
                    state.appUpdateError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !downloading && !installing,
                    onClick = {
                        if (!canInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            permissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                            )
                        } else {
                            onInstallUpdate()
                        }
                    },
                ) {
                    Text(when { !canInstall -> stringResource(R.string.ws_grant_permission); downloading -> stringResource(R.string.ws_downloading_ellipsis); installing -> stringResource(R.string.tools_installing); else -> stringResource(R.string.ws_download_and_install) })
                }
            },
            dismissButton = { if (!installing) TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.ws_later)) } },
        )
    }
}

@Composable
private fun ImportSourceButton(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ApiStatusChip(state: AppUiState, onSettings: () -> Unit, onPing: () -> Unit) {
    val dotColor = when (state.apiPingStatus) {
        ApiPingStatus.OK -> PocketGreen
        ApiPingStatus.FAILED -> MaterialTheme.colorScheme.error
        ApiPingStatus.PINGING -> PocketOrange
        ApiPingStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val agentTitle = state.agentKind.localizedTitle()
    val providerTitle = state.provider.kind.localizedTitle()
    val providerLabel = when {
        state.agentKind == AgentKind.ANTIGRAVITY ->
            state.antigravityModel.ifBlank { agentTitle }
        state.agentKind == AgentKind.CODEX -> agentTitle
        state.provider.model.isNotBlank() -> state.provider.model
        state.provider.baseUrl.isNotBlank() -> {
            runCatching { java.net.URI(state.provider.baseUrl).host ?: providerTitle }
                .getOrDefault(providerTitle)
        }
        else -> providerTitle
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onSettings).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            // Model / provider name
            Text(
                text = providerLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            // Ping button
            IconButton(
                onClick = onPing,
                modifier = Modifier.size(28.dp),
            ) {
                if (state.apiPingStatus == ApiPingStatus.PINGING) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = PocketOrange)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.ws_ping_api),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    taskRunning: Boolean,
    terminalRunning: Boolean,
    buildRunning: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var showRename by rememberSaveable(project.id) { mutableStateOf(false) }
    var showDelete by rememberSaveable(project.id) { mutableStateOf(false) }
    var renameText by rememberSaveable(project.id) { mutableStateOf(project.name) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    if (project.type == ProjectType.ANDROID) Icons.Default.Android else Icons.Default.Folder,
                    null,
                    Modifier.padding(13.dp),
                    tint = if (project.type == ProjectType.ANDROID) PocketGreen else PocketOrange,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (taskRunning || terminalRunning || buildRunning) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                taskRunning -> stringResource(R.string.ws_task_running)
                                buildRunning -> stringResource(R.string.an_android_build)
                                else -> stringResource(R.string.ws_terminal_running)
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    if (project.kind == ProjectKind.QUICK_PROJECT) stringResource(R.string.projects_quick_project) else project.localizedDescription(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Text("/workspace/${project.slug}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("${project.language} · ${project.localizedUpdatedAt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ws_project_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ws_rename_project)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; renameText = project.name; showRename = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ws_delete_project)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        }
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.ws_rename_project)) },
            text = { OutlinedTextField(renameText, { renameText = it }, label = { Text(stringResource(R.string.ws_project_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(renameText); showRename = false }, enabled = renameText.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.ws_delete_project_title)) },
            text = { Text(stringResource(R.string.ws_delete_project_desc)) },
            confirmButton = { TextButton(onClick = { onDelete(); showDelete = false }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

