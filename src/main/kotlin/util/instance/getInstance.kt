package com.magnariuk.util.instance

import com.magnariuk.data.configs.*
import com.magnariuk.util.configs.readConfig

import kotlinx.serialization.json.Json
import java.nio.file.Path

fun getInstance(name: String): INSTANCE_CONFIG? {
    val cfg = readConfig()
    val instancesPath = Path.of(cfg.instancesFolder)
    val instanceConfigPath = instancesPath.resolve(name).resolve("cfg.json")

    if (!instanceConfigPath.toFile().exists()) return null

    val text = instanceConfigPath.toFile().readText()
    val instanceCfg: INSTANCE_CONFIG = Json.decodeFromString(INSTANCE_CONFIG.serializer(), text)
    return instanceCfg
}
