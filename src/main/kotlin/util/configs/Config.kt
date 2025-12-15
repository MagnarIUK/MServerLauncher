package com.magnariuk.util.configs

import com.magnariuk.configFilePath
import com.magnariuk.data.configs.CONFIG
import com.magnariuk.util.t
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

fun writeConfig(cfg: CONFIG) {
    Files.createDirectories(configFilePath.parent)
    configFilePath.writeText(json.encodeToString(cfg))
}

fun readConfig(): CONFIG {
    val defaults = CONFIG()
    val oldConfig = (configFilePath.parent / "config.json").toFile()
    if(oldConfig.exists()) {
        oldConfig.renameTo(configFilePath.toFile())
    }


    if (Files.exists(configFilePath)) {
        val content = configFilePath.readText()
        val loaded = try {
            json.decodeFromString<CONFIG>(content)
        } catch (e: Exception) {
            println(t("util.primitives.configOpeningError", e.message))
            defaults
        }

        val merged = mergeWithDefaults(loaded)

        writeConfig(merged)

        return merged
    } else {
        writeConfig(defaults)
        return defaults
    }
}

private fun mergeWithDefaults(cfg: CONFIG): CONFIG {
    val defaults = CONFIG()
    return CONFIG(
        instancesFolder = cfg.instancesFolder.ifBlank { defaults.instancesFolder },
        api = cfg.api.ifBlank { defaults.api },
        apiLogin = cfg.apiLogin.ifBlank { defaults.apiLogin },
        apiPassword = cfg.apiPassword.ifBlank { defaults.apiPassword },
        backupOnRollback = cfg.backupOnRollback,
        launched = cfg.launched.ifEmpty { defaults.launched },
        defaultEditor = cfg.defaultEditor.ifBlank { defaults.defaultEditor },
        showFullProgress = cfg.showFullProgress,
        lang = cfg.lang,
        checkUpdateInterval = cfg.checkUpdateInterval,
        logMaxLines = cfg.logMaxLines,
        exec = cfg.exec,
    )
}
