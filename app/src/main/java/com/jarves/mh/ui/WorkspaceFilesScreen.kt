package com.jarves.mh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.model.WorkspaceEntry
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jarves.mh.ui.theme.PocketOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.jarves.mh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTabStrip(tabs: List<String>, dirty: Set<String>, selected: String?, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { path ->
            FilterChip(selected = selected == path, onClick = { onOpen(path) },
                label = { Text(path.substringAfterLast('/') + if (path in dirty) " •" else "", maxLines = 1) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileViewerScreen(
    file: OpenedFileState,
    tabs: List<String>,
    dirtyTabs: Set<String>,
    onOpenTab: (String) -> Unit,
    onBrowseFiles: () -> Unit,
    editingAllowed: Boolean,
    onClose: () -> Unit,
    onBeginEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onDismissConflict: () -> Unit,
) {
    val fileName = file.path.substringAfterLast('/')
    val ext = fileName.substringAfterLast('.', "")
    val isMarkdown = ext == "md"
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showFind by remember(file.path) { mutableStateOf(false) }
    var replacement by remember(file.path) { mutableStateOf("") }
    var findQuery by remember { mutableStateOf("") }
    var editorValue by remember(file.path, file.editing) { mutableStateOf(TextFieldValue(file.draft)) }
    var undoHistory by remember(file.path, file.editing) { mutableStateOf(emptyList<TextFieldValue>()) }
    var redoHistory by remember(file.path, file.editing) { mutableStateOf(emptyList<TextFieldValue>()) }
    val matches = remember(editorValue.text, findQuery) {
        if (findQuery.isBlank()) emptyList() else buildList {
            var start = 0
            while (start <= editorValue.text.length - findQuery.length) {
                val index = editorValue.text.indexOf(findQuery, start, ignoreCase = true)
                if (index < 0) break
                add(index)
                start = index + findQuery.length.coerceAtLeast(1)
            }
        }
    }
    val currentMatch = matches.indexOfFirst { it >= editorValue.selection.start }.let { if (it < 0 && matches.isNotEmpty()) 0 else it }

    fun leaveEditor() {
        if (file.dirty) showUnsavedDialog = true else onCancelEdit()
    }

    BackHandler {
        if (file.editing) leaveEditor() else onClose()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.unsaved_changes_description)) },
            confirmButton = {
                TextButton(onClick = { showUnsavedDialog = false; onSave() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedDialog = false; onCancelEdit() }) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text(stringResource(R.string.keep_editing)) }
                }
            },
        )
    }

    if (file.conflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.file_changed_title)) },
            text = { Text(stringResource(R.string.file_changed_description)) },
            confirmButton = { TextButton(onClick = onReload) { Text(stringResource(R.string.reload)) } },
            dismissButton = { TextButton(onClick = onDismissConflict) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // Code palette follows the app theme (light or dark) instead of a fixed dark scheme.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val codeBackground = if (darkTheme) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface
    val codeText = if (darkTheme) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface
    val gutterText = if (darkTheme) Color(0xFF6E7681) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val toolbarBackground = if (darkTheme) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, fontWeight = FontWeight.SemiBold)
                        Text(file.path, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (file.editing) leaveEditor() else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_file))
                    }
                },
                actions = {
                    IconButton(onClick = onBrowseFiles, enabled = !file.saving) {
                        Icon(Icons.Default.Folder, stringResource(R.string.nova_browse_files))
                    }
                    if (file.editing) {
                        TextButton(onClick = ::leaveEditor, enabled = !file.saving) { Text(stringResource(R.string.cancel)) }
                        IconButton(onClick = onSave, enabled = file.dirty && !file.saving && editingAllowed) {
                            if (file.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Save, stringResource(R.string.save_file))
                        }
                    } else if (file.content != null) {
                        // Empty files (e.g. just created) can still be edited; there is just nothing to copy.
                        if (file.content.isNotEmpty()) {
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(file.content))
                                copied = true
                                scope.launch { delay(2000); copied = false }
                            }) {
                                Icon(
                                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    stringResource(R.string.copy_file_contents),
                                    tint = if (copied) PocketOrange else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (file.readOnlyReason == null) {
                            IconButton(onClick = onBeginEdit, enabled = editingAllowed) {
                                Icon(Icons.Default.Edit, stringResource(R.string.edit_file))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            if (tabs.isNotEmpty()) EditorTabStrip(tabs, dirtyTabs, file.path, onOpenTab)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                file.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PocketOrange)
                    }
                }
                file.content == null -> {
                    val reason = when (file.readOnlyReason) {
                        FileReadOnlyReason.BINARY -> R.string.file_binary_description
                        FileReadOnlyReason.INVALID_UTF8 -> R.string.file_invalid_utf8_description
                        FileReadOnlyReason.UNSAFE -> R.string.file_unsafe_description
                        else -> R.string.file_could_not_read
                    }
                    EmptyState(Icons.Default.Description, stringResource(R.string.no_content), stringResource(reason))
                }
                file.editing -> {
                    val vertical = rememberScrollState()
                    val horizontal = rememberScrollState()
                    Column(Modifier.fillMaxSize().background(codeBackground)) {
                        CompositionLocalProvider(LocalContentColor provides codeText) {
                        Row(
                            Modifier.fillMaxWidth().background(toolbarBackground).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    val previous = undoHistory.lastOrNull() ?: return@IconButton
                                    undoHistory = undoHistory.dropLast(1)
                                    redoHistory = (redoHistory + editorValue).takeLast(20)
                                    editorValue = previous
                                    onDraftChange(previous.text)
                                },
                                enabled = undoHistory.isNotEmpty(),
                            ) { Icon(Icons.Default.Undo, stringResource(R.string.undo)) }
                            IconButton(
                                onClick = {
                                    val next = redoHistory.lastOrNull() ?: return@IconButton
                                    redoHistory = redoHistory.dropLast(1)
                                    undoHistory = (undoHistory + editorValue).takeLast(20)
                                    editorValue = next
                                    onDraftChange(next.text)
                                },
                                enabled = redoHistory.isNotEmpty(),
                            ) { Icon(Icons.Default.Redo, stringResource(R.string.redo)) }
                            IconButton(onClick = { showFind = !showFind }) {
                                Icon(Icons.Default.Search, stringResource(R.string.find_in_file))
                            }
                            Text(
                                if (file.dirty) stringResource(R.string.unsaved) else stringResource(R.string.saved),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        if (showFind) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = findQuery,
                                    onValueChange = { findQuery = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.find)) },
                                )
                                Text(
                                    if (matches.isEmpty()) "0/0" else "${currentMatch + 1}/${matches.size}",
                                    Modifier.padding(horizontal = 8.dp),
                                    fontSize = 12.sp,
                                    color = codeText,
                                )
                                IconButton(onClick = {
                                    if (matches.isNotEmpty()) {
                                        val index = if (currentMatch <= 0) matches.lastIndex else currentMatch - 1
                                        editorValue = editorValue.copy(selection = TextRange(matches[index], matches[index] + findQuery.length))
                                    }
                                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous_match)) }
                                IconButton(onClick = {
                                    if (matches.isNotEmpty()) {
                                        val index = if (currentMatch >= matches.lastIndex) 0 else currentMatch + 1
                                        editorValue = editorValue.copy(selection = TextRange(matches[index], matches[index] + findQuery.length))
                                    }
                                }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next_match)) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(value = replacement, onValueChange = { replacement = it },
                                    label = { Text(stringResource(R.string.nova_replace_with)) },
                                    modifier = Modifier.weight(1f), singleLine = true)
                                TextButton(enabled = matches.isNotEmpty() && !file.saving && editingAllowed, onClick = {
                                    val next = editorValue.text.replace(findQuery, replacement, ignoreCase = true)
                                    undoHistory = (undoHistory + editorValue).takeLast(20)
                                    redoHistory = emptyList()
                                    editorValue = TextFieldValue(next)
                                    onDraftChange(next)
                                }) { Text(stringResource(R.string.nova_replace_all)) }
                            }
                        }
                        }
                        val completion = remember(editorValue.text, editorValue.selection) {
                            if (editorValue.selection.collapsed) localEditorCompletion(editorValue.text, editorValue.selection.start) else null
                        }
                        if (completion != null && editingAllowed && !file.saving) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                completion.words.forEach { word ->
                                    AssistChip(onClick = {
                                        val next = editorValue.text.replaceRange(completion.start, completion.end, word)
                                        undoHistory = (undoHistory + editorValue).takeLast(20)
                                        redoHistory = emptyList()
                                        editorValue = TextFieldValue(next, selection = TextRange(completion.start + word.length))
                                        onDraftChange(next)
                                    }, label = { Text(word, fontFamily = FontFamily.Monospace) })
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            Modifier.fillMaxSize().verticalScroll(vertical),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                (1..editorValue.text.count { it == '\n' } + 1).joinToString("\n"),
                                modifier = Modifier.width(48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = gutterText,
                                textAlign = TextAlign.End,
                            )
                            BasicTextField(
                                value = editorValue,
                                onValueChange = { next ->
                                    if (next.text != editorValue.text) {
                                        undoHistory = (undoHistory + editorValue).takeLast(20)
                                        redoHistory = emptyList()
                                    }
                                    editorValue = next
                                    onDraftChange(next.text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(horizontal)
                                    .widthIn(min = 720.dp)
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = codeText,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
                isMarkdown -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item { MarkdownText(markdown = file.content, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
                else -> {
                    // Code / plain-text viewer
                    val codeListState = rememberLazyListState()
                    val targetIndex = file.targetLine?.minus(1)?.coerceAtLeast(0)
                    LaunchedEffect(file.path, targetIndex) {
                        if (targetIndex != null) codeListState.scrollToItem(targetIndex)
                    }
                    LazyColumn(
                        state = codeListState,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .background(codeBackground),
                    ) {
                        val lines = file.content.lines()
                        items(lines.size) { idx ->
                            val isTargetLine = idx == targetIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isTargetLine) PocketOrange.copy(alpha = 0.18f) else Color.Transparent)
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    modifier = Modifier
                                        .width(42.dp)
                                        .padding(start = 8.dp, end = 6.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = gutterText,
                                    textAlign = TextAlign.End,
                                )
                                Text(
                                    text = lines[idx],
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = codeText,
                                )
                            }
                        }
                    }
                }
            }
            if (!file.editing && file.readOnlyReason == FileReadOnlyReason.TOO_LARGE) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.file_too_large_description), Modifier.padding(12.dp), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesTab(
    searchResults: com.jarves.mh.runtime.ProjectSearchResult?,
    searchingProject: Boolean,
    onSearchProjectText: (String) -> Unit,
    onClearProjectSearch: () -> Unit,
    onOpenProjectSearchHit: (String, Int) -> Unit,
    editorTabs: List<String>,
    dirtyTabs: Set<String>,
    onOpenEditorTab: (String) -> Unit,
    selectedRoot: String,
    onSelectRoot: (String?) -> Unit,
    onTerminalAtFolder: (String) -> Unit,
    files: List<WorkspaceEntry>,
    loading: Boolean,
    suggestedProjectRoot: String?,
    onRefresh: () -> Unit,
    onLoadDirectory: (String) -> Unit,
    onOpenFile: (WorkspaceEntry) -> Unit,
    onUseSuggestedProjectRoot: () -> Unit,
    selectionMode: Boolean,
    exportSelection: ExportSelection,
    exportRunning: Boolean,
    exportEnabled: Boolean,
    onPortableBackup: () -> Unit,
    onEnterSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onToggleSelection: (WorkspaceEntry) -> Unit,
    onSelectAll: () -> Unit,
    onDownloadSelection: () -> Unit,
    fileOpsEnabled: Boolean = false,
    onCreateEntry: (parentPath: String, name: String, isDirectory: Boolean) -> Unit = { _, _, _ -> },
    onRenameEntry: (path: String, newName: String) -> Unit = { _, _ -> },
    onDeleteEntry: (path: String) -> Unit = {},
    onUploadInto: (parentPath: String) -> Unit = {},
    onShareFile: (path: String) -> Unit = {},
    onSaveFileCopy: (path: String) -> Unit = {},
) {
    var expandedDirectories by rememberSaveable(selectedRoot) { mutableStateOf(emptyList<String>()) }
    var nameDialog by remember { mutableStateOf<FileNameDialog?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkspaceEntry?>(null) }
    var rootMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(files.map { it.path }) {
        val directories = files.asSequence().filter { it.isDirectory }.map { it.path }.toSet()
        expandedDirectories = expandedDirectories.filter { it in directories }
    }
    var fileQuery by rememberSaveable(selectedRoot) { mutableStateOf("") }
    val expandedSet = remember(expandedDirectories) { expandedDirectories.toSet() }
    val filteredFiles = remember(files, expandedSet, fileQuery, selectionMode) {
        files.filter { entry ->
            if (!selectionMode && fileQuery.isNotBlank()) return@filter entry.path.contains(fileQuery.trim(), ignoreCase = true)
            val segments = entry.path.split('/')
            segments.size == 1 || (1 until segments.size).all { depth ->
                segments.take(depth).joinToString("/") in expandedSet
            }
        }
    }
    val visibleFiles = filteredFiles
    fun selectionState(entry: WorkspaceEntry): ToggleableState {
        if (!entry.isDirectory) return if (exportSelection.includes(entry.path)) ToggleableState.On else ToggleableState.Off
        val descendants = files.filter { it.path == entry.path || it.path.startsWith("${entry.path}/") }
        val selected = descendants.count { exportSelection.includes(it.path) }
        return when {
            selected == 0 -> ToggleableState.Off
            selected == descendants.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
    }
    val hasSelection = files.any { !it.isDirectory && exportSelection.includes(it.path) } ||
        files.any { it.isDirectory && selectionState(it) != ToggleableState.Off }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (selectionMode) R.string.select_files else R.string.workspace_files),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (selectionMode) {
                        TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all), fontSize = 11.sp) }
                        TextButton(onClick = onCancelSelection, enabled = !exportRunning) { Text(stringResource(R.string.cancel), fontSize = 11.sp) }
                    } else if (expandedDirectories.isNotEmpty()) {
                        TextButton(onClick = { expandedDirectories = emptyList() }) {
                            Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.collapse_all), fontSize = 11.sp)
                        }
                    }
                    if (!selectionMode) {
                        Box {
                            IconButton(onClick = { rootMenuOpen = true }, enabled = fileOpsEnabled) {
                                Icon(Icons.Default.Add, stringResource(R.string.fm_new))
                            }
                            DropdownMenu(expanded = rootMenuOpen, onDismissRequest = { rootMenuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.nova_terminal_here)) },
                                    onClick = { rootMenuOpen = false; onTerminalAtFolder("") })
                                if (selectedRoot.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.nova_restore_workspace)) },
                                    onClick = { rootMenuOpen = false; onSelectRoot(null) })
                                FolderActionItems(
                                    onNewFile = { rootMenuOpen = false; nameDialog = FileNameDialog.Create("", isDirectory = false) },
                                    onNewFolder = { rootMenuOpen = false; nameDialog = FileNameDialog.Create("", isDirectory = true) },
                                    onUpload = { rootMenuOpen = false; onUploadInto("") },
                                )
                            }
                        }
                    }
                    if (!selectionMode && !loading && files.any { !it.isDirectory }) {
                        IconButton(onClick = onEnterSelection, enabled = exportEnabled) {
                            Icon(Icons.Default.Download, stringResource(R.string.export_project_zip))
                        }
                    }
                    if (loading) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.ws_refresh_files)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!selectionMode) {
            item(key = "portable-backup") {
                OutlinedButton(onClick = onPortableBackup, enabled = exportEnabled && !exportRunning,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nova_portable_backup)) }
                Text(stringResource(R.string.nova_backup_detail), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!selectionMode) {
            item(key = "project-content-search") {
                var query by rememberSaveable(selectedRoot) { mutableStateOf("") }
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.nova_project_content_search)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !searchingProject && query.isNotBlank(), onClick = { onSearchProjectText(query) }) {
                        Text(stringResource(R.string.nova_search_action))
                    }
                    if (searchingProject) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    if (searchResults != null) TextButton(onClick = onClearProjectSearch) { Text(stringResource(R.string.close)) }
                }
            }
            searchResults?.let { result ->
                item { Text(stringResource(R.string.nova_project_count_matches, result.hits.size) +
                    if (result.limited) " · " + stringResource(R.string.nova_search_limited) else "") }
                items(result.hits, key = { "hit:${it.path}:${it.line}" }) { hit ->
                    Surface(Modifier.fillMaxWidth().clickable { onOpenProjectSearchHit(hit.path, hit.line) },
                        color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${hit.path}:${hit.line}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(hit.preview, fontSize = 11.sp, maxLines = 2, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        if (!selectionMode && editorTabs.isNotEmpty()) {
            item(key = "editor-tabs") {
                EditorTabStrip(editorTabs, dirtyTabs, null, onOpenEditorTab)
            }
        }
        if (!selectionMode) {
            item(key = "loaded-file-search") {
                OutlinedTextField(value = fileQuery, onValueChange = { fileQuery = it },
                    label = { Text(stringResource(R.string.nova_search_loaded_files)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = { if (fileQuery.isNotEmpty()) TextButton(onClick = { fileQuery = "" }) { Text("×") } })
            }
            if (fileQuery.isNotBlank() && visibleFiles.isEmpty() && !loading) {
                item { Text(stringResource(R.string.nova_no_matches), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (suggestedProjectRoot != null) {
            item(key = "suggested-project-root") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ws_project_folder_detected), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.ws_use_project_root_desc, suggestedProjectRoot),
                            fontSize = 13.sp,
                        )
                        Button(onClick = onUseSuggestedProjectRoot, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ws_use_project_root, suggestedProjectRoot))
                        }
                    }
                }
            }
        }
        if (!loading && files.isEmpty()) {
            item { EmptyState(Icons.Default.Folder, stringResource(R.string.workspace_no_files), stringResource(R.string.workspace_no_files_description)) }
        }
        items(visibleFiles, key = { it.path }) { entry ->
            var entryMenuOpen by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClick = { if (!selectionMode && fileOpsEnabled) entryMenuOpen = true },
                    ) {
                        if (selectionMode) {
                            onToggleSelection(entry)
                        } else if (entry.isDirectory) {
                            if (entry.path !in expandedSet) onLoadDirectory(entry.path)
                            expandedDirectories = if (entry.path in expandedSet) {
                                expandedDirectories.filterNot { it == entry.path || it.startsWith("${entry.path}/") }
                            } else {
                                expandedDirectories + entry.path
                            }
                        } else {
                            onOpenFile(entry)
                        }
                    }
                    .padding(start = (entry.depth * 20).dp)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectionMode) {
                    if (entry.isDirectory) {
                        TriStateCheckbox(
                            state = selectionState(entry),
                            onClick = { onToggleSelection(entry) },
                        )
                    } else {
                        Checkbox(
                            checked = exportSelection.includes(entry.path),
                            onCheckedChange = { onToggleSelection(entry) },
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                if (entry.isDirectory) {
                    Icon(
                        if (entry.path in expandedSet) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        stringResource(if (entry.path in expandedSet) R.string.collapse_folder else R.string.expand_folder),
                        Modifier.size(28.dp).clickable {
                            if (entry.path !in expandedSet) onLoadDirectory(entry.path)
                            expandedDirectories = if (entry.path in expandedSet) {
                                expandedDirectories.filterNot { it == entry.path || it.startsWith("${entry.path}/") }
                            } else expandedDirectories + entry.path
                        }.padding(5.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Icon(
                    if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    null,
                    tint = if (entry.isDirectory) PocketOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(11.dp))
                Text(
                    if (entry.isDirectory) "${entry.name} (${entry.childCount?.toString() ?: "…"})" else entry.name,
                    Modifier.weight(1f),
                    color = if (!entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (!entry.isDirectory) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatFileSize(entry.sizeBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!selectionMode) {
                    Box {
                        IconButton(
                            onClick = { entryMenuOpen = true },
                            enabled = fileOpsEnabled,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                stringResource(R.string.fm_more_actions, entry.name),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = entryMenuOpen, onDismissRequest = { entryMenuOpen = false }) {
                            if (entry.isDirectory) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.nova_terminal_here)) },
                                    onClick = { entryMenuOpen = false; onTerminalAtFolder(entry.path) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.ws_use_project_root, entry.name)) },
                                    onClick = { entryMenuOpen = false; onSelectRoot(entry.path) })
                                FolderActionItems(
                                    onNewFile = { entryMenuOpen = false; nameDialog = FileNameDialog.Create(entry.path, isDirectory = false) },
                                    onNewFolder = { entryMenuOpen = false; nameDialog = FileNameDialog.Create(entry.path, isDirectory = true) },
                                    onUpload = { entryMenuOpen = false; onUploadInto(entry.path) },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.fm_share)) },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { entryMenuOpen = false; onShareFile(entry.path) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.fm_save_copy)) },
                                    leadingIcon = { Icon(Icons.Default.Download, null) },
                                    onClick = { entryMenuOpen = false; onSaveFileCopy(entry.path) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fm_rename)) },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = { entryMenuOpen = false; nameDialog = FileNameDialog.Rename(entry.path, entry.name) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fm_delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { entryMenuOpen = false; deleteTarget = entry },
                            )
                        }
                    }
                }
            }
            if (!entry.isDirectory) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(start = (entry.depth * 20 + 42).dp))
            }
        }
        if (selectionMode) {
            item(key = "download-selection") {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDownloadSelection,
                    enabled = hasSelection && !exportRunning && exportEnabled,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                ) {
                    if (exportRunning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Download, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (exportRunning) R.string.creating_zip else R.string.download_zip))
                }
            }
        }
    }

    nameDialog?.let { dialog ->
        var name by remember(dialog) {
            val initial = (dialog as? FileNameDialog.Rename)?.currentName.orEmpty()
            // Preselect the stem so typing replaces the name but keeps the extension.
            mutableStateOf(TextFieldValue(initial, TextRange(0, initial.substringBeforeLast('.', initial).length)))
        }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(dialog) { focusRequester.requestFocus() }
        val title = when (dialog) {
            is FileNameDialog.Create -> stringResource(if (dialog.isDirectory) R.string.fm_new_folder else R.string.fm_new_file)
            is FileNameDialog.Rename -> stringResource(R.string.fm_rename)
        }
        val confirm = {
            when (dialog) {
                is FileNameDialog.Create -> onCreateEntry(dialog.parentPath, name.text, dialog.isDirectory)
                is FileNameDialog.Rename -> onRenameEntry(dialog.path, name.text)
            }
            nameDialog = null
        }
        AlertDialog(
            onDismissRequest = { nameDialog = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val parent = when (dialog) {
                        is FileNameDialog.Create -> dialog.parentPath
                        is FileNameDialog.Rename -> dialog.path.substringBeforeLast('/', "")
                    }
                    Text(
                        "/" + parent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.fm_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (name.text.isNotBlank()) confirm() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = confirm, enabled = name.text.isNotBlank()) {
                    Text(stringResource(if (dialog is FileNameDialog.Rename) R.string.fm_rename else R.string.fm_create))
                }
            },
            dismissButton = { TextButton(onClick = { nameDialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.fm_delete_title, entry.name)) },
            text = { Text(stringResource(if (entry.isDirectory) R.string.fm_delete_folder_desc else R.string.fm_delete_file_desc)) },
            confirmButton = {
                TextButton(onClick = { onDeleteEntry(entry.path); deleteTarget = null }) {
                    Text(stringResource(R.string.fm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private sealed interface FileNameDialog {
    data class Create(val parentPath: String, val isDirectory: Boolean) : FileNameDialog
    data class Rename(val path: String, val currentName: String) : FileNameDialog
}

@Composable
private fun FolderActionItems(onNewFile: () -> Unit, onNewFolder: () -> Unit, onUpload: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.fm_new_file)) },
        leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
        onClick = onNewFile,
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.fm_new_folder)) },
        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
        onClick = onNewFolder,
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.fm_upload_here)) },
        leadingIcon = { Icon(Icons.Default.Upload, null) },
        onClick = onUpload,
    )
}

