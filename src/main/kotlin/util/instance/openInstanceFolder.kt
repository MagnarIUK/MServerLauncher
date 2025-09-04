package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import java.awt.Desktop
import java.nio.file.Path
import kotlin.io.path.exists

fun openInstanceFolder(instanceName: String): Boolean {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)

    if (!instancePath.exists()) {
        println("Error: Instance folder '$instancePath' does not exist.")
        println("Make sure the instance has been created.")
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
                    println("Opening folders is not supported on this OS: $osName")
                    return false
                }
            }
        }
        println("Opened folder: $instancePath")
        true
    } catch (e: Exception) {
        println("Error opening folder '$instancePath': ${e.message}")
        false
    }
}
