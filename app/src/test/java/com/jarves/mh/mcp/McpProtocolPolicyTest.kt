package com.jarves.mh.mcp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class McpProtocolPolicyTest {
    @Test fun headerValuesCannotInjectHeaders() {
        for (value in listOf("value\r\nAuthorization: secret", "نص عربي", " padded ", "=?base64?YWJj?=")) {
            val encoded=McpProtocolPolicy.headerValue(value)
            assertTrue(encoded.startsWith("=?base64?"));assertFalse(encoded.contains('\r'));assertFalse(encoded.contains('\n'))
        }
        assertEquals("us-west1",McpProtocolPolicy.headerValue("us-west1"))
    }
    @Test fun rejectsUnsafeAndDuplicateAnnotations() {
        val schema=JSONObject("""{"properties":{"a":{"type":"string","x-mcp-header":"X\r\nAuth"}}}""")
        assertTrue(runCatching { McpProtocolPolicy.mirroredHeaders(schema,JSONObject()) }.isFailure)
        val duplicate=JSONObject("""{"properties":{"a":{"type":"string","x-mcp-header":"Region"},"b":{"type":"string","x-mcp-header":"region"}}}""")
        assertTrue(runCatching { McpProtocolPolicy.mirroredHeaders(duplicate,JSONObject()) }.isFailure)
        val unreachable=JSONObject("""{"items":{"type":"string","x-mcp-header":"X"}}""")
        assertTrue(runCatching { McpProtocolPolicy.mirroredHeaders(unreachable,JSONObject()) }.isFailure)
    }
    @Test fun mirrorsNestedValuesAndCanonicalizesDefinitions() {
        val schema=JSONObject("""{"properties":{"settings":{"properties":{"region":{"type":"string","x-mcp-header":"Region"}}}}}""")
        assertEquals(mapOf("Mcp-Param-Region" to "east"),McpProtocolPolicy.mirroredHeaders(schema,JSONObject("""{"settings":{"region":"east"}}""")))
        assertEquals(McpProtocolPolicy.fingerprint(JSONObject("""{"a":1,"b":2}""")),McpProtocolPolicy.fingerprint(JSONObject("""{"b":2,"a":1}""")))
    }
}
