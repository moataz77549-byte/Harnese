package com.jarves.mh.connect

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jarves.mh.data.AppPreferences
import com.jarves.mh.model.RuntimeEvent
import com.jarves.mh.telegram.TelegramSecretStore
import java.util.UUID

/** Durable event journal and delivery outbox, independent of Compose and ViewModels. */
class HarneseEventBus private constructor(private val context: Context) : SQLiteOpenHelper(context, "nova-events.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, project TEXT, chat TEXT, task TEXT, kind TEXT, source TEXT, created INTEGER, body TEXT)")
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY, project TEXT, chat TEXT, label TEXT, agent TEXT, response TEXT DEFAULT '', closed INTEGER DEFAULT 0, source TEXT DEFAULT 'android')")
        db.execSQL("CREATE TABLE outbox(id TEXT PRIMARY KEY, destination INTEGER, project TEXT, kind TEXT, body TEXT, attempts INTEGER DEFAULT 0, next_at INTEGER DEFAULT 0, created INTEGER, state TEXT DEFAULT 'pending')")
        db.execSQL("CREATE INDEX delivery_due ON outbox(state, next_at, created)")
        createProgressTable(db)
    }
    private fun createProgressTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS progress(task TEXT PRIMARY KEY,project TEXT,destination INTEGER,body TEXT,message_id INTEGER DEFAULT 0,state TEXT DEFAULT 'queued',next_at INTEGER DEFAULT 0,updated INTEGER,revision INTEGER DEFAULT 0,sent_revision INTEGER DEFAULT -1)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN source TEXT DEFAULT 'android'")
            createProgressTable(db)
        }
    }
    private val responses = mutableMapOf<String, StringBuilder>()
    private val responseFlushAt = mutableMapOf<String, Long>()
    private val secrets by lazy { TelegramSecretStore(context) }
    private val prefs by lazy { context.getSharedPreferences("nova_hub", Context.MODE_PRIVATE) }
    fun prepare(project: String, chat: String, label: String, source: String) {
        prefs.edit().putString("chat:$project", chat).putString("label:$project", label)
            .putString("source:$project", source).commit()
    }
    @Synchronized fun setSubscription(project: String, enabled: Boolean) {
        val editor = prefs.edit()
        if (project == "*") prefs.all.keys.filter { it.startsWith("sub:") }.forEach { editor.remove(it) }
        editor.putBoolean("sub:$project", enabled).commit()
        if (!enabled) {
            if (project == "*") writableDatabase.execSQL("UPDATE progress SET state='closed',body=''")
            else writableDatabase.execSQL("UPDATE progress SET state='closed',body='' WHERE project=?",arrayOf(project))
            if (project == "*") writableDatabase.delete("outbox", "state='pending'", null)
            else writableDatabase.delete("outbox", "state='pending' AND project=?", arrayOf(project))
        }
    }
    @Synchronized fun setContent(enabled: Boolean) {
        prefs.edit().putBoolean("content", enabled).commit()
        if (!enabled) {
            responses.clear(); responseFlushAt.clear()
            writableDatabase.execSQL("UPDATE progress SET state='closed',body=''")
            writableDatabase.execSQL("UPDATE sessions SET response=''")
            writableDatabase.delete("outbox", "state='pending' AND kind IN ('agent.completed','agent.failed','agent.cancelled')", null)
        }
    }
    fun streamingEnabled() = prefs.getBoolean("streaming", false)
    @Synchronized fun setStreaming(enabled: Boolean) {
        prefs.edit().putBoolean("streaming",enabled).commit()
        if (!enabled) writableDatabase.execSQL("UPDATE progress SET state='closed',body=''")
    }
    fun contentEnabled() = prefs.getBoolean("content", false)
    fun setMuted(muted: Boolean) { prefs.edit().putBoolean("muted", muted).commit() }
    fun muted() = prefs.getBoolean("muted", false)

    @Synchronized fun begin(task: String, project: String, agent: String) {
        val projectName = AppPreferences(context).loadProjects().firstOrNull { it.id == project }?.name ?: project
        writableDatabase.insertWithOnConflict("sessions", null, ContentValues().apply {
            put("id", task); put("project", project); put("chat", prefs.getString("chat:$project", ""))
            put("label", "$projectName / ${prefs.getString("label:$project", "")}"); put("agent", agent)
            put("source",prefs.getString("source:$project","android"))
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized fun runtime(event: RuntimeEvent) {
        val db = writableDatabase
        db.rawQuery("SELECT project,chat,label,agent,response,closed,source FROM sessions WHERE id=?", arrayOf(event.sessionId)).use { c ->
            if (!c.moveToFirst() || c.getInt(5) != 0) return
            val project = c.getString(0); val chat = c.getString(1)
            val title = "${c.getString(2)} / ${c.getString(3)}"
            val source = c.getString(6)
            when (event) {
                is RuntimeEvent.AssistantDelta -> {
                    // Only persist opted-in content; never store raw secrets in the outbox.
                    if (contentEnabled()) {
                        val buffer = responses.getOrPut(event.sessionId) { StringBuilder(secrets.openContent(c.getString(4))) }
                        buffer.append(event.text)
                        val now = System.currentTimeMillis()
                        if (now - (responseFlushAt[event.sessionId] ?: 0) >= 1000) {
                            db.execSQL("UPDATE sessions SET response=? WHERE id=?", arrayOf(secrets.sealContent(buffer.toString()), event.sessionId))
                            responseFlushAt[event.sessionId] = now
                            if (streamingEnabled()) queueProgress(event.sessionId,project,"$title\n…\n" + TelegramContentPolicy.redact(buffer.toString()).takeLast(3000))
                        }
                    }
                    return
                }
                is RuntimeEvent.SessionStarted -> publish("${event.sessionId}:start", project, chat, event.sessionId, "agent.started", "$title\nبدأ الوكيل",source = source)
                is RuntimeEvent.ToolRequested -> publish("${event.sessionId}:approval:${event.request.approvalId}", project, chat, event.sessionId, "approval.requested", "$title\nطلب موافقة: ${event.request.toolName}\nالموافقة على الهاتف. /reject ${event.request.approvalId}",source = source)
                is RuntimeEvent.SessionCompleted, is RuntimeEvent.SessionFailed -> {
                    val failed = event is RuntimeEvent.SessionFailed
                    val reason = (event as? RuntimeEvent.SessionFailed)?.reason.orEmpty()
                    val cancelled = failed && (reason.contains("stopped by user",true) || reason.contains("cancel",true))
                    val status = if (failed) "توقفت المهمة: ${(event as RuntimeEvent.SessionFailed).reason}" else "اكتملت الإجابة"
                    val body = if (contentEnabled()) responses.remove(event.sessionId)?.toString() ?: secrets.openContent(c.getString(4)) else "مزامنة المحتوى معطلة؛ راجع الإجابة في الهاتف."
                    responses.remove(event.sessionId)
                    responseFlushAt.remove(event.sessionId)
                    db.beginTransaction()
                    try {
                        publish("${event.sessionId}:end", project, chat, event.sessionId, if (cancelled) "agent.cancelled" else if (failed) "agent.failed" else "agent.completed", "$title\n$status\n\n$body",source = source)
                        db.execSQL("UPDATE progress SET state='closed',body='' WHERE task=?",arrayOf(event.sessionId))
                        db.execSQL("UPDATE sessions SET closed=1,response='' WHERE id=?", arrayOf(event.sessionId))
                        db.setTransactionSuccessful()
                    } finally { db.endTransaction() }
                }
                else -> Unit
            }
        }
    }

    @Synchronized fun publish(id: String = UUID.randomUUID().toString(), project: String, chat: String, task: String, kind: String, body: String, source: String? = null) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val safe = TelegramContentPolicy.redact(body)
        db.beginTransaction()
        try {
            val inserted = db.insertWithOnConflict("events", null, ContentValues().apply {
                put("id", id); put("project", project); put("chat", chat); put("task", task); put("kind", kind)
                put("source", source ?: prefs.getString("source:$project", "android")); put("created", now); put("body", safe)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            val destination = TelegramSecretStore(context).linkedChatId()
            val subscribed = prefs.getBoolean("sub:$project", prefs.getBoolean("sub:*", true))
            if (inserted != -1L && destination != 0L && subscribed && !muted()) {
                TelegramContentPolicy.chunks(safe).forEachIndexed { index, part ->
                    db.insertWithOnConflict("outbox", null, ContentValues().apply {
                        put("id", "$id:$index"); put("project", project); put("kind", kind); put("destination", destination); put("body", part); put("created", now)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
            db.delete("events", "created < ?", arrayOf((now - RETENTION).toString()))
            db.delete("outbox", "created < ?", arrayOf((now - RETENTION).toString()))
            db.delete("progress", "updated < ?", arrayOf((now - RETENTION).toString()))
            db.execSQL("DELETE FROM sessions WHERE closed=1 AND id NOT IN (SELECT task FROM events)")
            db.execSQL("DELETE FROM outbox WHERE id IN (SELECT id FROM outbox ORDER BY created DESC LIMIT -1 OFFSET 4000)")
            db.execSQL("DELETE FROM events WHERE id IN (SELECT id FROM events ORDER BY created DESC LIMIT -1 OFFSET 2000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized private fun queueProgress(task: String, project: String, body: String) {
        val destination = secrets.linkedChatId()
        if (destination == 0L || muted() || !prefs.getBoolean("sub:$project",prefs.getBoolean("sub:*",true))) return
        val now=System.currentTimeMillis()
        writableDatabase.insertWithOnConflict("progress",null,ContentValues().apply {
            put("task",task);put("project",project);put("destination",destination);put("body",body);put("updated",now)
        },SQLiteDatabase.CONFLICT_IGNORE)
        writableDatabase.execSQL("UPDATE progress SET body=?,updated=?,revision=revision+1 WHERE task=? AND state IN ('queued','active','sending') AND body<>?",arrayOf(body,now,task,body))
    }
    data class Progress(val task: String,val destination: Long,val body: String,val messageId: Long,val revision: Long)
    @Synchronized fun nextProgress(): Progress? = readableDatabase.rawQuery(
        "SELECT task,destination,body,message_id,revision FROM progress WHERE state IN ('queued','active') AND revision>sent_revision AND next_at<=? AND updated>=? ORDER BY updated LIMIT 1",
        arrayOf(System.currentTimeMillis().toString(),(System.currentTimeMillis()-60000).toString())
    ).use { if(it.moveToFirst()) Progress(it.getString(0),it.getLong(1),it.getString(2),it.getLong(3),it.getLong(4)) else null }
    @Synchronized fun beginProgress(task: String) { writableDatabase.execSQL("UPDATE progress SET state='sending' WHERE task=? AND state IN ('queued','active')",arrayOf(task)) }
    @Synchronized fun progressSent(task: String,messageId: Long,revision: Long) {
        writableDatabase.execSQL("UPDATE progress SET state='active',message_id=?,sent_revision=?,next_at=? WHERE task=? AND state='sending'",arrayOf(messageId,revision,System.currentTimeMillis()+5000,task))
    }
    @Synchronized fun progressFailed(task: String,uncertain: Boolean,retryAfterMillis: Long=5000) {
        writableDatabase.execSQL("UPDATE progress SET state=CASE WHEN ?=1 AND message_id=0 THEN 'uncertain' WHEN message_id=0 THEN 'queued' ELSE 'active' END,next_at=? WHERE task=? AND state='sending'",arrayOf(if(uncertain)1 else 0,System.currentTimeMillis()+maxOf(5000,retryAfterMillis),task))
    }
    @Synchronized fun closeProgress(task: String) { writableDatabase.execSQL("UPDATE progress SET state='closed',body='' WHERE task=?",arrayOf(task)) }
    @Synchronized fun recoverInterruptedTasks() {
        data class Interrupted(val task: String,val project: String,val chat: String,val label: String,val source: String)
        val tasks=readableDatabase.rawQuery("SELECT id,project,chat,label,source FROM sessions WHERE closed=0",null).use { c ->
            buildList { while(c.moveToNext()) add(Interrupted(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4))) }
        }
        for(task in tasks) {
            publish(id="${task.task}:interrupted",project=task.project,chat=task.chat,task=task.task,kind="agent.interrupted",body="${task.label}\nتوقفت عملية التطبيق. لم تُستأنف المهمة تلقائيًا.",source=task.source)
            writableDatabase.execSQL("UPDATE sessions SET closed=1,response='' WHERE id=?",arrayOf(task.task))
        }
        writableDatabase.execSQL("UPDATE progress SET state='closed',body=''")
    }
    data class Delivery(val id: String, val destination: Long, val body: String, val attempts: Int)
    @Synchronized fun next(): Delivery? = readableDatabase.rawQuery(
        "SELECT id,destination,body,attempts FROM outbox WHERE state='pending' AND next_at<=? AND created>=? ORDER BY created,rowid LIMIT 1",
        arrayOf(System.currentTimeMillis().toString(), (System.currentTimeMillis()-RETENTION).toString())
    ).use { c -> if (c.moveToFirst()) Delivery(c.getString(0), c.getLong(1), c.getString(2), c.getInt(3)) else null }
    @Synchronized fun beginDelivery(id: String) {
        writableDatabase.execSQL("UPDATE outbox SET state='sending' WHERE id=? AND state='pending'", arrayOf(id))
    }
    @Synchronized fun recoverInterruptedDeliveries() {
        writableDatabase.execSQL("UPDATE outbox SET state='uncertain' WHERE state='sending'")
        writableDatabase.execSQL("UPDATE progress SET state=CASE WHEN message_id=0 THEN 'uncertain' ELSE 'active' END WHERE state='sending'")
    }
    @Synchronized fun uncertain(id: String) {
        writableDatabase.execSQL("UPDATE outbox SET state='uncertain' WHERE id=?", arrayOf(id))
    }
    @Synchronized fun delivered(id: String) { writableDatabase.execSQL("UPDATE outbox SET state='sent',body='' WHERE id=?", arrayOf(id)) }
    @Synchronized fun failed(item: Delivery, retryAfterMillis: Long = 0) {
        val count = item.attempts + 1
        writableDatabase.update("outbox", ContentValues().apply {
            put("attempts", count); put("next_at", System.currentTimeMillis() + maxOf(retryAfterMillis, minOf(3_600_000L, 5000L * (1L shl count.coerceAtMost(10)))))
            put("state", if (count >= 8) "failed" else "pending")
        }, "id=?", arrayOf(item.id))
    }
    fun pendingCount(): Long = readableDatabase.rawQuery("SELECT count(*) FROM outbox WHERE state='pending'", null).use { it.moveToFirst(); it.getLong(0) }
    fun uncertainCount(): Long = readableDatabase.rawQuery("SELECT count(*) FROM outbox WHERE state='uncertain'", null).use { it.moveToFirst(); it.getLong(0) }
    fun recentEvents(): List<String> = readableDatabase.rawQuery("SELECT kind,body FROM events ORDER BY created DESC LIMIT 20", null).use { c ->
        buildList { while (c.moveToNext()) add("${c.getString(0)}: ${c.getString(1).take(300)}") }
    }
    companion object {
        private const val RETENTION = 7L * 24 * 60 * 60 * 1000
        @Volatile private var instance: HarneseEventBus? = null
        fun get(context: Context): HarneseEventBus = instance ?: synchronized(this) {
            instance ?: HarneseEventBus(context.applicationContext).also { instance = it }
        }
    }
}
