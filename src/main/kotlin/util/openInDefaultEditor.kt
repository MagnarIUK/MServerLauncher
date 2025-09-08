package com.magnariuk.util

import com.magnariuk.util.configs.readConfig
import java.awt.Desktop
import java.io.File

fun openInDefaultEditor(file: File, ifNotExists: String? = null ) {
    if (!file.exists()) {
        ifNotExists?.let { println(it); return }
        throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
    }

    val cfg = readConfig()
    if (cfg.defaultEditor.isNotBlank()) {
        try {
            ProcessBuilder(cfg.defaultEditor, file.absolutePath)
                .inheritIO()
                .start()
            return
        } catch (e: Exception) {
            println("Failed to start ${cfg.defaultEditor}, falling back to system editor. Error: ${e.message}")
        }
    }


    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.EDIT)) {
            desktop.edit(file)
        } else if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file)
        } else {
            throw UnsupportedOperationException("No supported action to open editor")
        }
    } else {
        throw UnsupportedOperationException("Desktop is not supported on this system")
    }
}
