package com.magnariuk.util.instance.backupApi

import com.magnariuk.data.configs.CONFIG
import com.magnariuk.util.ProgressBar
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
import com.magnariuk.util.t
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

fun rollbackInstance(instanceName: String, backupId: String): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val instanceCfg = getInstance(instanceName)!!
    val backups = instanceCfg.backups
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
        println(t("command.backup.subs.backupNotFoundAt", listOf(backupZipPath)))
        return false
    }

    if (cfg.backupOnRollback) {
        println(t("command.backup.subs.automaticBackup"))
        if (!backupInstance(instanceName, desc = t("command.backup.subs.autobackupRollbackDesc", listOf(backupId)))) {
            println(t("command.backup.subs.autobackupRollbackDesc"))
            return false
        }
    }

    println(t("command.backup.subs.rollingBack", listOf(instanceName, backupId)))

    val worldFolder = instancePath.resolve("world")
    if (worldFolder.toFile().exists()) {
        try {
            worldFolder.toFile().deleteRecursivelyWithProgress(t("command.backup.subs.deletingWorld"))
        } catch (e: Exception) {
            println(t("command.backup.subs.errorDeletingWorld", listOf(e.message)))
            return false
        }
    }

    return try {
        println(t("command.backup.subs.extractingBackup", listOf(backupZipPath.fileName)))
        Files.createDirectories(worldFolder)

        ZipFile(backupZipPath.toFile()).use { zip ->
            val maxLogSize = if(zip.size() < readConfig().logMaxLines) zip.size() else readConfig().logMaxLines
            val progressBar = ProgressBar(label = t("command.backup.subs.rollingBackProgress"))
            progressBar.start(zip.size())
            zip.entries().asSequence().forEach { entry ->
                val outPath = worldFolder.resolve(entry.name)
                if (entry.isDirectory) {
                    Files.createDirectories(outPath)
                } else {
                    Files.createDirectories(outPath.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.copy(input, outPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                progressBar.step()
            }
        }

        println(t("command.backup.subs.rollbackComplete"))
        true
    } catch (e: Exception) {
        println(t("command.backup.subs.errorDuringExtraction", listOf(e.message)))
        false
    }
}
