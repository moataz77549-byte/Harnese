package com.jarves.mh.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class McpEndpointPolicyTest {
    @Test fun permitsScopedReadOnlyProviderEndpoint() {
        val endpoint = "https://mcp.supabase.com/mcp?project_ref=abc123&read_only=true&features=database,docs"
        assertEquals(endpoint, McpEndpointPolicy.validate(endpoint).toString())
    }
    @Test fun rejectsCredentialsAndAmbiguousRouting() {
        listOf(
            "https://user:password@example.com/mcp",
            "https://example.com/mcp?access_token=secret",
            "https://example.com/mcp?%61pi_key=secret",
            "https://example.com/mcp?read_only=true&read_only=false",
            "https://example.com/mcp?project_ref=abc%0Apassword",
            "http://example.com/mcp",
        ).forEach { endpoint -> assertThrows(IllegalArgumentException::class.java) { McpEndpointPolicy.validate(endpoint) } }
    }
}
