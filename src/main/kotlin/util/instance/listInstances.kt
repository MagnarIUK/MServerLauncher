package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.Table
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.t
import java.nio.file.Path

fun listInstances(apiMode: Boolean = false ): List<Pair<String, INSTANCE_CONFIG>> {
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
                    println(t("command.list.subs.errorProcessing", listOf(instanceDir.name, e.message)))
                }
            }
        }
    }

    if (foundInstances.isNotEmpty()) {
        if (!apiMode) {
            val title = t("command.list.subs.table.title")
            val columns = listOf(
                t("command.list.subs.table.name") to 20,
                t("command.list.subs.table.loader") to 15,
                t("command.list.subs.table.version") to 20,
                t("command.list.subs.table.memory") to 10)
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
        }
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
