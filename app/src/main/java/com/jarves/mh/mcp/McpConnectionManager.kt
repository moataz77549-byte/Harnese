package com.jarves.mh.mcp

import android.content.Context
import com.jarves.mh.telegram.TelegramSecretStore
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Streamable HTTP compatibility transport (2025-11-25); no production resources are created. */
class McpConnectionManager(context: Context) {
    private val prefs = context.getSharedPreferences("nova_mcp", Context.MODE_PRIVATE)
    private val vault = TelegramSecretStore(context)
    private val journal = com.jarves.mh.connect.HarneseEventBus.get(context)
    data class Server(val id: String, val name: String, val endpoint: String)
    private fun profiles(): JSONObject = prefs.getString("profiles", null)?.let { JSONObject(vault.openContent(it)) } ?: JSONObject()
    @Synchronized fun servers(): List<Server> { val json=profiles(); return json.keys().asSequence().map { id -> val o=json.getJSONObject(id);Server(id,o.getString("name"),o.getString("endpoint")) }.toList() }
    @Synchronized fun save(name: String, endpoint: String, bearer: String): Server {
        val uri=URI(endpoint.trim())
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.rawQuery==null && uri.fragment==null) { "Use an HTTPS endpoint without credentials in its URL" }
        val id=UUID.randomUUID().toString();val json=profiles()
        json.put(id,JSONObject().put("name",name.take(80)).put("endpoint",uri.toString()).put("bearer",bearer))
        check(prefs.edit().putString("profiles",vault.sealContent(json.toString())).commit())
        return Server(id,name,uri.toString())
    }
    fun enabled(server: String, project: String, agent: String) = prefs.getBoolean("enabled:$server:$project:$agent",false)
    fun enable(server: String, project: String, agent: String, value: Boolean) { prefs.edit().putBoolean("enabled:$server:$project:$agent",value).commit() }
    private data class Session(val endpoint: String, val token: String, var id: String? = null, var version: String="2025-11-25")
    private fun request(session: Session, method: String, params: JSONObject, notification: Boolean=false): JSONObject {
        val id=UUID.randomUUID().toString()
        val body=JSONObject().put("jsonrpc","2.0").put("method",method).put("params",params)
        if(!notification) body.put("id",id)
        val connection=URL(session.endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.requestMethod="POST";connection.doOutput=true;connection.connectTimeout=15000;connection.readTimeout=30000
            connection.setRequestProperty("Content-Type","application/json")
            connection.setRequestProperty("Accept","application/json, text/event-stream")
            connection.setRequestProperty("MCP-Protocol-Version",session.version)
            if(session.token.isNotBlank()) connection.setRequestProperty("Authorization","Bearer ${session.token}")
            session.id?.let { connection.setRequestProperty("Mcp-Session-Id",it) }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "MCP HTTP ${connection.responseCode}" }
            connection.getHeaderField("Mcp-Session-Id")?.let { session.id=it }
            if(notification) return JSONObject()
            val reader=connection.inputStream.bufferedReader()
            val payload=StringBuilder()
            if(connection.contentType?.startsWith("text/event-stream")==true) {
                var count=0
                while(true) {
                    val line=reader.readLine() ?: break;count+=line.length
                    check(count<=1_000_000) { "MCP response too large" }
                    if(line.startsWith("data:")) payload.append(line.removePrefix("data:").trimStart()).append('\n')
                    if(line.isEmpty() && payload.isNotEmpty()) {
                        val response=JSONObject(payload.toString());payload.clear()
                        if(response.optString("id")==id) return checked(response)
                    }
                }
                error("MCP response ended before its result")
            }
            val buffer=CharArray(8192)
            while(true) { val n=reader.read(buffer);if(n<0)break;payload.append(buffer,0,n);check(payload.length<=1_000_000) }
            val response=JSONObject(payload.toString());check(response.optString("id")==id)
            return checked(response)
        } finally { connection.disconnect() }
    }
    private fun checked(response: JSONObject): JSONObject {
        check(!response.has("error")) { "MCP rejected request (${response.optJSONObject("error")?.optInt("code")})" }
        return response.getJSONObject("result")
    }
    private fun connect(server: String): Session {
        val json=profiles().getJSONObject(server)
        val session=Session(json.getString("endpoint"),json.optString("bearer"))
        val result=request(session,"initialize",JSONObject().put("protocolVersion",session.version).put("capabilities",JSONObject())
            .put("clientInfo",JSONObject().put("name","Harnese Nova").put("version","1.3.2")))
        val version=result.getString("protocolVersion")
        require(version in setOf("2025-03-26","2025-06-18","2025-11-25")) { "MCP protocol $version requires a newer transport" }
        session.version=version
        request(session,"notifications/initialized",JSONObject(),true)
        return session
    }
    fun tools(server: String): JSONArray {
        val session=connect(server);val tools=JSONArray();var cursor: String?=null
        repeat(10) {
            val params=JSONObject();cursor?.let { params.put("cursor",it) }
            val result=request(session,"tools/list",params);val page=result.getJSONArray("tools")
            for(i in 0 until page.length())tools.put(page.getJSONObject(i))
            cursor=result.optString("nextCursor").takeIf { it.isNotBlank() }
            if(cursor==null)return tools
        }
        error("MCP tool list exceeded pagination limit")
    }
    /** Every tool call is queued for local review, including SQL. Server annotations are not authority. */
    fun propose(server: String, project: String, agent: String, tool: String, arguments: JSONObject): String {
        require(enabled(server,project,agent)) { "Enable this server for the project and agent first" }
        val id=UUID.randomUUID().toString()
        val request=JSONObject().put("server",server).put("project",project).put("agent",agent).put("tool",tool)
            .put("arguments",arguments).put("expires",System.currentTimeMillis()+5*60_000L)
        check(prefs.edit().putString("pending:$id",vault.sealContent(request.toString())).commit())
        journal.publish(id="mcp:$id",project=project,chat="",task=id,kind="mcp.review",body="MCP: $tool — مراجعة محلية مطلوبة؛ لا تُنفذ استعلامات SQL تلقائيًا.")
        return id
    }
    fun review(id: String): String = vault.openContent(prefs.getString("pending:$id",null) ?: error("Review unavailable"))
    /** Called only after phone credential confirmation. Consume durably BEFORE the remote operation. */
    @Synchronized fun executeLocally(id: String): JSONObject {
        val json=JSONObject(review(id));require(json.getLong("expires")>System.currentTimeMillis()) { "Review expired" }
        require(enabled(json.getString("server"),json.getString("project"),json.getString("agent")))
        check(prefs.edit().remove("pending:$id").putString("audit:$id","started").commit())
        try {
            val result=request(connect(json.getString("server")),"tools/call",JSONObject().put("name",json.getString("tool")).put("arguments",json.getJSONObject("arguments")))
            prefs.edit().putString("audit:$id","completed").commit();return result
        } catch(error: Exception) { prefs.edit().putString("audit:$id","failed_or_uncertain").commit();throw error }
    }
}
