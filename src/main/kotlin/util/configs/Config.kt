package com.magnariuk.util.configs

import com.magnariuk.data.configs.CONFIG
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val configPath: Path = Path.of(System.getProperty("user.home"))
    .resolve(".minecraft/server_instances/config.json")
private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun writeConfig(cfg: CONFIG) {
    Files.createDirectories(configPath.parent)
    configPath.writeText(json.encodeToString(cfg))
}

fun readConfig(): CONFIG {
    if (Files.exists(configPath)) {
        val content = configPath.readText()
        return try {
            val loaded = json.decodeFromString<CONFIG>(content)
            mergeWithDefaults(loaded)
        } catch (e: Exception) {
            println("Error reading config: ${e.message}, falling back to defaults.")
            CONFIG()
        }
    }
    return CONFIG().also { writeConfig(it) }
}
private fun mergeWithDefaults(cfg: CONFIG): CONFIG {
    return CONFIG(
        instancesFolder = cfg.instancesFolder.ifBlank { CONFIG().instancesFolder },
        api = cfg.api,
        apiLogin = cfg.apiLogin,
        apiPassword = cfg.apiPassword,
        backupOnRollback = cfg.backupOnRollback,
        launched = cfg.launched
    )
}
