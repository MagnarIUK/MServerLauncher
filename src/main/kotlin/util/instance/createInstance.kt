package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.data.configs.Loader
import com.magnariuk.data.configs.Version
import com.magnariuk.util.api.checkMinecraftVersion
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.worldApi.resetWorld
import com.magnariuk.util.prompt
import com.magnariuk.util.t
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.text.trim

private val json = Json { prettyPrint = true }

fun createInstance(
    name: String,
    version: String,
    memory: String,
    autoBackup: Boolean = false,
    resourcePack: String = "",
    resourcePackPort: Int = 2548,
    loader: String = "vanilla",
    loaderVersion: String = "latest",
    runServer: Boolean = false,
    apiMode: Boolean = false
): Boolean {
    val config = readConfig()
    val instancePath = Path.of(config.instancesFolder, name)
    Files.createDirectories(instancePath)

    val validLoaders = setOf("vanilla", "fabric")
    if (loader !in validLoaders) {
        if(!apiMode) println(t("instance.notValidLoader", listOf(loader)))
        return false
    }

    if (!checkMinecraftVersion(version)) {
        if(!apiMode) println(t("instance.notValidVersion", listOf(version)))
        return false
    }

    val instanceCfg = INSTANCE_CONFIG(
        name = name,
        memory = memory,
        autoBackup = autoBackup,
        resourcepack = resourcePack,
        resourcepackPort = 2054,
        version = Version(
            minecraft = version,
            loader = Loader(type = loader, version = loaderVersion)
        )
    )

    val cfgFile = instancePath.resolve("cfg.json").toFile()
    cfgFile.writeText(json.encodeToString(instanceCfg))

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

    if(!apiMode)println(t("instance.created", listOf(name, loader, version, memory, if (autoBackup) "Enabled" else "Disabled")))
    if (resourcePack.isNotEmpty()) {
        if(!apiMode) println(t("instance.createdRp", listOf(Path.of(resourcePack).fileName)))
    }
    if(!apiMode){
        runBlocking {
            val delete = prompt(t("prompts.deleteWorldAfterInitializingProperties"))
            launchServer(name, exitImmediately = true)
            if (delete== "y") {
                resetWorld(name)
            }
        }
    }
    if(runServer){
        runBlocking {
            launchServer(name, exitImmediately = true)
        }
    }

    return true
}

