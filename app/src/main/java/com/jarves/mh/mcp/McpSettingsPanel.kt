package com.jarves.mh.mcp

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun McpSettingsPanel(project: String?, agent: String) {
    val context=LocalContext.current
    val manager=remember { McpConnectionManager(context) }
    val scope=rememberCoroutineScope()
    var servers by remember { mutableStateOf(manager.servers()) }
    var endpoint by remember { mutableStateOf("") };var bearer by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) };var tools by remember { mutableStateOf<List<String>>(emptyList()) }
    var tool by remember { mutableStateOf("") };var arguments by remember { mutableStateOf("{}") }
    var pending by remember { mutableStateOf<String?>(null) };var preview by remember { mutableStateOf("") };var status by remember { mutableStateOf("") }
    val auth=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) pending?.let { id -> scope.launch {
            status=withContext(Dispatchers.IO) { runCatching { manager.executeLocally(id).toString() }.getOrElse { it.message ?: "MCP failed" } }
            pending=null
        } }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("MCP — HTTPS / Streamable HTTP (2025)")
        OutlinedTextField(value=endpoint,onValueChange={endpoint=it},label={Text("HTTPS endpoint (Neon / Supabase / MCP)")})
        OutlinedTextField(value=bearer,onValueChange={bearer=it},label={Text("Bearer token")},visualTransformation=PasswordVisualTransformation())
        OutlinedButton(onClick={ runCatching { manager.save("MCP",endpoint,bearer);servers=manager.servers();bearer="" }.onFailure {status=it.message.orEmpty()} }) {Text("Save / حفظ")}
        servers.forEach { server ->
            Text(server.endpoint)
            if(project!=null) {
                var enabled by remember(server.id,project,agent) { mutableStateOf(manager.enabled(server.id,project,agent)) }
                Row { Text("$project / $agent",modifier=androidx.compose.ui.Modifier.weight(1f));Switch(checked=enabled,onCheckedChange={enabled=it;manager.enable(server.id,project,agent,it)}) }
            }
            OutlinedButton(onClick={ selected=server.id;scope.launch { status="…";status=withContext(Dispatchers.IO) {runCatching {
                val list=manager.tools(server.id);tools=(0 until list.length()).map {list.getJSONObject(it).getString("name")};tools.joinToString("\n")
            }.getOrElse {it.message ?: "MCP connection failed"}} } }) {Text("Connect / Tools — اتصال / الأدوات")}
        }
        if(selected!=null && project!=null && tools.isNotEmpty()) {
            OutlinedTextField(value=tool,onValueChange={tool=it},label={Text("Tool name / اسم الأداة")})
            OutlinedTextField(value=arguments,onValueChange={arguments=it},label={Text("JSON arguments / SQL review")})
            OutlinedButton(onClick={runCatching {require(tool in tools);pending=manager.propose(selected!!,project,agent,tool,JSONObject(arguments));preview=manager.review(pending!!)}.onFailure{status=it.message.orEmpty()}}) {Text("Review / مراجعة")}
            if(pending!=null) {
                Text(preview)
                OutlinedButton(onClick={val intent=context.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("Harnese Nova MCP",tool);if(intent!=null)auth.launch(intent) else status="Set a phone screen lock / فعّل قفل الهاتف"}) {Text("Authenticate and execute / تأكيد الهوية والتنفيذ")}
            }
        }
        Text(status)
    }
}
