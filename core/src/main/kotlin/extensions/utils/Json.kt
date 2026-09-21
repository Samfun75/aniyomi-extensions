package extensions.utils

// From https://github.com/keiyoushi/extensions-source/blob/main/core/src/main/kotlin/keiyoushi/utils/Json.kt

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Parses JSON string into an object of type [T].
 */
context(source: Source)
inline fun <reified T> String.parseAs(): T = source.json.decodeFromString(this)

/**
 * Parses the response body into an object of type [T].
 */
context(source: Source)
inline fun <reified T> Response.parseAs(): T = parseAs(source.json)

/**
 * Serializes the object to a JSON string.
 */
context(source: Source)
inline fun <reified T> T.toJsonString(): String = source.json.encodeToString(this)

/**
 * Converts a string into a JSON request body.
 */
fun String.toJsonBody(): RequestBody = this.toRequestBody("application/json; charset=utf-8".toMediaType())

/**
 * Converts the object to a JSON request body.
 */
context(source: Source)
inline fun <reified T> T.toRequestBody(): RequestBody = this.toJsonString().toJsonBody()

/**
 * Parses the response body into an object of type [T] with an explicit [json], for code
 * that has no [Source] in scope.
 */
inline fun <reified T> Response.parseAs(json: Json): T = use {
    json.decodeFromBufferedSource(serializer(), it.body.source())
}

/**
 * Parses the response body into an object of type [T] after rewriting it with [transform].
 */
inline fun <reified T> Response.parseAs(json: Json, transform: (String) -> String): T = use { json.decodeFromString(transform(it.body.string())) }

/**
 * Parses JSON string into an object of type [T] with an explicit [json].
 */
inline fun <reified T> String.parseAs(json: Json): T = json.decodeFromString(this)
