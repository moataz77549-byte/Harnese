package com.jarves.mh.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.jarves.mh.connect.HarneseCommand
import com.jarves.mh.connect.HarneseCommandBus
import com.jarves.mh.connect.HarneseCommandResult
import com.jarves.mh.data.AppPreferences
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-started foreground service using Telegram Bot API long polling.
 *
 * Remote identity is checked on *every* command. Remote shell commands and
 * approvals are intentionally not exposed. Agents use the same VM policy as UI.
 * The service does not keep a ViewModel reference and stops when user requests.
 */
class TelegramPollingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = kotlinx.coroutines.sync.Mutex()
    private var lastSentAt = 0L
    private var pollingJob: Job? = null
    private var deliveryJob: Job? = null
    private lateinit var store: TelegramSecretStore
    private lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        store = TelegramSecretStore(applicationContext)
        preferences = AppPreferences(applicationContext)
        com.jarves.mh.ui.HarneseRuntimeOwner.get(application as android.app.Application)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Harnese Connect Telegram control", NotificationManager.IMPORTANCE_LOW
        ))
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Harnese Connect")
            .setContentText("Telegram bot connection enabled by device owner")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (pollingJob?.isActive == true) return START_STICKY
        val token = store.token()
        if (token == null) {
            connection.value = "توكن تيليجرام مفقود أو تعذر فك تشفيره."
            stopSelf()
            return START_NOT_STICKY
        }
        pollingJob = scope.launch { pollingLoop(token) }
        deliveryJob = scope.launch {
            val journal = com.jarves.mh.connect.HarneseEventBus.get(applicationContext)
            journal.recoverInterruptedDeliveries()
            while (isActive) {
                val item = journal.next()
                if (item == null) {
                    val progress = journal.nextProgress()
                    if (progress == null) { delay(2000); continue }
                    if (store.linkedChatId() != progress.destination || journal.muted() || !journal.contentEnabled() || !journal.streamingEnabled()) { journal.closeProgress(progress.task); continue }
                    journal.beginProgress(progress.task)
                    try { journal.progressSent(progress.task,sendProgress(token,progress),progress.revision) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (rejected: TelegramRejectedException) {
                        if (rejected.status == 400 || rejected.status == 403) journal.closeProgress(progress.task)
                        else journal.progressFailed(progress.task,false,rejected.retryAfterSeconds*1000)
                    } catch (_: Exception) { journal.progressFailed(progress.task,true) }
                    continue
                }
                // Drop queued content after unlink/re-pair; never reroute an old delivery.
                if (store.linkedChatId() != item.destination || journal.muted()) {
                    journal.delivered(item.id)
                    continue
                }
                journal.beginDelivery(item.id)
                try {
                    reply(token, item.destination, item.body)
                    journal.delivered(item.id)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (rejected: TelegramRejectedException) { journal.failed(item, rejected.retryAfterSeconds * 1000L) }
                catch (_: Exception) { journal.uncertain(item.id) }
                delay(1100)
            }
        }
        return START_STICKY
    }

    private suspend fun pollingLoop(token: String) {
        connection.value = "جار الاتصال بتيليجرام…"
        while (scope.isActive) {
            try {
                val args = JSONObject()
                    .put("offset", store.updateOffset())
                    .put("timeout", 25)
                    .put("limit", 30)
                    .put("allowed_updates", JSONArray().put("message").put("callback_query"))
                val response = api(token, "getUpdates", args)
                val updates = response.getJSONArray("result")
                connection.value = "متصل — بانتظار الرسائل"
                for (index in 0 until updates.length()) {
                    val update = updates.getJSONObject(index)
                    if (!store.reserveUpdate(update.getLong("update_id"))) continue
                    try {
                        if (update.has("callback_query")) handleCallback(token, update.getJSONObject("callback_query"))
                        else handleMessage(token, update.optJSONObject("message"))
                    }
                    catch (_: CancellationException) { throw CancellationException() }
                    catch (_: Exception) { connection.value = "تعذر معالجة أحد الأوامر. أعد إرساله." }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                // Do not log the full HTTP URL: Telegram embeds bot tokens in it.
                connection.value = if (error.message?.contains("HTTP 409") == true) {
                    "تعارض: أوقف webhook أو أي نسخة بوت أخرى تستخدم getUpdates."
                } else "الاتصال غير متاح. سيتم إعادة المحاولة."
                delay(8_000L)
            }
        }
    }

    private suspend fun handleMessage(token: String, message: JSONObject?) {
        if (message == null) return
        val chat = message.optJSONObject("chat") ?: return
        val sender = message.optJSONObject("from") ?: return
        if (sender.optBoolean("is_bot")) return
        val userId = sender.optLong("id")
        val chatId = chat.optLong("id")
        val isPrivate = chat.optString("type") == "private"
        if (!isPrivate || userId == 0L || chatId != userId) return
        val instruction = TelegramInstructionParser.parse(message.optString("text", ""))
        if (instruction is TelegramInstruction.Pair) {
            val paired = store.tryPair(instruction.code, userId, chatId, isPrivate)
            reply(token, chatId, if (paired) {
                "✅ تم اقتران حسابك بتطبيق Harnese. اكتب /help."
            } else "رمز الاقتران غير صالح أو انتهت صلاحيته أو الحساب مرتبط مسبقًا.")
            return
        }
        if (!store.authorized(userId, chatId, isPrivate)) {
            if (instruction == TelegramInstruction.Help) reply(token, chatId,
                "Harnese Connect: افتح إعدادات تيليجرام في تطبيق Harnese، ثم أرسل /pair مع الرمز المؤقت.")
            return
        }
        if (instruction is TelegramInstruction.Control && instruction.name == "apk" && instruction.argument == "send") {
            val result = HarneseCommandBus.dispatch(HarneseCommand.HubOperation(store.lastProjectId(), "apk", "send"))
            reply(token, chatId, result.text)
            result.filePath?.let { path ->
                scope.launch {
                    val journal = com.jarves.mh.connect.HarneseEventBus.get(applicationContext)
                    val task = java.util.UUID.randomUUID().toString()
                    val outcome = runCatching { sendDocument(token, chatId, java.io.File(path)) }
                    journal.publish(id = "apk:$task", project = store.lastProjectId().orEmpty(), chat = store.lastChatId().orEmpty(), task = task,
                        kind = "apk.upload", body = if (outcome.isSuccess) "اكتمل إرسال APK" else "تعذر رفع APK أو لم يتأكد الإرسال. استخدم مشاركة الملف من الهاتف أو أعد الطلب يدويًا.")
                }
            }
            return
        }
        val output = execute(instruction)
        val keyboard = when (instruction) {
            TelegramInstruction.Projects -> preferences.loadProjects().take(20).map { it.name to "p:${it.id}" }
            TelegramInstruction.Chats -> store.lastProjectId()?.let { id -> preferences.loadProjectChats(id).take(20).map { it.title to "c:${it.id}" } }.orEmpty()
            else -> emptyList()
        }
        reply(token, chatId, output, keyboard)
    }

    private suspend fun handleCallback(token: String, callback: JSONObject) {
        val sender = callback.optJSONObject("from") ?: return
        val message = callback.optJSONObject("message") ?: return
        val chat = message.optJSONObject("chat") ?: return
        if (!store.authorized(sender.optLong("id"), chat.optLong("id"), chat.optString("type") == "private") || sender.optBoolean("is_bot")) return
        api(token, "answerCallbackQuery", JSONObject().put("callback_query_id", callback.getString("id")))
        val data = callback.optString("data")
        val instruction = when {
            data.startsWith("p:") -> TelegramInstructionParser.parse("/open " + data.removePrefix("p:"))
            data.startsWith("c:") -> TelegramInstructionParser.parse("/chat " + data.removePrefix("c:"))
            else -> return
        }
        reply(token, chat.getLong("id"), execute(instruction))
    }

    private suspend fun execute(instruction: TelegramInstruction): String {
        fun selectId(value: String, ids: List<String>): String? {
            ids.firstOrNull { it == value }?.let { return it }
            val matching = ids.filter { it.startsWith(value, ignoreCase = true) }
            return matching.singleOrNull()
        }
        suspend fun dispatch(cmd: HarneseCommand): HarneseCommandResult = HarneseCommandBus.dispatch(cmd)
        fun noProject() = "اختر مشروعًا أولًا: /projects ثم /open <معرّف>."
        return when (instruction) {
            is TelegramInstruction.Control -> {
                val journal = com.jarves.mh.connect.HarneseEventBus.get(applicationContext)
                when (instruction.name) {
                    "subscribe", "unsubscribe" -> {
                        val project = if (instruction.argument == "all") "*" else store.lastProjectId() ?: return noProject()
                        journal.setSubscription(project, instruction.name == "subscribe")
                        "تم تحديث الاشتراك: $project"
                    }
                    "notify" -> when (instruction.argument) {
                        "mute" -> { journal.setMuted(true); "الإشعارات مكتومة" }
                        "unmute" -> { journal.setMuted(false); "الإشعارات مفعلة" }
                        "status" -> { journal.setContent(false); "إشعارات الحالة فقط" }
                        "content" -> "فعّل مزامنة المحتوى من الهاتف لحماية خصوصيتك."
                        else -> "/notify mute | unmute | status\nالمعلّق: ${journal.pendingCount()}"
                    }
                    else -> {
                        if ((instruction.name in setOf("build", "updates") || (instruction.name in setOf("agent","model") && instruction.argument.isNotBlank())) && !store.remoteTasksEnabled()) return "فعّل المهام عن بعد من الهاتف أولًا."
                        dispatch(HarneseCommand.HubOperation(store.lastProjectId(), instruction.name, instruction.argument)).text
                    }
                }
            }
            TelegramInstruction.Help -> """🤖 Harnese Connect
/projects — المشاريع
/newproject NAME — إنشاء مشروع
/files — ملفات جذر المشروع
/history — آخر رسائل الدردشة
/open ID — فتح مشروع
/chats — دردشات المشروع
/chat ID — اختيار دردشة
/newchat — إنشاء دردشة
/ask TEXT — رسالة للوكيل (يلزم تفعيل الأوامر من الهاتف)
/reply TEXT — متابعة المحادثة
/dashboard /active /tasks — المتابعة
/model /models /agent — الوكيل والنموذج
/build /buildlog /apk — البناء ونتيجته
/subscribe [all] /unsubscribe [all] — الاشتراك
/notify mute|unmute|status — الإشعارات
/health /updates — البيئة والتحديثات
/status — حالة المشروع
/pending — طلبات الموافقة
/reject ID — رفض طلب أداة
/stop — إيقاف المهمة
⚠️ لا يقبل البوت أوامر shell أو الموافقة على أدوات حساسة. افتح التطبيق للموافقة المحلية.""".trimIndent()
            is TelegramInstruction.NewProject ->
                dispatch(HarneseCommand.CreateProject(instruction.name)).text
            TelegramInstruction.Files -> {
                val project = store.lastProjectId() ?: return noProject()
                dispatch(HarneseCommand.ListFiles(project)).text.take(3700)
            }
            TelegramInstruction.History -> {
                val project = store.lastProjectId() ?: return noProject()
                val chat = store.lastChatId()?.takeIf(String::isNotBlank)
                    ?: return "اختر دردشة أولاً: /chats ثم /chat ID."
                dispatch(HarneseCommand.ReadMessages(project, chat)).text.take(3700)
            }
            TelegramInstruction.Projects -> {
                val result = dispatch(HarneseCommand.ListProjects)
                if (!result.ok) result.text else result.text.take(3700)
            }
            is TelegramInstruction.Open -> {
                val project = selectId(instruction.projectIdOrPrefix, preferences.loadProjects().map { it.id })
                    ?: return "معرّف المشروع غير موجود أو غير محدد. أرسل /projects."
                val result = dispatch(HarneseCommand.OpenProject(project))
                if (result.ok) store.selection(project, preferences.loadProjectChats(project).firstOrNull()?.id)
                result.text
            }
            TelegramInstruction.Chats -> {
                val project = store.lastProjectId() ?: return noProject()
                dispatch(HarneseCommand.ListChats(project)).text.take(3700)
            }
            is TelegramInstruction.Chat -> {
                val project = store.lastProjectId() ?: return noProject()
                val chat = selectId(instruction.chatIdOrPrefix, preferences.loadProjectChats(project).map { it.id })
                    ?: return "الدردشة غير موجودة. اكتب /chats."
                val result = dispatch(HarneseCommand.SelectChat(project, chat))
                if (result.ok) store.selectChat(chat)
                result.text
            }
            TelegramInstruction.NewChat -> {
                val project = store.lastProjectId() ?: return noProject()
                val result = dispatch(HarneseCommand.NewChat(project))
                if (result.ok) store.selectChat(preferences.loadProjectChats(project).firstOrNull()?.id.orEmpty())
                result.text
            }
            is TelegramInstruction.Ask -> {
                if (!store.remoteTasksEnabled())
                    return "إرسال مهام الوكيل معطّل. فعّل «السماح بإرسال المهام من تيليجرام» من الهاتف أولاً."
                val project = store.lastProjectId() ?: return noProject()
                val chat = store.lastChatId()?.takeIf(String::isNotBlank)
                    ?: return "اختر دردشة من /chats ثم /chat ID."
                dispatch(HarneseCommand.SendPrompt(project, chat, instruction.prompt)).text
            }
            TelegramInstruction.Status -> dispatch(HarneseCommand.ProjectStatus(store.lastProjectId())).text
            TelegramInstruction.Stop -> dispatch(HarneseCommand.StopTask).text
            TelegramInstruction.Pending -> dispatch(HarneseCommand.PendingApproval).text
            is TelegramInstruction.Reject -> dispatch(HarneseCommand.RejectPendingTool(instruction.approvalId)).text
            is TelegramInstruction.Pair -> "الاقتران يتم قبل تنفيذ الأوامر."
            TelegramInstruction.Unknown -> "أمر غير معروف. اكتب /help."
        }
    }

    private suspend fun reply(token: String, chatId: Long, text: String, keyboard: List<Pair<String, String>> = emptyList()) {
        for (part in com.jarves.mh.connect.TelegramContentPolicy.chunks(com.jarves.mh.connect.TelegramContentPolicy.redact(text))) {
            val body = JSONObject().put("chat_id", chatId).put("text", part)
                .put("disable_web_page_preview", true)
            if (keyboard.isNotEmpty()) body.put("reply_markup", JSONObject().put("inline_keyboard", JSONArray().apply {
                keyboard.forEach { (label, data) -> put(JSONArray().put(JSONObject().put("text", label.take(60)).put("callback_data", data))) }
            }))
            sendMutex.lock()
            try {
                delay((1100L - (System.currentTimeMillis() - lastSentAt)).coerceAtLeast(0))
                api(token, "sendMessage", body)
                lastSentAt = System.currentTimeMillis()
            } finally { sendMutex.unlock() }
        }
    }

    private suspend fun sendProgress(token: String, item: com.jarves.mh.connect.HarneseEventBus.Progress): Long {
        sendMutex.lock()
        try {
            delay((1100L-(System.currentTimeMillis()-lastSentAt)).coerceIn(0,1100))
            val body=JSONObject().put("chat_id",item.destination).put("text",item.body.take(3900)).put("disable_web_page_preview",true)
            if(item.messageId!=0L) body.put("message_id",item.messageId)
            val result=api(token,if(item.messageId==0L) "sendMessage" else "editMessageText",body)
            lastSentAt=System.currentTimeMillis()
            val id = result.optJSONObject("result")?.optLong("message_id")?.takeIf { it!=0L } ?: item.messageId
            check(id != 0L) { "Telegram message ID unavailable" }
            return id
        } finally { sendMutex.unlock() }
    }

    private suspend fun sendDocument(token: String, chatId: Long, file: java.io.File) {
        require(store.linkedChatId() == chatId && file.length() in 1..49_000_000L)
        val project = preferences.loadProjects().firstOrNull { it.id == store.lastProjectId() } ?: error("Project unavailable")
        val root = java.io.File(applicationContext.filesDir, "workspaces/${project.id}")
        com.jarves.mh.connect.ProjectFilePolicy.allowedApk(root, file.path)
        val boundary = "Nova${java.util.UUID.randomUUID()}"
        val prefix = ("--$boundary\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n$chatId\r\n" +
            "--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"project.apk\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n").toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val connection = URL("https://api.telegram.org/bot$token/sendDocument").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.doOutput = true; connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 120000
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setFixedLengthStreamingMode(prefix.size + file.length() + suffix.size)
            file.inputStream().use { input -> connection.outputStream.use { output -> output.write(prefix); input.copyTo(output, 64*1024); output.write(suffix) } }
            check(connection.responseCode in 200..299)
            check(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optBoolean("ok"))
        } finally { connection.disconnect() }
    }

    private class TelegramRejectedException(val status: Int = 0, val retryAfterSeconds: Long = 0) : java.io.IOException("HTTP $status")

    private fun api(token: String, method: String, body: JSONObject): JSONObject {
        val connection = (URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val retry = runCatching { JSONObject(connection.errorStream.bufferedReader().use { it.readText() }).optJSONObject("parameters")?.optLong("retry_after") ?: 0L }.getOrDefault(0L).coerceIn(0, 3600)
                throw TelegramRejectedException(status, retry)
            }
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!result.optBoolean("ok")) throw TelegramRejectedException()
            return result
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        deliveryJob?.cancel()
        scope.cancel()
        connection.value = "متوقف"
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_STOP = "com.jarves.harnese.telegram.STOP"
        private const val CHANNEL_ID = "harnese_telegram_connect"
        private const val NOTIFICATION_ID = 19458
        val connection: MutableStateFlow<String> = MutableStateFlow("متوقف")
    }
}
