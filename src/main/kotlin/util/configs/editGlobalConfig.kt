package com.magnariuk.util.configs

import com.magnariuk.configFilePath
import com.magnariuk.util.t
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*


fun editGlobalConfig(key: String? = null, value: String? = null) {
    val configFile = configFilePath.toFile()
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val currentConfig = json.parseToJsonElement(configFile.readText()).jsonObject.toMutableMap()

    var updated = false
    if (key != null && value != null) {
        if (!currentConfig.containsKey(key)) {
            println(t("util.primitives.keyNotFound", key))
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
        println(t("util.primitives.updatedGlobal"))
    } else {
        println(t("util.primitives.noSettingsProvided"))
    }
}
