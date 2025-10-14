package com.magnariuk.util.instance.backupApi

import com.magnariuk.util.ProgressBar
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.prompt
import com.magnariuk.util.t
import java.nio.file.Path

fun removeBackups(instanceName: String, backupIds: List<String>): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val instanceCfg = getInstance(instanceName)!!

    val backups = instanceCfg.backups.toMutableMap()
    val removedBackups = mutableListOf<String>()

    val maxProgressLogs = if (backupIds.size < cfg.logMaxLines) backupIds.size else cfg.logMaxLines
    val progressBar = ProgressBar(t("command.backup.subs.removingBackups"), useMessages = true, maxMessageLines = maxProgressLogs)
    progressBar.start(backupIds.size)
    for (backupId in backupIds) {
        if (!backups.containsKey(backupId)) {
            println(t("command.backup.subs.backupNotFound", listOf(backupId, instanceName)))
            return false
        }

        val backupInfo = backups[backupId]!!
        val timestamp = backupInfo.dateTime
        val version = backupInfo.version

        val fileNameBase = "${timestamp.replace(".", "").replace(":", "")}-world-backup"
        val backupZipPath = instancePath.resolve("backups").resolve("$fileNameBase.zip")

        if (!backupZipPath.toFile().exists()) {
            println(t("command.backup.subs.missingBackup", listOf(backupId, fileNameBase)))
            removedBackups.add(backupId)
            continue
        }

        backupZipPath.toFile().delete()
        removedBackups.add(backupId)
        progressBar.step(message = t("command.backup.subs.backupRemoved", listOf(backupId, fileNameBase)))
    }

    removedBackups.forEach { backups.remove(it) }
    editInstance(instanceName, backups = backups)

    return true
}

fun removeAllBackups(instanceName: String): Boolean {
    val areYouSure = prompt(t("prompts.removeBackups"), timeoutMs = 600000, default = "n")

    if (areYouSure == "n") {
        return false
    }
    val instanceCfg = getInstance(instanceName)!!
    val backups = instanceCfg.backups.keys.toList()

    removeBackups(instanceName, backups)

    return true
}