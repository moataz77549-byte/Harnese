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
import androidx.lifecycle.viewModelScope
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

/** UI facade. Runtime ownership and coroutine lifetime belong to the application. */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = HarneseRuntimeOwner.get(application)
    val state: StateFlow<AppUiState> get() = runtime.state
    val terminalLines: StateFlow<List<TerminalOutputLine>> get() = runtime.terminalLines
    val isTerminalRunning: StateFlow<Boolean> get() = runtime.isTerminalRunning
    val terminalLiveOutput: StateFlow<String> get() = runtime.terminalLiveOutput
    val terminalCurrentCommand: StateFlow<String?> get() = runtime.terminalCurrentCommand
    fun runTerminalCommand(cmd: String) = runtime.runTerminalCommand(cmd)
    fun sendTerminalInput(text: String) = runtime.sendTerminalInput(text)
    fun interruptTerminalCommand() = runtime.interruptTerminalCommand()
    fun clearTerminal() = runtime.clearTerminal()
    fun requestProjectTerminalCommand(command: String) = runtime.requestProjectTerminalCommand(command)
    fun prepareProjectTerminalCommand(command: String) = runtime.prepareProjectTerminalCommand(command)
    fun consumeProjectTerminalDraft() = runtime.consumeProjectTerminalDraft()
    fun openProjectTerminal() = runtime.openProjectTerminal()
    fun confirmProjectTerminalCommand() = runtime.confirmProjectTerminalCommand()
    fun cancelProjectTerminalCommand() = runtime.cancelProjectTerminalCommand()
    fun stopProjectTerminalCommand() = runtime.stopProjectTerminalCommand()
    fun sendProjectTerminalInput(text: String) = runtime.sendProjectTerminalInput(text)
    fun interruptProjectTerminalCommand() = runtime.interruptProjectTerminalCommand()
    fun clearProjectTerminal() = runtime.clearProjectTerminal()
    fun refreshDeveloperDiagnostics() = runtime.refreshDeveloperDiagnostics()
    fun stopInactiveDeveloperProcesses() = runtime.stopInactiveDeveloperProcesses()
    fun clearDeveloperRuntimeCache() = runtime.clearDeveloperRuntimeCache()
    fun releaseMemoryCaches() = runtime.releaseMemoryCaches()
    fun buildAndRunAndroidApp() = runtime.buildAndRunAndroidApp()
    fun performAndroidAction(action: AndroidAction) = runtime.performAndroidAction(action)
    fun cleanAndroidProject() = runtime.cleanAndroidProject()
    fun cancelAndroidBuild() = runtime.cancelAndroidBuild()
    fun refreshAndroidDashboard() = runtime.refreshAndroidDashboard()
    fun refreshAndroidApkInfo() = runtime.refreshAndroidApkInfo()
    fun shareAndroidApk() = runtime.shareAndroidApk()
    fun saveAndroidApk(uri: Uri) = runtime.saveAndroidApk(uri)
    fun openInstalledAndroidAppDetails() = runtime.openInstalledAndroidAppDetails()
    fun uninstallAndroidApp() = runtime.uninstallAndroidApp()
    fun openAndroidIssue(issue: AndroidBuildIssue) = runtime.openAndroidIssue(issue)
    fun refreshAndroidHealth() = runtime.refreshAndroidHealth()
    fun performAndroidHealthFix(fix: AndroidHealthFix) = runtime.performAndroidHealthFix(fix)
    fun startAndroidLogcat() = runtime.startAndroidLogcat()
    fun stopAndroidLogcat() = runtime.stopAndroidLogcat()
    fun toggleAndroidLogcatPause() = runtime.toggleAndroidLogcatPause()
    fun clearAndroidLogcat() = runtime.clearAndroidLogcat()
    fun setAndroidLogcatQuery(value: String) = runtime.setAndroidLogcatQuery(value)
    fun setAndroidLogcatLevel(value: AndroidLogLevel) = runtime.setAndroidLogcatLevel(value)
    fun openAndroidLogLine(line: AndroidLogLine) = runtime.openAndroidLogLine(line)
    fun toggleTheme() = runtime.toggleTheme()
    fun setThemeMode(mode: com.jarves.mh.ui.theme.AppThemeMode) = runtime.setThemeMode(mode)
    fun setLanguage(code: String) = runtime.setLanguage(code)
    fun finishInitialLanguageSetup() = runtime.finishInitialLanguageSetup()
    fun reopenInitialLanguageSetup() = runtime.reopenInitialLanguageSetup()
    fun getSavedApiKey(kind: ProviderKind): String = runtime.getSavedApiKey(kind)
    fun getSavedApiKeys(kind: ProviderKind): List<ApiKeyInfo> = runtime.getSavedApiKeys(kind)
    fun addApiKey(kind: ProviderKind, name: String, secret: String): List<ApiKeyInfo> = runtime.addApiKey(kind, name, secret)
    fun activateApiKey(kind: ProviderKind, keyId: String): List<ApiKeyInfo> = runtime.activateApiKey(kind, keyId)
    fun removeApiKey(kind: ProviderKind, keyId: String): List<ApiKeyInfo> = runtime.removeApiKey(kind, keyId)
    fun startRuntimeSetup() = runtime.startRuntimeSetup()
    fun retryStartup() = runtime.retryStartup()
    fun checkForAppUpdate(force: Boolean = false) = runtime.checkForAppUpdate(force)
    fun setDebugUpdateManifestUrl(url: String) = runtime.setDebugUpdateManifestUrl(url)
    fun clearDebugUpdateManifestUrl() = runtime.clearDebugUpdateManifestUrl()
    fun debugUpdateManifestUrl(): String = runtime.debugUpdateManifestUrl()
    fun installAppUpdate() = runtime.installAppUpdate()
    fun dismissAppUpdateError() = runtime.dismissAppUpdateError()
    fun finishOnboarding(profile: ProviderProfile, secret: String) = runtime.finishOnboarding(profile, secret)
    fun finishAntigravityOnboarding() = runtime.finishAntigravityOnboarding()
    fun chooseOnboardingAgent(kind: AgentKind) = runtime.chooseOnboardingAgent(kind)
    fun updateProvider(profile: ProviderProfile, secret: String) = runtime.updateProvider(profile, secret)
    fun finishBackgroundSetup() = runtime.finishBackgroundSetup()
    fun selectAgent(kind: AgentKind) = runtime.selectAgent(kind)
    fun installAgent(kind: AgentKind) = runtime.installAgent(kind)
    fun requestEnvironmentProbe(name: String) = runtime.requestEnvironmentProbe(name)
    fun repairDevelopmentEnvironment(name: String) = runtime.repairDevelopmentEnvironment(name)
    fun installRequestedRuntime() = runtime.installRequestedRuntime()
    fun checkAgentUpdates() = runtime.checkAgentUpdates()
    fun updateAgent(kind: AgentKind) = runtime.updateAgent(kind)
    fun startAntigravityLogin() = runtime.startAntigravityLogin()
    fun submitAntigravityCode(code: String) = runtime.submitAntigravityCode(code)
    fun logoutAntigravity() = runtime.logoutAntigravity()
    fun setAntigravityModel(model: String) = runtime.setAntigravityModel(model)
    fun setAntigravityEffort(effort: String) = runtime.setAntigravityEffort(effort)
    fun refreshAntigravityModels() = runtime.refreshAntigravityModels()
    fun toggleDevStack(stack: DevStack) = runtime.toggleDevStack(stack)
    fun installDevStack(stack: DevStack, refreshAndroidHealthAfter: Boolean = false) = runtime.installDevStack(stack, refreshAndroidHealthAfter)
    fun removeDevStack(stack: DevStack) = runtime.removeDevStack(stack)
    suspend fun discoverModels(profile: ProviderProfile, secret: String): ModelDiscoveryResult = runtime.discoverModels(profile, secret)
    suspend fun validateProvider(
        profile: ProviderProfile,
        secret: String,
        models: List<com.jarves.mh.network.DiscoveredModel>,
    ): ConnectionValidation = runtime.validateProvider(profile, secret, models)
    fun pingApi() = runtime.pingApi()
    fun testAntigravityConnection() = runtime.testAntigravityConnection()
    fun openProject(project: Project) = runtime.openProject(project)
    fun closeProject() = runtime.closeProject()
    fun closeReadOnlyProject() = runtime.closeReadOnlyProject()
    fun switchReadOnlyChat(chatId: String) = runtime.switchReadOnlyChat(chatId)
    fun activateReadOnlyProject() = runtime.activateReadOnlyProject()
    fun consumeToast() = runtime.consumeToast()
    fun createProject(name: String) = runtime.createProject(name)
    fun createDevelopmentProject(name: String, typescript: Boolean) = runtime.createDevelopmentProject(name, typescript)
    fun createAndroidProject(name: String, template: AndroidTemplate) = runtime.createAndroidProject(name, template)
    fun createQuickProject() = runtime.createQuickProject()
    fun importZipProject(uri: Uri) = runtime.importZipProject(uri)
    fun clonePublicGitRepository(url: String) = runtime.clonePublicGitRepository(url)
    fun cloneGitHubRepository(repository: GitHubRepository) = runtime.cloneGitHubRepository(repository)
    fun startGitHubLogin() = runtime.startGitHubLogin()
    fun generateNewGitHubCode() = runtime.generateNewGitHubCode()
    fun refreshGitHubRepositories() = runtime.refreshGitHubRepositories()
    fun disconnectGitHub() = runtime.disconnectGitHub()
    fun renameProject(projectId: String, newName: String) = runtime.renameProject(projectId, newName)
    fun deleteProject(projectId: String) = runtime.deleteProject(projectId)
    fun useSuggestedProjectRoot() = runtime.useSuggestedProjectRoot()
    fun exportActiveProject(uri: Uri, selection: ExportSelection) = runtime.exportActiveProject(uri, selection)
    fun createChat() = runtime.createChat()
    fun switchChat(chatId: String) = runtime.switchChat(chatId)
    fun refreshProjectFiles() = runtime.refreshProjectFiles()
    fun loadProjectDirectory(relativePath: String) = runtime.loadProjectDirectory(relativePath)
    fun openFile(entry: WorkspaceEntry) = runtime.openFile(entry)
    fun closeFile() = runtime.closeFile()
    fun beginFileEdit() = runtime.beginFileEdit()
    fun updateFileDraft(value: String) = runtime.updateFileDraft(value)
    fun cancelFileEdit() = runtime.cancelFileEdit()
    fun reloadOpenedFile() = runtime.reloadOpenedFile()
    fun dismissFileConflict() = runtime.dismissFileConflict()
    fun saveOpenedFile() = runtime.saveOpenedFile()
    fun createWorkspaceEntry(parentPath: String, name: String, isDirectory: Boolean) = runtime.createWorkspaceEntry(parentPath, name, isDirectory)
    fun renameWorkspaceEntry(path: String, newName: String) = runtime.renameWorkspaceEntry(path, newName)
    fun deleteWorkspaceEntry(path: String) = runtime.deleteWorkspaceEntry(path)
    fun uploadToWorkspace(parentPath: String, uris: List<Uri>) = runtime.uploadToWorkspace(parentPath, uris)
    fun shareWorkspaceFile(path: String) = runtime.shareWorkspaceFile(path)
    fun saveWorkspaceFileCopy(path: String, destination: Uri) = runtime.saveWorkspaceFileCopy(path, destination)
    fun addChatAttachments(uris: List<Uri>) = runtime.addChatAttachments(uris)
    fun removePendingAttachment(attachmentId: String) = runtime.removePendingAttachment(attachmentId)
    fun openChatAttachment(attachment: ChatAttachment) = runtime.openChatAttachment(attachment)
    fun sendPrompt(prompt: String) = runtime.sendPrompt(prompt)
    fun steerQueuedFollowUp(id: String) = runtime.steerQueuedFollowUp(id)
    fun removeQueuedFollowUp(id: String) = runtime.removeQueuedFollowUp(id)
    fun answerApproval(approved: Boolean) = runtime.answerApproval(approved)
    fun stopTask() = runtime.stopTask()
    fun undoLastChanges() = runtime.undoLastChanges()
    fun keepLastChanges() = runtime.keepLastChanges()
    fun undoFileChange(path: String) = runtime.undoFileChange(path)
    fun keepFileChange(path: String) = runtime.keepFileChange(path)
    override fun onCleared() { runtime.stopAndroidLogcat(); super.onCleared() }
    companion object {
        const val LOGCAT_GRANT_COMMAND = HarneseRuntimeController.LOGCAT_GRANT_COMMAND
    }
}
