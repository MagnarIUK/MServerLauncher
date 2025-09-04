package com.magnariuk.util

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.relativeTo

fun zipWithProgress(sourcePath: Path, destinationPath: Path): Boolean {
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
        for ((i, pair) in fileList.withIndex()) {
            val (fullPath, relPath) = pair
            zipOut.putNextEntry(ZipEntry(relPath.toString().replace("\\", "/"))) // Ensure zip uses forward slashes
            Files.newInputStream(fullPath).use { input ->
                input.copyTo(zipOut)
            }
            zipOut.closeEntry()

            val progress = ((i + 1) * 100) / total
            val barLength = 30
            val filledLength = (progress * barLength) / 100
            val bar = "█".repeat(filledLength) + "-".repeat(barLength - filledLength)
            print("\rBacking up: |$bar| $progress% (${i+1}/$total files)")
        }
    }

    println("\nBackup completed successfully.")
    return true
}