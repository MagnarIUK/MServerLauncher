package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.Table
import com.magnariuk.util.configs.readConfig
import java.nio.file.Path

fun listInstances(): List<Pair<String, INSTANCE_CONFIG>> {
    val config = readConfig()
    val instancesPath = Path.of(config.instancesFolder)
    instancesPath.toFile().mkdirs()

    val foundInstances = mutableListOf<Pair<String, INSTANCE_CONFIG>>()

    instancesPath.toFile().listFiles()?.forEach { instanceDir ->
        if (instanceDir.isDirectory) {
            val cfgFile = instanceDir.resolve("cfg.json")
            if (cfgFile.exists()) {
                try {
                    val inst = getInstance(instanceDir.name)
                    if (inst != null) {
                        foundInstances.add(instanceDir.name to inst)
                    }
                } catch (e: Exception) {
                    println("Warning: An error occurred while processing instance '${instanceDir.name}': ${e.message}. Skipping.")
                }
            }
        }
    }

    if (foundInstances.isNotEmpty()) {
        val title = "--- Available Minecraft Server Instances ---"
        val columns = listOf("Name" to 20, "Loader" to 15, "Version" to 20, "Memory" to 10)
        val table = Table(title, columns)

        table.printHeader()
        for ((name, instance) in foundInstances) {
            table.printRow(
                listOf(
                    name,
                    instance.version.loader.type,
                    getVersionNameDownload(instance.version.minecraft),
                    instance.memory
                )
            )
        }
        table.printClosing()
    } else {
        println("No Minecraft server instances found.")
    }

    return foundInstances
}

fun getVersionNameDownload(ver: String): String {
    return when (ver.lowercase()) {
        "l", "latest" -> "latest version"
        "s", "snapshot" -> "latest snapshot"
        else -> ver
    }
}
