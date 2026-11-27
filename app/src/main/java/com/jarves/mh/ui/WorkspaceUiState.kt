package com.jarves.mh.ui

import android.app.Application
import android.app.ActivityManager
import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.os.SystemClock
import android.os.Build
import android.os.StatFs
import android.os.Debug
import android.system.Os
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.jarves.mh.BuildConfig
import com.jarves.mh.connect.HarneseCommand
import com.jarves.mh.connect.HarneseCommandBus
import com.jarves.mh.connect.HarneseCommandHandler
import com.jarves.mh.connect.HarneseCommandResult
import com.jarves.mh.R
import com.jarves.mh.data.ApiKeyVault
import com.jarves.mh.data.ApiKeyInfo
import com.jarves.mh.data.AppPreferences
import com.jarves.mh.model.ActivityItem
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.ChatAttachment
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.Project
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProjectType
import com.jarves.mh.model.AndroidTemplate
import com.jarves.mh.model.ProjectChat
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.RuntimeEvent
import com.jarves.mh.model.ToolRequest
import com.jarves.mh.model.WorkspaceEntry
import com.jarves.mh.model.projectSlug
import com.jarves.mh.model.generateQuickChatIdentity
import com.jarves.mh.model.providerProtocolForAgent
import com.jarves.mh.network.ConnectionValidation
import com.jarves.mh.network.ModelDiscoveryResult
import com.jarves.mh.network.ProviderApiClient
import com.jarves.mh.network.GitHubRepository
import com.jarves.mh.network.GitHubCliResponse
import com.jarves.mh.network.GitHubRepositoryLoadException
import com.jarves.mh.network.GitHubRepositoryLoadFailure
import com.jarves.mh.network.GitHubRepositoryPages
import com.jarves.mh.runtime.ClaudeRuntimeBridge
import com.jarves.mh.runtime.DshRuntimeBridge
import com.jarves.mh.runtime.AgentRegistry
import com.jarves.mh.runtime.AgentUpdateInfo
import com.jarves.mh.runtime.AntigravityAuthController
import com.jarves.mh.runtime.AntigravityAuthState
import com.jarves.mh.runtime.AntigravityAuthStatus
import com.jarves.mh.runtime.AntigravityRuntimeBridge
import com.jarves.mh.runtime.NativeSpawnProcess
import com.jarves.mh.runtime.RuntimeInstallProgress
import com.jarves.mh.runtime.RuntimeInstaller
import com.jarves.mh.runtime.RuntimeSetupController
import com.jarves.mh.runtime.RuntimeSetupService
import com.jarves.mh.runtime.RuntimeSetupSnapshot
import com.jarves.mh.runtime.RuntimeSetupStatus
import com.jarves.mh.runtime.readHeadBytes
import com.jarves.mh.runtime.readTailText
import com.jarves.mh.runtime.supportsArm64Runtime
import com.jarves.mh.runtime.AndroidAppInstaller
import com.jarves.mh.runtime.AndroidBuildPhase
import com.jarves.mh.runtime.AndroidBuildRecord
import com.jarves.mh.runtime.AndroidAction
import com.jarves.mh.runtime.AndroidApkInfo
import com.jarves.mh.runtime.AndroidBuildIssue
import com.jarves.mh.runtime.AndroidBuildStage
import com.jarves.mh.runtime.AndroidHealthCheck
import com.jarves.mh.runtime.AndroidHealthFix
import com.jarves.mh.runtime.AndroidHealthStatus
import com.jarves.mh.runtime.AndroidLogLevel
import com.jarves.mh.runtime.AndroidLogLine
import com.jarves.mh.runtime.AndroidLogcatState
import com.jarves.mh.runtime.AndroidProjectTemplateGenerator
import com.jarves.mh.runtime.RuntimeExecutionService
import com.jarves.mh.runtime.RuntimeTaskController
import com.jarves.mh.runtime.androidGradleCommand
import com.jarves.mh.runtime.diagnoseAndroidBuildFailure
import com.jarves.mh.runtime.findAndroidProjectRoot
import com.jarves.mh.runtime.ProjectWorkspaceFacts
import com.jarves.mh.runtime.ProjectWorkspaceInspector
import com.jarves.mh.runtime.ReleaseArtifactInfo
import com.jarves.mh.runtime.findReleaseApk
import com.jarves.mh.runtime.findReleaseBundle
import com.jarves.mh.runtime.isArtifactStale
import com.jarves.mh.runtime.isAndroidArtifact
import com.jarves.mh.runtime.findDebugApk
import com.jarves.mh.runtime.findReusableDebugApk
import com.jarves.mh.runtime.inferAndroidBuildStage
import com.jarves.mh.runtime.isDebugApkStale
import com.jarves.mh.runtime.parseAndroidBuildIssues
import com.jarves.mh.update.AppUpdateInfo
import com.jarves.mh.update.AppUpdater
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.net.UnknownHostException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class StartupStage { CHECKING, SETUP_REQUIRED, INSTALLING, MODEL_SETUP, INITIALIZING, READY, ERROR }

