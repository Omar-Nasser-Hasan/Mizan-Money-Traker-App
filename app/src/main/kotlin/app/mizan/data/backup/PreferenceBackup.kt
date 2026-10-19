// Copyright (c) 2026 Mizan contributors
package app.mizan.data.backup

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Preferences travel only inside the authenticated encrypted archive. Device Keystore blobs never travel. */
object PreferenceBackup {
    private const val DEVICE_SECRET = "backup_passphrase_keystore_wrapped"

    fun encode(preferences: Preferences): ByteArray =
        JsonObject(
            preferences
                .asMap()
                .filterKeys {
                    it.name != DEVICE_SECRET && !it.name.contains("keystore", ignoreCase = true)
                }.mapKeys { it.key.name }
                .mapValues { (_, value) ->
                    val type =
                        when (value) {
                            is Boolean -> "boolean"
                            is Int -> "int"
                            is Long -> "long"
                            is Float -> "float"
                            is Double -> "double"
                            is Set<*> -> "set"
                            is String -> "string"
                            else -> error("Unsupported preference")
                        }
                    val encodedValue =
                        if (value is Set<*>) {
                            JsonArray(value.map { JsonPrimitive(it as String) })
                        } else {
                            JsonPrimitive(value.toString())
                        }
                    JsonObject(mapOf("type" to JsonPrimitive(type), "value" to encodedValue))
                },
        ).toString().encodeToByteArray()

    fun decode(bytes: ByteArray): Preferences {
        val result = mutablePreferencesOf()
        Json.parseToJsonElement(bytes.decodeToString()).jsonObject.forEach { (name, item) ->
            require(!name.contains("keystore", ignoreCase = true))
            val objectValue = item.jsonObject
            val value = objectValue.getValue("value")
            when (objectValue.getValue("type").jsonPrimitive.content) {
                "boolean" -> result[booleanPreferencesKey(name)] = value.jsonPrimitive.content.toBooleanStrict()
                "int" -> result[intPreferencesKey(name)] = value.jsonPrimitive.content.toInt()
                "long" -> result[longPreferencesKey(name)] = value.jsonPrimitive.content.toLong()
                "float" -> result[floatPreferencesKey(name)] = value.jsonPrimitive.content.toFloat()
                "double" -> result[doublePreferencesKey(name)] = value.jsonPrimitive.content.toDouble()
                "string" -> result[stringPreferencesKey(name)] = value.jsonPrimitive.content
                "set" -> result[stringSetPreferencesKey(name)] = (value as JsonArray).map { it.jsonPrimitive.content }.toSet()
                else -> error("Unsupported preference type")
            }
        }
        return result
    }
}
