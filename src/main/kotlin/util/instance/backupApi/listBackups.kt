package com.magnariuk.util.instance.backupApi

import com.magnariuk.util.Table
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.t
import java.nio.file.Path
import kotlin.collections.iterator

fun listBackups(instanceName: String, printHeader: Boolean = true) {
    val instanceCfg = getInstance(instanceName)!!


    val instancePath = Path.of(readConfig().instancesFolder, instanceName)
    val backupsPath = instancePath.resolve("backups").toFile()

    val backups = instanceCfg.backups.toMutableMap()
    if (backups.isEmpty()) {
        println(t("command.backup.subs.noBackups", listOf(instanceName)))
        return
    }

    val title = t("command.backup.subs.table.title", listOf(instanceName))
    val columns = listOf(
        t("command.backup.subs.table.id") to 10,
        t("command.backup.subs.table.dt") to 20,
        t("command.backup.subs.table.ver") to 10,
        t("command.backup.subs.table.desc") to 40)
    val table = Table(title, columns)
    if (printHeader) table.printHeader()

    val removed = mutableListOf<String>()

    for ((backupId, info) in backups) {
        val ts = info.dateTime.replace(".", "").replace(":", "")
        val backupFileName = "$ts-world-backup"
        val backupFile = backupsPath.resolve("$backupFileName.zip")

        if (!backupFile.exists()) {
            println(t("command.backup.subs.missingBackup", listOf(backupId, backupFileName)))
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
