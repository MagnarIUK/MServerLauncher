package com.magnariuk.util.instance.backupApi

import com.magnariuk.util.ProgressBar
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
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
        println("Error: Backup ID '$backupId' not found for instance '$instanceName'.")
        return false
    }

    val backupInfo = backups[backupId]!!
    val timestamp = backupInfo.dateTime
    val version = backupInfo.version

    val fileNameBase = "${timestamp.replace(".", "").replace(":", "")}-world-backup"
    val backupZipPath = instancePath.resolve("backups").resolve("$fileNameBase.zip")

    if (!backupZipPath.toFile().exists()) {
        println("Error: Backup file not found at '$backupZipPath'.")
        return false
    }

    if (cfg.backupOnRollback) {
        println("Creating automatic backup before rollback...")
        if (!backupInstance(instanceName, desc = "Auto-backup before rollback to $backupId")) {
            println("Error: Failed to create backup before rollback. Aborting.")
            return false
        }
    }

    println("Rolling back instance '$instanceName' using backup '$backupId'...")

    val worldFolder = instancePath.resolve("world")
    if (worldFolder.toFile().exists()) {
        try {
            worldFolder.toFile().deleteRecursivelyWithProgress("Deleting existing world folder")
        } catch (e: Exception) {
            println("Error removing old world folder: ${e.message}")
            return false
        }
    }

    return try {
        println("Extracting backup '${backupZipPath.fileName}' into 'world' folder...")
        Files.createDirectories(worldFolder)

        ZipFile(backupZipPath.toFile()).use { zip ->
            val progressBar = ProgressBar(label = "Rolling back")
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

        println("Rollback complete.")
        true
    } catch (e: Exception) {
        println("Error during extraction: ${e.message}")
        false
    }
}
