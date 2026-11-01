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
    private var pollingJob: Job? = null
    private lateinit var store: TelegramSecretStore
    private lateinit var preferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        store = TelegramSecretStore(applicationContext)
        preferences = AppPreferences(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Harnese Telegram remote control", NotificationManager.IMPORTANCE_LOW
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
                    .put("allowed_updates", JSONArray().put("message"))
                val response = api(token, "getUpdates", args)
                val updates = response.getJSONArray("result")
                connection.value = "متصل — بانتظار الرسائل"
                for (index in 0 until updates.length()) {
                    val update = updates.getJSONObject(index)
                    if (!store.reserveUpdate(update.getLong("update_id"))) continue
                    try { handleMessage(token, update.optJSONObject("message")) }
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
        val output = execute(instruction)
        reply(token, chatId, output)
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
            TelegramInstruction.Help -> """🤖 Harnese Connect
/projects — المشاريع
/open ID — فتح مشروع
/chats — دردشات المشروع
/chat ID — اختيار دردشة
/newchat — إنشاء دردشة
/ask TEXT — رسالة للوكيل (يلزم تفعيل الأوامر من الهاتف)
/status — حالة المشروع
/pending — طلبات الموافقة
/reject ID — رفض طلب أداة
/stop — إيقاف المهمة
⚠️ لا يقبل البوت أوامر shell أو الموافقة على أدوات حساسة. افتح التطبيق للموافقة المحلية.""".trimIndent()
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

    private suspend fun reply(token: String, chatId: Long, text: String) {
        val body = JSONObject().put("chat_id", chatId).put("text", text.take(3900))
            .put("disable_web_page_preview", true)
        api(token, "sendMessage", body)
    }

    private fun api(token: String, method: String, body: JSONObject): JSONObject {
        val connection = (URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) error("HTTP $status")
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(result.optBoolean("ok")) { "Telegram rejected the request" }
            return result
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        pollingJob?.cancel()
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
