package com.jarves.mh.mcp

import android.content.Context
import com.jarves.mh.telegram.TelegramSecretStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Streamable HTTP transport with per-request metadata and legacy session compatibility. */
class McpConnectionManager(context: Context) {
    private val prefs = context.getSharedPreferences("nova_mcp", Context.MODE_PRIVATE)
    private val vault = TelegramSecretStore(context)
    private val definitions = mutableMapOf<String, Map<String, JSONObject>>()
    private val journal = com.jarves.mh.connect.HarneseEventBus.get(context)
    data class Server(val id: String, val name: String, val endpoint: String)
    private fun profiles(): JSONObject = prefs.getString("profiles", null)?.let { JSONObject(vault.openContent(it)) } ?: JSONObject()
    @Synchronized fun servers(): List<Server> { val json=profiles(); return json.keys().asSequence().map { id -> val o=json.getJSONObject(id);Server(id,o.getString("name"),o.getString("endpoint")) }.toList() }
    @Synchronized fun save(name: String, endpoint: String, bearer: String): Server {
        val uri=McpEndpointPolicy.validate(endpoint)
        val id=UUID.randomUUID().toString();val json=profiles()
        json.put(id,JSONObject().put("name",name.take(80)).put("endpoint",uri.toString()).put("bearer",bearer))
        check(prefs.edit().putString("profiles",vault.sealContent(json.toString())).commit())
        return Server(id,name,uri.toString())
    }
    fun enabled(server: String, project: String, agent: String) = prefs.getBoolean("enabled:$server:$project:$agent",false)
    fun enable(server: String, project: String, agent: String, value: Boolean) { prefs.edit().putBoolean("enabled:$server:$project:$agent",value).commit() }
    private data class Session(val endpoint: String, val token: String, var id: String? = null, var version: String=McpProtocolPolicy.MODERN)
    private fun request(session: Session, method: String, params: JSONObject, notification: Boolean=false, extraHeaders: Map<String,String> = emptyMap()): JSONObject {
        val id=UUID.randomUUID().toString()
        if (session.version == McpProtocolPolicy.MODERN) params.put("_meta",JSONObject()
            .put("io.modelcontextprotocol/protocolVersion",session.version)
            .put("io.modelcontextprotocol/clientInfo",JSONObject().put("name","Harnese Nova").put("version","1.3.2"))
            .put("io.modelcontextprotocol/clientCapabilities",JSONObject()))
        val body=JSONObject().put("jsonrpc","2.0").put("method",method).put("params",params)
        if(!notification) body.put("id",id)
        val connection=URL(session.endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.requestMethod="POST";connection.doOutput=true;connection.connectTimeout=15000;connection.readTimeout=30000
            connection.setRequestProperty("Content-Type","application/json")
            connection.setRequestProperty("Accept","application/json, text/event-stream")
            connection.setRequestProperty("MCP-Protocol-Version",session.version)
            if (session.version == McpProtocolPolicy.MODERN) {
                connection.setRequestProperty("Mcp-Method",method)
                params.optString("name").takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Mcp-Name",McpProtocolPolicy.headerValue(it)) }
                extraHeaders.forEach { (key,value) -> connection.setRequestProperty(key,value) }
            }
            if(session.token.isNotBlank()) connection.setRequestProperty("Authorization","Bearer ${session.token}")
            session.id?.let { connection.setRequestProperty("Mcp-Session-Id",it) }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                val error = runCatching { JSONObject(connection.errorStream.bufferedReader().use { it.readText().take(16000) }).optJSONObject("error") }.getOrNull()
                throw RemoteFailure(connection.responseCode,error?.optInt("code"),error?.optJSONObject("data")?.optJSONArray("supported"))
            }
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
    private class RemoteFailure(val http: Int, val code: Int?, val supported: JSONArray? = null) : java.io.IOException("MCP request rejected: HTTP $http / RPC $code")
    private fun checked(response: JSONObject): JSONObject {
        response.optJSONObject("error")?.let { throw RemoteFailure(200,it.optInt("code"),it.optJSONObject("data")?.optJSONArray("supported")) }
        return response.getJSONObject("result")
    }
    private fun connect(server: String): Session {
        val json=profiles().getJSONObject(server)
        val session=Session(json.getString("endpoint"),json.optString("bearer"))
        try {
            request(session,"server/discover",JSONObject())
            return session
        } catch (failure: RemoteFailure) {
            if (failure.code == -32022) {
                val offered = failure.supported ?: throw failure
                require((0 until offered.length()).any { offered.optString(it) in setOf("2025-11-25","2025-06-18","2025-03-26") }) { "No compatible MCP version" }
            } else if (failure.http !in setOf(400,404) && failure.code != -32601) throw failure
        }
        session.version="2025-11-25"
        session.id=null
        val result=request(session,"initialize",JSONObject().put("protocolVersion",session.version).put("capabilities",JSONObject())
            .put("clientInfo",JSONObject().put("name","Harnese Nova").put("version","1.3.2")))
        val version=result.getString("protocolVersion")
        require(version in setOf("2025-03-26","2025-06-18","2025-11-25")) { "MCP protocol $version requires a newer transport" }
        session.version=version
        request(session,"notifications/initialized",JSONObject(),true)
        return session
    }
    fun tools(server: String): JSONArray = listTools(server,connect(server))
    private fun listTools(server: String, session: Session): JSONArray {
        val tools=JSONArray();var cursor: String?=null
        repeat(10) {
            val params=JSONObject();cursor?.let { params.put("cursor",it) }
            val result=request(session,"tools/list",params);val page=result.getJSONArray("tools")
            for(i in 0 until page.length()) {
                val tool=page.getJSONObject(i)
                if (runCatching { McpProtocolPolicy.mirroredHeaders(tool.getJSONObject("inputSchema"),JSONObject()) }.isSuccess) tools.put(tool)
            }
            cursor=result.optString("nextCursor").takeIf { it.isNotBlank() }
            if(cursor==null) {
                definitions[server]=(0 until tools.length()).map { tools.getJSONObject(it) }.associateBy { it.getString("name") }
                return tools
            }
        }
        error("MCP tool list exceeded pagination limit")
    }
    /** Every tool call is queued for local review, including SQL. Server annotations are not authority. */
    fun propose(server: String, project: String, agent: String, tool: String, arguments: JSONObject): String {
        require(enabled(server,project,agent)) { "Enable this server for the project and agent first" }
        val definition=definitions[server]?.get(tool) ?: error("Load the server tool list before proposing a call")
        McpProtocolPolicy.mirroredHeaders(definition.getJSONObject("inputSchema"),arguments)
        val id=UUID.randomUUID().toString()
        val request=JSONObject().put("toolDefinitionHash",McpProtocolPolicy.fingerprint(definition)).put("server",server).put("project",project).put("agent",agent).put("tool",tool)
            .put("arguments",arguments).put("expires",System.currentTimeMillis()+5*60_000L)
        check(prefs.edit().putString("pending:$id",vault.sealContent(request.toString())).commit())
        journal.publish(id="mcp:$id",project=project,chat="",task=id,kind="mcp.review",body="MCP: $tool — مراجعة محلية مطلوبة؛ لا تُنفذ استعلامات SQL تلقائيًا.",source="mcp")
        return id
    }
    fun review(id: String): String = vault.openContent(prefs.getString("pending:$id",null) ?: error("Review unavailable"))
    /** Called only after phone credential confirmation. Consume durably BEFORE the remote operation. */
    @Synchronized fun executeLocally(id: String): JSONObject {
        val json=JSONObject(review(id));require(json.getLong("expires")>System.currentTimeMillis()) { "Review expired" }
        require(enabled(json.getString("server"),json.getString("project"),json.getString("agent")))
        check(prefs.edit().remove("pending:$id").putString("audit:$id","started:${System.currentTimeMillis()}").putString("audit_body:$id",vault.sealContent(json.toString())).commit())
        try {
            val server=json.getString("server");val session=connect(server)
            listTools(server,session)
            val definition=definitions[server]?.get(json.getString("tool")) ?: error("Tool is no longer available")
            require(McpProtocolPolicy.fingerprint(definition)==json.getString("toolDefinitionHash")) { "Tool definition changed; review again" }
            val headers=McpProtocolPolicy.mirroredHeaders(definition.getJSONObject("inputSchema"),json.getJSONObject("arguments"))
            val result=request(session,"tools/call",JSONObject().put("name",json.getString("tool")).put("arguments",json.getJSONObject("arguments")),extraHeaders=headers)
            prefs.edit().putString("audit:$id","completed").commit();return result
        } catch(error: Exception) { prefs.edit().putString("audit:$id","failed_or_uncertain").commit();throw error }
    }
}
