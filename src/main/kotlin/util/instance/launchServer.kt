package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.ResourcePackServer
import com.magnariuk.util.api.calculateFileSha1
import com.magnariuk.util.api.calculateRemoteSha1
import com.magnariuk.util.api.downloadServer
import com.magnariuk.util.api.getFabric
import com.magnariuk.util.api.getVersion
import com.magnariuk.util.configs.*
import java.nio.file.Path
import kotlin.concurrent.thread

suspend fun launchServer(instanceName: String, gui: Boolean = false) {
    val config = readConfig()
    val instance = getInstance(instanceName)

    if (instance == null) {
        println("Instance '$instanceName' not found.")
        return
    }

    val instancePath = Path.of(config.instancesFolder, instanceName).toFile()
    val serverJarPath = instancePath.resolve("server.jar")

    val javaExec = "java"
    try {
        if (instance.autoBackup) {
            println("Auto-backup enabled. Creating backup for '$instanceName' before launch...")
            if (!backupInstance(instanceName, "Auto-backup")) {
                println("Auto-backup failed. Continuing with server launch anyway.")
            } else {
                println("Auto-backup completed.")
            }
        }

        val ver = getVersion(instance.version.minecraft)
        var serverJarUrl: String?
        var serverSha: String?

        when (instance.version.loader.type) {
            "vanilla" -> {
                serverJarUrl = ver.downloads.server.url
                serverSha = ver.downloads.server.sha1
            }

            "fabric" -> {
                serverJarUrl = getFabric(instance.version.minecraft, loader = instance.version.loader.version) ?: run {
                    println("Failed to get fabric version.")
                    return
                }
                println(serverJarUrl)
                println(instance.version.loader.version)
                serverSha = calculateRemoteSha1(serverJarUrl) ?: run {
                    println("Failed to calculate SHA1 for Fabric server jar.")
                    return
                }
            }

            else -> {
                println("Unsupported loader type: ${instance.version.loader.type}")
                return
            }
        }

        if (serverJarUrl.isEmpty() || serverSha.isEmpty()) {
            println("Server download information missing from version manifest.")
            return
        }

        val currentSha = if (serverJarPath.exists()) {
            println("Checking existing server.jar SHA1...")
            calculateFileSha1(serverJarPath.absolutePath)
        } else null

        if (!serverJarPath.exists() || currentSha != serverSha) {
            println("server.jar not found or SHA1 mismatch. Downloading server.jar...")
            downloadServer(serverJarUrl, serverSha, serverJarPath)
        } else {
            println("server.jar is up to date.")
        }

        setResourcePack(instanceName)


        val memoryAllocation = instance.memory.ifEmpty { INSTANCE_CONFIG().memory }

        val command = mutableListOf(
            javaExec,
            "-Xmx$memoryAllocation",
            "-Xms$memoryAllocation",
            "-jar",
            serverJarPath.absolutePath
        )

        val resPack = instance.resourcepack
        val needsHosting = resPack.isNotEmpty() && !resPack.startsWith("http://") && !resPack.startsWith("https://")
        println("Launching server with command: ${command.joinToString(" ")}")



        println("Use GUI: $gui")
        println("Needs hosting: $needsHosting")

        if (needsHosting || !gui) {
            command.add("nogui")
            val process = ProcessBuilder(command)
                .directory(instancePath)
                .inheritIO()
                .start()
            process.waitFor()
            println("Server '$instanceName' exited with code ${process.exitValue()}.")
        } else {
            val process = ProcessBuilder(command)
                .directory(instancePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()


            thread(start = true, isDaemon = true) {
                println("Server process started with PID ${process.pid()}.")
                process.waitFor()
                println("Server '$instanceName' exited with code ${process.exitValue()}.")
            }
        }

    } catch (e: Exception) {
        when (e) {
            is java.net.ConnectException, is java.net.UnknownHostException -> println("Network error during server launch: $e")
            is java.io.FileNotFoundException -> println("Error: Java executable '$javaExec' not found.")
            else -> println("An unexpected error occurred during server launch: $e")
        }
    } finally {
        ResourcePackServer.stop()
    }
}
