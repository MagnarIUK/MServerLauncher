package com.magnariuk.util.instance

import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.api.calculateFileSha1
import com.magnariuk.util.api.calculateRemoteSha1
import com.magnariuk.util.api.downloadServer
import com.magnariuk.util.api.getFabric
import com.magnariuk.util.api.getVersion
import com.magnariuk.util.configs.*
import com.magnariuk.util.instance.backupApi.backupInstance
import com.magnariuk.util.instance.configsApi.setResourcePack
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.div

suspend fun launchServer(instanceName: String, gui: Boolean = false, exitImmediately: Boolean = false) {
    val config = readConfig()
    val instance = getInstance(instanceName)!!

    val instancePath = Path.of(config.instancesFolder, instanceName).toFile()

    val serverJarPath = (Path.of(config.instancesFolder) / ".versions")
    val javaExec = "java"
    try {
        if (instance.autoBackup && !exitImmediately) {
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
        var versionStr = ver.id
        if (instance.version.loader.type == "fabric"){
            versionStr = "fabric-${ver.id}.jar"
        }
        val serverJarFile = (serverJarPath / versionStr).toFile()
        serverJarFile.parentFile.mkdirs()
        val currentSha = if (serverJarFile.exists()) {
            println("Checking existing server.jar SHA1...")
            calculateFileSha1(serverJarFile.absolutePath)
        } else null

        if (!serverJarFile.exists() || currentSha != serverSha) {
            println("server.jar not found or SHA1 mismatch. Downloading server.jar...")
            downloadServer(serverJarUrl, serverSha, serverJarFile)
        } else {
            println("server.jar is up to date.")
        }


        if(!exitImmediately) setResourcePack(instanceName)


        val memoryAllocation = instance.memory.ifEmpty { INSTANCE_CONFIG().memory }

        val command = mutableListOf(
            javaExec,
            "-Xmx$memoryAllocation",
            "-Xms$memoryAllocation",
            "-jar",
            serverJarFile.absolutePath
        )

        val resPack = instance.resourcepack
        println("Launching server with command: ${command.joinToString(" ")}")



        if(!exitImmediately) {
            println("Use GUI: $gui")
        }


        cleanUp(instanceName)

        if (!gui || exitImmediately) {
            command.add("nogui")
            if(exitImmediately) {
                val process = ProcessBuilder(command)
                    .directory(instancePath)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()

                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val logLineRegex = Regex("""\[\d{2}:\d{2}:\d{2}]\s+\[([^\]]+?)/([A-Z]+)]:""")
                        val match = logLineRegex.find(line)
                        if (match != null) {
                            println(line)
                        }

                        if ("Done (" in line && line.contains("For help, type")){
                            process.outputStream.bufferedWriter().use { it.write("stop\n"); it.flush() }
                            break
                        }
                    }
                }

                process.waitFor()
                println("Server '$instanceName' initialised and exited")
            } else{
                val process = ProcessBuilder(command)
                    .directory(instancePath)
                    .inheritIO()
                    .start()
                process.waitFor()
                println("Server '$instanceName' exited with code ${process.exitValue()}.")
            }

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
    }
}
