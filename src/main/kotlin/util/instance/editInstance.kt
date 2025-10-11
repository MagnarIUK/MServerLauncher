package com.magnariuk.util.instance

import com.magnariuk.data.configs.Backup
import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.t
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

fun editInstance(
    name: String,
    version: String? = null,
    memory: String? = null,
    autoBackup: Boolean? = null,
    resourcepack: String? = null,
    resourcepackPort: Int? = null,
    backups: Map<String, Backup>? = null,
    loaderType: String? = null,
    loaderVersion: String? = null,
    isInternal: Boolean = false,
) {
    val config = readConfig()
    val instancePath = Path.of(config.instancesFolder, name)
    val cfgFile = instancePath.resolve("cfg.json").toFile()

    if (!cfgFile.exists()) {
        throw FileNotFoundException(t("command.edit.subs.configNotFoundError", listOf(cfgFile.absolutePath)))
    }

    var instanceCfg = Json.decodeFromString<INSTANCE_CONFIG>(cfgFile.readText())

    instanceCfg = instanceCfg.copy(
        memory = memory ?: instanceCfg.memory,
        autoBackup = autoBackup ?: instanceCfg.autoBackup,
        backups = backups ?: instanceCfg.backups,
        version = instanceCfg.version.copy(
            minecraft = version ?: instanceCfg.version.minecraft,
            loader = instanceCfg.version.loader.copy(
                type = loaderType ?: instanceCfg.version.loader.type,
                version = loaderVersion ?: instanceCfg.version.loader.version
            )
        ),
        resourcepackPort = resourcepackPort ?: instanceCfg.resourcepackPort,
    )

    resourcepack?.let {
        if (!isInternal) println(t("command.edit.subs.useAttach"))
        else{
            instanceCfg.resourcepack = resourcepack
        }
    }

    Files.createDirectories(instancePath)
    cfgFile.writeText(Json { prettyPrint = true }.encodeToString(instanceCfg))

    println(t("command.edit.subs.updated", listOf(name)))
}
