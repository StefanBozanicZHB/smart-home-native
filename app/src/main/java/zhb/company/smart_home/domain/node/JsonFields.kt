package zhb.company.smart_home.domain.node

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Lenient reading of the nodes' JSON, shared by every parser: a payload that is not a JSON object,
 * a missing field or a field of the wrong type gives null, never an exception. Numbers and booleans
 * sent as strings ("21.5", "true") are not accepted, so a firmware bug shows up as a missing value.
 */

private val nodeJson = Json { ignoreUnknownKeys = true }

/** The payload as a JSON object, or null when it is not one (empty, malformed, an array, ...). */
internal fun parseJsonObject(payload: String): JsonObject? =
    runCatching { nodeJson.parseToJsonElement(payload) }.getOrNull() as? JsonObject

/** A primitive field; null when missing, JSON `null`, an object or an array. */
internal fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.string(key: String): String? = primitive(key)?.takeIf { it.isString }?.content

internal fun JsonObject.bool(key: String): Boolean? = primitive(key)?.takeUnless { it.isString }?.booleanOrNull

internal fun JsonObject.int(key: String): Int? = primitive(key)?.takeUnless { it.isString }?.intOrNull

internal fun JsonObject.long(key: String): Long? = primitive(key)?.takeUnless { it.isString }?.longOrNull

internal fun JsonObject.double(key: String): Double? = primitive(key)?.takeUnless { it.isString }?.doubleOrNull

/** A list of strings; null when any element is not a string. */
internal fun JsonObject.strings(key: String): List<String>? =
    (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }

/** A list of integers; null when any element is not an integer. */
internal fun JsonObject.ints(key: String): List<Int>? =
    (this[key] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull ?: return null }
