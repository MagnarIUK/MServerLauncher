package com.magnariuk.util.instance.backupApi

import com.magnariuk.util.Table
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import java.nio.file.Path
import kotlin.collections.iterator

fun listBackups(instanceName: String, printHeader: Boolean = true) {
    val instanceCfg = getInstance(instanceName)
    if (instanceCfg == null) {
        println("Instance '$instanceName' not found.")
        return
    }

    val instancePath = Path.of(readConfig().instancesFolder, instanceName)
    val backupsPath = instancePath.resolve("backups").toFile()

    val backups = instanceCfg.backups.toMutableMap()
    if (backups.isEmpty()) {
        println("\nNo backups found for instance '$instanceName'.")
        return
    }

    val title = "--- Backups for $instanceName ---"
    val columns = listOf("ID" to 10, "Date/Time" to 20, "Version" to 10, "Description" to 40)
    val table = Table(title, columns)
    if (printHeader) table.printHeader()

    val removed = mutableListOf<String>()

    for ((backupId, info) in backups) {
        val ts = info.dateTime.replace(".", "").replace(":", "")
        val backupFileName = "$ts-world-backup.zip"
        val backupFile = backupsPath.resolve(backupFileName)

        if (!backupFile.exists()) {
            println("Missing backup $backupId ($backupFileName), removing from config...")
            removed.add(backupId)
            continue
        }

        table.printRow(listOf(backupId, info.dateTime, info.version, info.desc))
    }

    if (removed.isNotEmpty()) {
        removed.forEach { backups.remove(it) }
        editInstance(instanceName, backups = backups)
    }

    table.printClosing()
}