enum class ApiPingStatus { IDLE, PINGING, OK, FAILED }
enum class AppUpdateStatus { AVAILABLE, PERMISSION_REQUIRED, DOWNLOADING, INSTALLING, ERROR }
enum class GitHubAuthStatus { DISCONNECTED, STARTING, AWAITING_USER, CONNECTED, ERROR }

data class ExportSelection(
    val defaultIncluded: Boolean = true,
    val portableBackup: Boolean = false,
    val overrides: Map<String, Boolean> = emptyMap(),
) {
    fun includes(path: String): Boolean {
        val normalized = path.trim('/')
        return overrides.entries
            .asSequence()
            .filter { (rule, _) -> normalized == rule || normalized.startsWith("$rule/") }
            .maxByOrNull { it.key.length }
            ?.value ?: defaultIncluded
    }

    fun canContainIncluded(path: String): Boolean {
        val normalized = path.trim('/')
        return includes(normalized) || overrides.any { (rule, included) ->
            included && rule.startsWith("$normalized/")
        }
    }

    fun toggled(path: String, directory: Boolean): ExportSelection {
        val normalized = path.trim('/')
        val nextValue = !includes(normalized)
        val next = overrides.toMutableMap()
        if (directory) next.keys.filter { it.startsWith("$normalized/") }.forEach(next::remove)
        next[normalized] = nextValue
        return copy(overrides = next)
    }
}

data class DeveloperDiagnostics(
    val loading: Boolean = false,
    val appPssBytes: Long = 0L,
    val javaHeapBytes: Long = 0L,
    val javaHeapMaxBytes: Long = 0L,
    val activeProcesses: List<String> = emptyList(),
    val messageCount: Int = 0,
    val liveActivityCount: Int = 0,
    val terminalBufferBytes: Long = 0L,
    val buildLogBytes: Long = 0L,
    val logcatLines: Int = 0,
    val fileEntries: Int = 0,
    val runtimeCacheBytes: Long = 0L,
    val recentExit: String? = null,
    val refreshedAtMillis: Long? = null,
)

/** Counts UTF-8 storage without allocating a second byte array for large logs. */
internal fun utf8SizeInBytes(value: String): Long {
    var bytes = 0L
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char.code < 0x80 -> bytes += 1
            char.code < 0x800 -> bytes += 2
            char.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                bytes += 4
                index += 1
            }
            else -> bytes += 3
        }
        index += 1
    }
    return bytes
}

enum class FileReadOnlyReason { TOO_LARGE, BINARY, INVALID_UTF8, UNSAFE, READ_ERROR }

data class OpenedFileState(
    val path: String,
    val content: String? = null,
    val draft: String = "",
    val loading: Boolean = true,
    val editing: Boolean = false,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val conflict: Boolean = false,
    val readOnlyReason: FileReadOnlyReason? = null,
    val originalFingerprint: String? = null,
    val lineEnding: String = "\n",
    val targetLine: Int? = null,
    val targetColumn: Int? = null,
)

