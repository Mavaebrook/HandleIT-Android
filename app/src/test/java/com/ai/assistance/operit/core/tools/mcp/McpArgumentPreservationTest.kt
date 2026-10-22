package com.ai.assistance.operit.core.tools.mcp

import com.ai.assistance.operit.data.mcp.plugins.toMcpBridgeArguments
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class McpArgumentPreservationTest {
    @Test
    fun `declared strings survive conversion and transport serialization exactly`() {
        val values = listOf(
            "00123", "true", "false", "[1,null]", "{\"id\":123}",
            "  text with whitespace\n", "界🙂é".repeat(3000)
        )
        val tool = toolWithProperties("""{"value":{"type":"string"}}""")

        values.forEach { value ->
            val converted = MCPToolExecutor.convertParameterTypes(mapOf("value" to value), tool)
            val payload = roundTrip(converted)

            assertTrue(converted.getValue("value") is String)
            assertArrayEquals(value.toByteArray(Charsets.UTF_8), payload.getString("value").toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun `JSON containers retain literal types and null positions in transport payload`() {
        val tool = toolWithProperties("""{"array":{"type":"array"},"object":{"type":"object"}}""")
        val converted = MCPToolExecutor.convertParameterTypes(
            mapOf(
                "array" to """[null,"00123","true","[1]","{\"x\":1}",7,false,{"id":"09","missing":null},[null,"false"],null]""",
                "object" to """{"id":"00123","enabled":"true","missing":null,"nested":{"text":"[1]"},"items":[null,"12",3]}"""
            ),
            tool
        )
        val arrayValues = converted.getValue("array") as List<*>
        val objectValues = converted.getValue("object") as Map<*, *>
        assertEquals(10, arrayValues.size)
        assertEquals(null, arrayValues[0])
        assertEquals(null, arrayValues[9])
        assertTrue(objectValues.containsKey("missing"))
        assertEquals(null, objectValues["missing"])

        val payload = roundTrip(converted)
        val array = payload.getJSONArray("array")
        assertEquals(10, array.length())
        assertTrue(array.isNull(0))
        assertEquals("00123", array.get(1))
        assertEquals("true", array.get(2))
        assertEquals("[1]", array.get(3))
        assertEquals("{\"x\":1}", array.get(4))
        assertEquals(7, array.getInt(5))
        assertFalse(array.getBoolean(6))
        assertEquals("09", array.getJSONObject(7).get("id"))
        assertTrue(array.getJSONObject(7).isNull("missing"))
        assertTrue(array.getJSONArray(8).isNull(0))
        assertEquals("false", array.getJSONArray(8).get(1))
        assertTrue(array.isNull(9))

        val obj = payload.getJSONObject("object")
        assertEquals("00123", obj.get("id"))
        assertEquals("true", obj.get("enabled"))
        assertTrue(obj.has("missing"))
        assertTrue(obj.isNull("missing"))
        assertEquals("[1]", obj.getJSONObject("nested").get("text"))
        assertTrue(obj.getJSONArray("items").isNull(0))
        assertEquals("12", obj.getJSONArray("items").get(1))
        assertEquals(3, obj.getJSONArray("items").getInt(2))
    }

    @Test
    fun `structured arguments are preserved without recursively guessing list elements`() {
        val list = listOf("00123", "true", null, mapOf("id" to "007", "missing" to null))
        val obj = mapOf("items" to list)
        val tool = toolWithProperties("""{"list":{"type":"array"},"object":{"type":"object"}}""")
        val converted = MCPToolExecutor.convertParameterTypes(mapOf("list" to list, "object" to obj), tool)

        assertSame(list, converted.getValue("list"))
        assertSame(obj, converted.getValue("object"))
        val payload = roundTrip(converted)
        assertEquals("00123", payload.getJSONArray("list").get(0))
        assertEquals("true", payload.getJSONObject("object").getJSONArray("items").get(1))
        assertTrue(payload.getJSONArray("list").isNull(2))
    }

    @Test
    fun `absent unknown and union schema types do not infer values from contents`() {
        val parameters = mapOf("absent" to "00123", "unknown" to "true", "union" to "[1]", "empty" to "{\"x\":1}")
        val tool = toolWithProperties("""{"unknown":{"type":"custom"},"union":{"type":["string","array"]},"empty":{}}""")

        assertEquals(parameters, MCPToolExecutor.convertParameterTypes(parameters, tool))
        assertEquals(parameters, MCPToolExecutor.convertParameterTypes(parameters, null))
    }

    @Test
    fun `explicit scalar types still convert valid values`() {
        val tool = toolWithProperties("""{"count":{"type":"integer"},"amount":{"type":"number"},"enabled":{"type":"boolean"},"disabled":{"type":"boolean"}}""")
        val converted = MCPToolExecutor.convertParameterTypes(
            mapOf("count" to "12", "amount" to "1.5", "enabled" to "true", "disabled" to "false"), tool
        )

        assertEquals(12, converted["count"])
        assertEquals(1.5, converted["amount"])
        assertEquals(true, converted["enabled"])
        assertEquals(false, converted["disabled"])
    }

    @Test
    fun `malformed values are not repaired or silently converted to false`() {
        val parameters = mapOf("array" to "[1, {]", "object" to "{", "boolean" to "not-a-boolean", "integer" to "not-a-number")
        val tool = toolWithProperties("""{"array":{"type":"array"},"object":{"type":"object"},"boolean":{"type":"boolean"},"integer":{"type":"integer"}}""")

        assertEquals(parameters, MCPToolExecutor.convertParameterTypes(parameters, tool))
    }

    @Test
    fun `parameter instance uses the same literal preservation rules`() {
        val parameter = MCPToolParameter("value", "array", "")
        assertEquals(listOf("00123", null, "true"), parameter.convertParameterValue("""["00123",null,"true"]"""))
    }

    private fun toolWithProperties(properties: String) = McpRuntimeTool(
        name = "write", description = "", inputSchema = """{"type":"object","properties":$properties}"""
    )

    // Exercise the actual bridge serializer after the executor's production conversion boundary.
    private fun roundTrip(arguments: Map<String, Any>): JSONObject =
        JSONObject(arguments.toMcpBridgeArguments().toString())
}
