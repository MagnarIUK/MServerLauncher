package com.magnariuk.util.configs

import kotlinx.serialization.json.Json
import java.nio.file.Path

private val configPath: Path = Path.of(System.getProperty("user.home"))
    .resolve(".minecraft/server_instances/config.json")

fun editGlobalConfig(key: String? = null, value: Any? = null) {
    val configFile = configPath.toFile()
    val json = Json { ignoreUnknownKeys = true }

    val currentConfig: MutableMap<String, Any> = json.decodeFromString<Map<String, Any>>(configFile.readText()).toMutableMap()

    var updated = false
    if (key != null && value != null) {
        if (!currentConfig.containsKey(key)) {
            println("Warning: Key '$key' not found in current config. Skipping.")
        } else {
            currentConfig[key] = value
            updated = true
        }
    }

    if (updated) {
        configFile.writeText(json.encodeToString(currentConfig))
        println("Global configuration updated successfully.")
    } else {
        println("No global configuration settings provided to update.")
    }
}
