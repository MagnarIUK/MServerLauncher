package com.magnariuk.util

import com.magnariuk.util.configs.readConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.relativeTo

fun zipWithProgress(sourcePath: Path, destinationPath: Path, showFullProgress: Boolean = false): Boolean {
    val fileList = mutableListOf<Pair<Path, Path>>()
    Files.walk(sourcePath).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { fullPath ->
            val relPath = fullPath.relativeTo(sourcePath)
            fileList.add(fullPath to relPath)
        }
    }

    val total = fileList.size
    if (total == 0) {
        println("No files to back up.")
        return false
    }

    ZipOutputStream(Files.newOutputStream(destinationPath.resolveSibling("${destinationPath.fileName}.zip"))).use { zipOut ->
        val maxLogLines = if(fileList.size < readConfig().logMaxLines) fileList.size else readConfig().logMaxLines
        val progressBar = ProgressBar(label = "Backing up", useMessages = showFullProgress, maxMessageLines = maxLogLines)
        progressBar.start(total)
        for (pair in fileList) {
            val (fullPath, relPath) = pair
            zipOut.putNextEntry(ZipEntry(relPath.toString().replace("\\", "/")))
            Files.newInputStream(fullPath).use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()
            progressBar.step(message = "+ $fullPath")
        }
    }

    println("\nBackup completed successfully.")
    return true
}