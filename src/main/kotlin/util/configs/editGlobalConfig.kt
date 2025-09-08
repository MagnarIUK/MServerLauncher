package com.magnariuk.util.configs

import com.magnariuk.configPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*


fun editGlobalConfig(key: String? = null, value: String? = null) {
    val configFile = configPath.toFile()
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val currentConfig = json.parseToJsonElement(configFile.readText()).jsonObject.toMutableMap()

    var updated = false
    if (key != null && value != null) {
        if (!currentConfig.containsKey(key)) {
            println("Warning: Key '$key' not found in current config. Skipping.")
        } else {
            val parsedValue: JsonElement = try {
                json.parseToJsonElement(value)
            } catch (e: Exception) {
                JsonPrimitive(value)
            }
            currentConfig[key] = parsedValue
            updated = true
        }
    }

    if (updated) {
        val newJson = JsonObject(currentConfig)
        configFile.writeText(json.encodeToString(newJson))
        println("Global configuration updated successfully.")
    } else {
        println("No global configuration settings provided to update.")
    }
}
