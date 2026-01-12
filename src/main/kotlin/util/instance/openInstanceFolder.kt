package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.t
import java.awt.Desktop
import java.nio.file.Path
import kotlin.io.path.exists

fun openInstanceFolder(instanceName: String, apiMode: Boolean =false): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)

    if (!instancePath.exists()) {
        if(!apiMode) println(t("command.open.subs.folderNotExists", instancePath))
        return false
    }

    return try {
        val folderFile = instancePath.toFile()
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(folderFile)
        } else {
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", folderFile.absolutePath))
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") ->
                    Runtime.getRuntime().exec(arrayOf("xdg-open", folderFile.absolutePath))
                else -> {
                    if(!apiMode) println(t("command.open.subs.notSupported", osName))
                    return false
                }
            }
        }
        if(!apiMode) println(t("command.open.subs.opened", instancePath))
        true
    } catch (e: Exception) {
        if(!apiMode) println(t("command.open.subs.error", instancePath, e.message))
        false
    }
}