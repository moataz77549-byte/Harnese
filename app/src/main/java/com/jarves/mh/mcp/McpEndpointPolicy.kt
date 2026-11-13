package com.jarves.mh.mcp

import java.net.URI
import java.net.URLDecoder

/** Provider routing belongs in the URL; credentials belong in the encrypted bearer field. */
object McpEndpointPolicy {
    fun validate(endpoint: String): URI {
        val uri = URI(endpoint.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "Use an HTTPS endpoint without credentials or a fragment"
        }
        val seen = mutableSetOf<String>()
        uri.rawQuery?.split('&')?.forEach { parameter ->
            val pair = parameter.split('=', limit = 2)
            require(pair.size == 2) { "MCP query parameters must have a value" }
            val name = URLDecoder.decode(pair[0], "UTF-8")
            val value = URLDecoder.decode(pair[1], "UTF-8")
            require(seen.add(name)) { "Duplicate MCP query parameter" }
            val valid = when (name) {
                "project_ref", "branch_id" -> value.matches(Regex("[A-Za-z0-9_-]{1,128}"))
                "read_only" -> value in setOf("true", "false")
                "features" -> value.matches(Regex("[a-z_]+(,[a-z_]+){0,30}"))
                else -> false
            }
            require(valid) { "Only project_ref, branch_id, read_only and features are supported in MCP URLs; use the bearer field for credentials" }
        }
        return uri
    }
}
