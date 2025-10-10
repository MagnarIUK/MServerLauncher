package com.magnariuk.util.instance.backupApi

import com.magnariuk.util.ProgressBar
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.prompt
import java.nio.file.Path

fun removeBackups(instanceName: String, backupIds: List<String>): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val instanceCfg = getInstance(instanceName)!!

    val backups = instanceCfg.backups.toMutableMap()
    val removedBackups = mutableListOf<String>()


    val progressBar = ProgressBar("Removing backups", useMessages = true)
    progressBar.start(backupIds.size)
    for (backupId in backupIds) {
        if (!backups.containsKey(backupId)) {
            println("Error: Backup ID '$backupId' not found for instance '$instanceName'.")
            return false
        }

        val backupInfo = backups[backupId]!!
        val timestamp = backupInfo.dateTime
        val version = backupInfo.version

        val fileNameBase = "${timestamp.replace(".", "").replace(":", "")}-world-backup"
        val backupZipPath = instancePath.resolve("backups").resolve("$fileNameBase.zip")

        if (!backupZipPath.toFile().exists()) {
            println("Missing backup $backupId ($fileNameBase.zip), removing from config...")
            removedBackups.add(backupId)
            continue
        }

        backupZipPath.toFile().delete()
        removedBackups.add(backupId)
        progressBar.step(message = "Successfully removed backup $backupId ($fileNameBase.zip)")
    }

    removedBackups.forEach { backups.remove(it) }
    editInstance(instanceName, backups = backups)

    return true
}

fun removeAllBackups(instanceName: String): Boolean {
    val areYouSure = prompt("Are you sure you want to remove all backups in '$instanceName' instance? (y/N): ", timeoutMs = 600000, default = "n")

    if (areYouSure == "n") {
        return false
    }

    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val instanceCfg = getInstance(instanceName)!!

    val backups = instanceCfg.backups.toMutableMap()
    val removedBackups = mutableListOf<String>()


    val progressBar = ProgressBar("Removing backups", useMessages = true)
    progressBar.start(backups.size)
    for ( backup in backups) {

        val backupInfo = backup.value
        val timestamp = backupInfo.dateTime
        val version = backupInfo.version

        val fileNameBase = "${timestamp.replace(".", "").replace(":", "")}-world-backup"
        val backupZipPath = instancePath.resolve("backups").resolve("$fileNameBase.zip")

        if (!backupZipPath.toFile().exists()) {
            println("Missing backup ${backup.key} ($fileNameBase.zip), removing from config...")
            removedBackups.add(backup.key)
            continue
        }

        backupZipPath.toFile().delete()
        removedBackups.add(backup.key)
        progressBar.step(message = "Successfully removed backup ${backup.key} ($fileNameBase.zip)")
    }

    removedBackups.forEach { backups.remove(it) }
    editInstance(instanceName, backups = backups)

    return true
}