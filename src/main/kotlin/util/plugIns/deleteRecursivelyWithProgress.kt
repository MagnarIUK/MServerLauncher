package com.magnariuk.util.plugIns

import com.magnariuk.util.ProgressBar
import com.magnariuk.util.configs.readConfig
import java.io.File


fun File.deleteRecursivelyWithProgress(label: String = "Deleting") {
    val allFiles = this.walkBottomUp().toList()
    val cfg = readConfig()
    val useLogs = cfg.showFullProgress
    val maxLogLines = if (allFiles.size < cfg.logMaxLines) allFiles.size else cfg.logMaxLines
    val progress = ProgressBar(label = label, useMessages = useLogs, maxMessageLines = maxLogLines)
    progress.start(allFiles.size)

    for (file in allFiles) {
        file.delete()
        progress.step()
    }
}