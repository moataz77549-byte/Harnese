package com.jarves.mh.mcp

import java.security.MessageDigest
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Header mirroring never grants a server permission to call a tool. */
object McpProtocolPolicy {
    const val MODERN = "2026-07-28"
    fun headerValue(value: String): String = if (
        value == value.trim() && value.all { it.code in 32..126 || it == '\t' } &&
        !(value.startsWith("=?base64?") && value.endsWith("?="))
    ) value else "=?base64?${Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))}?="

    fun mirroredHeaders(schema: JSONObject, arguments: JSONObject): Map<String, String> {
        val result = linkedMapOf<String,String>()
        val seen = mutableSetOf<String>()
        fun walk(node: JSONObject, path: List<String>, reachable: Boolean) {
            if (node.has("x-mcp-header")) {
                val name = node.getString("x-mcp-header")
                require(reachable && path.isNotEmpty() && Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+").matches(name)) { "Invalid MCP header annotation" }
                require(node.optString("type") in setOf("string","integer","boolean") && seen.add(name.lowercase())) { "Invalid or duplicate MCP header" }
                var value: Any? = arguments
                for (part in path) value = (value as? JSONObject)?.opt(part)
                if (value != null && value != JSONObject.NULL) {
                    when (node.getString("type")) {
                        "string" -> require(value is String)
                        "boolean" -> require(value is Boolean)
                        "integer" -> require(value is Number && value.toDouble() % 1.0 == 0.0 && kotlin.math.abs(value.toDouble()) <= 9007199254740991.0)
                    }
                    result["Mcp-Param-$name"] = headerValue(value.toString())
                }
            }
            for (key in node.keys()) {
                val child = node.opt(key)
                if (key == "properties" && child is JSONObject) {
                    for (name in child.keys()) child.optJSONObject(name)?.let { walk(it,path + name,reachable) }
                } else if (child is JSONObject) walk(child,path,false)
                else if (child is JSONArray) for (i in 0 until child.length()) child.optJSONObject(i)?.let { walk(it,path,false) }
            }
        }
        walk(schema,emptyList(),true)
        return result
    }
    fun fingerprint(value: Any?): String {
        fun canonical(v: Any?): String = when(v) {
            is JSONObject -> v.keys().asSequence().sorted().joinToString(",","{","}") { JSONObject.quote(it) + ":" + canonical(v.get(it)) }
            is JSONArray -> (0 until v.length()).joinToString(",","[","]") { canonical(v.get(it)) }
            null, JSONObject.NULL -> "null"
            is String -> JSONObject.quote(v)
            else -> v.toString()
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
