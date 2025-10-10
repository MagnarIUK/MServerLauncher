package com.magnariuk.util.instance.backupApi

import com.magnariuk.data.configs.Backup
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.zipWithProgress
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun backupInstance(instanceName: String, desc: String = ""): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val instanceCfg = getInstance(instanceName)!!

    if (!instancePath.toFile().isDirectory) {
        println("Error: Instance folder '$instanceName' not found at '$instancePath'.")
        return false
    }

    val backupsPath = instancePath.resolve("backups")
    Files.createDirectories(backupsPath)

    val worldFolder = instancePath.resolve("world")
    if (!worldFolder.toFile().isDirectory) {
        println("Error: 'world' folder not found for instance '$instanceName'.")
        return false
    }

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HH:mm:ss"))
    val backupId = generateUniqueHex(instanceCfg.backups.keys)
    val backupName = "${timestamp.replace(".", "").replace(":", "")}-world-backup"
    val destinationPath = backupsPath.resolve(backupName)

    val backup = Backup(
        version = instanceCfg.version.minecraft,
        dateTime = timestamp,
        desc = desc
    )

    return try {
        if (!zipWithProgress(worldFolder, destinationPath, showFullProgress = cfg.showFullProgress)) return false

        println("Backup created successfully: ${destinationPath}.zip")

        val updatedBackups = instanceCfg.backups.toMutableMap()
        updatedBackups[backupId] = backup

        editInstance(instanceName, backups = updatedBackups)

        true
    } catch (e: Exception) {
        println("Error creating backup for '$instanceName': ${e.message}")
        false
    }
}

fun generateUniqueHex(existing: Set<String>, size: Int = 1): String {
    var hex: String
    do {
        hex = (1..size).joinToString("") {
            List(2) { "0123456789abcdef".random() }.joinToString("")
        }
    } while (hex in existing)
    return hex
}
