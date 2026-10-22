package com.ai.assistance.operit.core.tools.mcp

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Represents a parameter for an MCP tool */
@Serializable
data class MCPToolParameter(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = false,
        val defaultValue: String? = null
) {
    /** Convert only a string value whose schema explicitly requests another type. */
    fun convertParameterValue(value: Any): Any = smartConvert(value, type)

    companion object {
        /**
         * Convert a top-level string according to one explicit schema type.
         *
         * Unknown types and already structured values retain their original representation.
         * JSON strings inside containers are literals, not another set of parameters to infer.
         */
        fun smartConvert(value: Any, typeName: String?): Any {
            if (value !is String) return value

            return when (typeName?.lowercase()) {
                "number" -> {
                    try {
                        if (value.contains(".")) value.toDouble() else value.toLong()
                    } catch (e: NumberFormatException) {
                        value
                    }
                }
                "boolean" -> when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> value
                }
                "integer" -> {
                    try {
                        value.toInt()
                    } catch (e: NumberFormatException) {
                        value
                    }
                }
                "float", "double" -> {
                    try {
                        value.toDouble()
                    } catch (e: NumberFormatException) {
                        value
                    }
                }
                "array" -> parseArray(value)
                "object" -> parseObject(value)
                else -> value
            }
        }

        private fun parseArray(value: String): Any = try {
            toPlainValue(JSONArray(value.trim()))!!
        } catch (e: JSONException) {
            value
        }

        private fun parseObject(value: String): Any = try {
            toPlainValue(JSONObject(value.trim()))!!
        } catch (e: JSONException) {
            value
        }

        // Reinterpret neither quoted strings nor nulls: dropping an array null shifts arguments,
        // and guessing the type of a quoted numeric identifier changes the remote write payload.
        private fun toPlainValue(value: Any?): Any? = when (value) {
            null, JSONObject.NULL -> null
            is JSONArray -> List(value.length()) { index -> toPlainValue(value.get(index)) }
            is JSONObject -> {
                val result = linkedMapOf<String, Any?>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = toPlainValue(value.get(key))
                }
                result
            }
            else -> value
        }
    }
}