data class TerminalOutputLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val command: String,
    val output: String,
    val exitCode: Int = 0,
)

data class QueuedFollowUp(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val chatId: String,
    val agentKind: AgentKind,
    val prompt: String,
    val attachments: List<ChatAttachment>,
)

data class AppUiState(
    val startupStage: StartupStage = StartupStage.CHECKING,
    val startupProgress: Float = 0f,
    val startupMessage: String = "Checking this device…",
    val startupBytes: Pair<Long, Long>? = null,
    val startupLogs: List<String> = emptyList(),
    val startupIndeterminate: Boolean = false,
    val startupError: String? = null,
    val startupErrorIsOffline: Boolean = false,
    val showDetailedSetupProgress: Boolean = false,
    val onboardingComplete: Boolean = false,
    val backgroundSetupComplete: Boolean = false,
    val initialLanguageSelected: Boolean = false,
    val provider: ProviderProfile = ProviderProfile(ProviderKind.ANTHROPIC),
    val activeApiKeyName: String? = null,
    val themeMode: com.jarves.mh.ui.theme.AppThemeMode = com.jarves.mh.ui.theme.AppThemeMode.DARK,
    val languageCode: String = "system",
    val apiPingStatus: ApiPingStatus = ApiPingStatus.IDLE,
    val apiPingMessage: String? = null,
    val projects: List<Project> = emptyList(),
    val projectImporting: Boolean = false,
    val projectImportMessage: String? = null,
    val gitCloneRunning: Boolean = false,
    val gitCloneMessage: String? = null,
    val githubAuthStatus: GitHubAuthStatus = GitHubAuthStatus.DISCONNECTED,
    val githubLogin: String? = null,
    val githubUserCode: String? = null,
    val githubVerificationUri: String? = null,
    val githubMessage: String? = null,
    val githubRepositories: List<GitHubRepository> = emptyList(),
    val githubRepositoriesLoading: Boolean = false,
    val activeProject: Project? = null,
    val workspaceVisible: Boolean = false,
    val readOnlyProject: Project? = null,
    val readOnlyProjectChats: List<ProjectChat> = emptyList(),
    val readOnlyChatId: String? = null,
    val readOnlyMessages: List<ChatMessage> = emptyList(),
    val projectChats: List<ProjectChat> = emptyList(),
    val activeChatId: String? = null,
    val workspaceFiles: List<WorkspaceEntry> = emptyList(),
    val projectFacts: ProjectWorkspaceFacts? = null,
    val projectRepositoryChecking: Boolean = false,
    val projectRepositoryStatus: String? = null,
    val androidProjectDetected: Boolean = false,
    val filesLoading: Boolean = false,
    val diagnostics: DeveloperDiagnostics = DeveloperDiagnostics(),
    val openedFile: OpenedFileState? = null,
    val projectExportRunning: Boolean = false,
    val projectExportSucceededAtMillis: Long? = null,
    val messages: List<ChatMessage> = listOf(
        ChatMessage(fromUser = false, text = "Hi! Tell me what you want to build or change."),
    ),
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val pendingApproval: ToolRequest? = null,
    val changes: List<ChangeItem> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val liveProcess: List<ActivityItem> = emptyList(),
    val liveThinking: Boolean = false,
    val activeThinkingBlockId: Long? = null,
    val taskStartedAtMillis: Long? = null,
    val taskFinishedAtMillis: Long? = null,
    val workSegmentStartedAtMillis: Long? = null,
    val currentTaskRequest: String? = null,
    val previewReady: Boolean = false,
    val previewUrl: String? = null,
    val isRunning: Boolean = false,
    val pendingAgentSwitch: AgentKind? = null,
    val taskFailure: String? = null,
    val taskHasOutput: Boolean = false,
    val changesAgentKind: AgentKind? = null,
    val queuedFollowUps: List<QueuedFollowUp> = emptyList(),
    val activeSessionId: String? = null,
    val toastMessage: String? = null,
    val terminalSlot: Int = 0,
    val editorTabs: List<String> = emptyList(),
    val editorDirtyTabs: Set<String> = emptySet(),
    val projectTerminalLines: List<TerminalOutputLine> = emptyList(),
    val projectTerminalLiveOutput: String = "",
    val projectTerminalRunning: Boolean = false,
    val projectTerminalCwd: String = "/workspace",
    val projectTerminalCommand: String? = null,
    val projectTerminalDraft: String? = null,
    val pendingTerminalCommand: String? = null,
    val suggestedProjectRoot: String? = null,
    val selectedDevStacks: Set<DevStack> = emptySet(),
    val installCodexDuringSetup: Boolean = true,
    val installedDevStacks: Set<DevStack> = emptySet(),
    val devStackInstalling: DevStack? = null,
    val devStackRemoving: Boolean = false,
    val devStackMessage: String? = null,
    val devStackProgress: Float = 0f,
    val devStackBytes: Pair<Long, Long>? = null,
    val devStackBytesPerSecond: Long? = null,
    val agentKind: AgentKind = AgentKind.CLAUDE_CODE,
    val primaryAgentKind: AgentKind = AgentKind.CLAUDE_CODE,
    val installedAgentVersions: Map<AgentKind, String> = emptyMap(),
    val agentInstalling: AgentKind? = null,
    val agentMessage: String? = null,
    val agentProgress: Float = 0f,
    val agentDownloadedBytes: Long? = null,
    val agentTotalBytes: Long? = null,
    val agentBytesPerSecond: Long? = null,
    val agentUpdates: Map<AgentKind, AgentUpdateInfo> = emptyMap(),
    val agentUpdatesChecking: Boolean = false,
    val agentUpdating: AgentKind? = null,
    val agentUpdateMessage: String? = null,
    val agentUpdateProgress: Float = 0f,
    val agentUpdateIndeterminate: Boolean = false,
    val agentUpdateDownloadedBytes: Long? = null,
    val agentUpdateTotalBytes: Long? = null,
    val agentUpdateBytesPerSecond: Long? = null,
    val antigravityAuth: AntigravityAuthState = AntigravityAuthState(),
    val antigravityModel: String = "",
    val antigravityEffort: String = "high",
    val antigravityModels: List<String> = emptyList(),
    val antigravityModelsLoading: Boolean = false,
    val antigravityModelsError: String? = null,
    val pendingRuntimeInstall: String? = null,
    val runtimeProbeRunning: Boolean = false,
    val androidBuildRunning: Boolean = false,
    val androidBuildMessage: String? = null,
    val androidBuildPhase: AndroidBuildPhase = AndroidBuildPhase.IDLE,
    val androidBuildAction: AndroidAction = AndroidAction.NONE,
    val androidBuildStage: AndroidBuildStage = AndroidBuildStage.IDLE,
    val androidBuildIssues: List<AndroidBuildIssue> = emptyList(),
    val androidBuildLog: String = "",
    val androidBuildStartedAtMillis: Long? = null,
    val androidBuildFinishedAtMillis: Long? = null,
    val androidBuildApkPath: String? = null,
    val androidBuildApkSizeBytes: Long? = null,
    val androidBuildRecentDurationsMillis: List<Long> = emptyList(),
    val androidApkInfo: AndroidApkInfo? = null,
    val releaseArtifactInfo: ReleaseArtifactInfo? = null,
    val projectSearchResult: com.jarves.mh.runtime.ProjectSearchResult? = null,
    val projectSearchRunning: Boolean = false,
    val localGitStatus: String? = null,
    val localGitChecking: Boolean = false,
    val androidHealthChecks: List<AndroidHealthCheck> = emptyList(),
    val androidHealthRunning: Boolean = false,
    val androidLogcat: AndroidLogcatState = AndroidLogcatState(),
    val appUpdate: AppUpdateInfo? = null,
    val appUpdateStatus: AppUpdateStatus? = null,
    val appUpdateDownloadedBytes: Long = 0L,
    val appUpdateTotalBytes: Long = -1L,
    val appUpdateError: String? = null,
)

