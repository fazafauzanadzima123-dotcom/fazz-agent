package com.orbit.agent.core.tools

import com.orbit.agent.core.common.array
import com.orbit.agent.core.common.obj
import com.orbit.agent.core.common.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Validates model-supplied arguments against a tool's declared schema (the subset we use). */
object SchemaValidator {

    fun validate(schema: JsonObject, args: JsonObject): String? {
        val props = schema.obj("properties") ?: JsonObject(emptyMap())
        val required = schema.array("required")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

        for (key in required) {
            val v = args[key]
            if (v == null || v is JsonNull) return "missing required argument '$key'"
        }
        for ((key, value) in args) {
            val propSchema = props[key] as? JsonObject ?: return "unexpected argument '$key'"
            if (value is JsonNull) continue
            checkValue(key, propSchema, value)?.let { return it }
        }
        return null
    }

    private fun checkValue(key: String, schema: JsonObject, value: JsonElement): String? {
        val type = schema.string("type")?.lowercase()
        val prim = value as? JsonPrimitive
        when (type) {
            "string" -> {
                if (prim == null || !prim.isString) return "'$key' must be a string"
                val s = prim.content
                schema.longValue("maxLength")?.let { if (s.length > it) return "'$key' is too long (max $it)" }
                schema.longValue("minLength")?.let { if (s.length < it) return "'$key' is too short (min $it)" }
                schema.array("enum")?.let { e ->
                    val allowed = e.mapNotNull { (it as? JsonPrimitive)?.content }
                    if (s !in allowed) return "'$key' must be one of $allowed"
                }
            }
            "integer" -> {
                val n = prim?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.takeIf { d -> d % 1.0 == 0.0 }?.toLong() }
                    ?: return "'$key' must be an integer"
                schema.longValue("minimum")?.let { if (n < it) return "'$key' must be >= $it" }
                schema.longValue("maximum")?.let { if (n > it) return "'$key' must be <= $it" }
            }
            "number" -> {
                val n = prim?.takeIf { !it.isString }?.doubleOrNull ?: return "'$key' must be a number"
                schema.longValue("minimum")?.let { if (n < it) return "'$key' must be >= $it" }
                schema.longValue("maximum")?.let { if (n > it) return "'$key' must be <= $it" }
            }
            "boolean" -> if (prim?.takeIf { !it.isString }?.booleanOrNull == null) return "'$key' must be a boolean"
            "array" -> if (value !is JsonArray) return "'$key' must be an array"
            "object" -> if (value !is JsonObject) return "'$key' must be an object"
        }
        return null
    }

    private fun JsonObject.longValue(k: String): Long? =
        (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
}
