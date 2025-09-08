package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.data.configs.Loader
import com.magnariuk.data.configs.Version
import com.magnariuk.util.api.checkMinecraftVersion
import com.magnariuk.util.configs.readConfig
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

fun createInstance(
    name: String,
    version: String,
    memory: String,
    autoBackup: Boolean = false,
    resourcePack: String = "",
    resourcePackPort: Int = 2548,
    loader: String = "vanilla",
    loaderVersion: String = "latest"
): Boolean {
    val config = readConfig()
    val instancePath = Path.of(config.instancesFolder, name)
    Files.createDirectories(instancePath)

    val validLoaders = setOf("vanilla", "fabric")
    if (loader !in validLoaders) {
        println("$loader is not a valid loader.")
        return false
    }

    if (!checkMinecraftVersion(version)) {
        println("$version is not a valid Minecraft version.")
        return false
    }

    val instanceCfg = INSTANCE_CONFIG(
        name = name,
        memory = memory,
        autoBackup = autoBackup,
        resourcepack = resourcePack,
        resourcepackPort = resourcePackPort,
        version = Version(
            minecraft = version,
            loader = Loader(type = loader, version = loaderVersion)
        )
    )

    val cfgFile = instancePath.resolve("cfg.json").toFile()
    cfgFile.writeText(Json { prettyPrint = true }.encodeToString(instanceCfg))

    val eulaFile = instancePath.resolve("eula.txt").toFile()
    val spFile = instancePath.resolve("server.properties").toFile()
    if (!spFile.exists()) {
        spFile.createNewFile()
    }

    if (!eulaFile.exists()) {
        eulaFile.writeText("eula=true\n")
    } else {
        val lines = eulaFile.readLines().toMutableList()
        var updated = false
        for (i in lines.indices) {
            if (lines[i].trim() == "eula=false") {
                lines[i] = "eula=true"
                updated = true
            }
        }
        if (!updated && lines.none { it.trim() == "eula=true" }) {
            lines.add("eula=true")
        }
        eulaFile.writeText(lines.joinToString("\n") + "\n")
    }

    println("Instance '$name' created successfully.")
    println("  Version: $loader $version")
    println("  Memory: $memory")
    println("  Auto-backup: ${if (autoBackup) "Enabled" else "Disabled"}")
    if (resourcePack.isNotEmpty()) {
        println("  Resource pack: '${Path.of(resourcePack).fileName}' on port $resourcePackPort")
    }

    return true
}

