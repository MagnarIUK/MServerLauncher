package com.magnariuk.util.plugIns

import com.magnariuk.util.ProgressBar
import java.io.File


fun File.deleteRecursivelyWithProgress(label: String = "Deleting") {
    val allFiles = this.walkBottomUp().toList()
    val progress = ProgressBar(label = label)
    progress.start(allFiles.size)

    for (file in allFiles) {
        file.delete()
        progress.step()
    }
}