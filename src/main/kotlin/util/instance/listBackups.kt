package com.magnariuk.util.instance

import com.magnariuk.util.Table
import com.magnariuk.util.configs.readConfig
import java.nio.file.Path

fun listBackups(instanceName: String) {
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

    table.printHeader()

    val removed = mutableListOf<String>()

    for ((backupId, info) in backups) {
        val ts = info.dateTime.replace(".", "").replace(":", "")
        val backupName = "$ts-world-backup.zip"
        val backupFile = backupsPath.resolve(backupName)

        if (!backupFile.exists()) {
            println("Missing backup $backupId ($backupName), removing from config...")
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
