package com.fazz.agent.core.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Tiny builder for Gemini function-declaration parameter schemas. */
class SchemaBuilder {
    private val props = linkedMapOf<String, JsonElement>()
    private val required = mutableListOf<String>()

    fun string(name: String, description: String, required: Boolean = false, maxLength: Int? = null, enum: List<String>? = null) {
        val m = linkedMapOf<String, JsonElement>("type" to JsonPrimitive("string"), "description" to JsonPrimitive(description))
        maxLength?.let { m["maxLength"] = JsonPrimitive(it.toString()) }
        enum?.let { e -> m["enum"] = JsonArray(e.map { JsonPrimitive(it) }) }
        add(name, JsonObject(m), required)
    }

    fun integer(name: String, description: String, required: Boolean = false, min: Long? = null, max: Long? = null) {
        val m = linkedMapOf<String, JsonElement>("type" to JsonPrimitive("integer"), "description" to JsonPrimitive(description))
        min?.let { m["minimum"] = JsonPrimitive(it) }
        max?.let { m["maximum"] = JsonPrimitive(it) }
        add(name, JsonObject(m), required)
    }

    fun boolean(name: String, description: String, required: Boolean = false) =
        add(name, JsonObject(mapOf("type" to JsonPrimitive("boolean"), "description" to JsonPrimitive(description))), required)

    private fun add(name: String, schema: JsonObject, req: Boolean) {
        props[name] = schema
        if (req) required += name
    }

    fun build(): JsonObject {
        val m = linkedMapOf<String, JsonElement>("type" to JsonPrimitive("object"), "properties" to JsonObject(props))
        if (required.isNotEmpty()) m["required"] = JsonArray(required.map { JsonPrimitive(it) })
        return JsonObject(m)
    }
}

fun schema(block: SchemaBuilder.() -> Unit = {}): JsonObject = SchemaBuilder().apply(block).build()
