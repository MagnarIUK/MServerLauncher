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
import com.magnariuk.util.t
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.div

suspend fun launchServer(instanceName: String, gui: Boolean = false, exitImmediately: Boolean = false) {
    val config = readConfig()
    val instance = getInstance(instanceName)!!

    val instancePath = Path.of(config.instancesFolder, instanceName).toFile()

    val serverJarPath = (Path.of(config.instancesFolder) / ".versions")
    val javaExec = config.exec
    try {
        if (instance.autoBackup && !exitImmediately) {
            println(t("command.launch.autobackup"))
            if (!backupInstance(instanceName, t("command.launch.backupDesc"))) {
                println(t("command.launch.autoBackupFailed"))
            } else {
                println(t("command.launch.autoBackupCompleted"))
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
                    println(t("command.launch.failedToGetFabric"))
                    return
                }
                println(serverJarUrl)
                println(instance.version.loader.version)
                serverSha = calculateRemoteSha1(serverJarUrl) ?: run {
                    println(t("command.launch.failedCalculateFabricSha"))
                    return
                }
            }

            else -> {
                println(t("command.launch.unsupportedLoader", listOf(instance.version.loader.type)))
                return
            }
        }

        if (serverJarUrl.isEmpty() || serverSha.isEmpty()) {
            println(t("command.launch.serverDownloadInfoMissing"))
            return
        }
        var versionStr = "${ver.id}.jar"
        if (instance.version.loader.type == "fabric"){
            versionStr = "fabric-${ver.id}.jar"
        }
        val serverJarFile = (serverJarPath / versionStr).toFile()
        serverJarFile.parentFile.mkdirs()
        val currentSha = if (serverJarFile.exists()) {
            println(t("command.launch.checkingServerJarSha"))
            calculateFileSha1(serverJarFile.absolutePath)
        } else null

        if (!serverJarFile.exists() || currentSha != serverSha) {
            println(t("command.launch.downloadingJar"))
            downloadServer(serverJarUrl, serverSha, serverJarFile)
        } else {
            println(t("command.launch.jarUpToDate"))
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
        println(t("command.launch.launchingWithCommand", listOf(command.joinToString(" "))))



        if(!exitImmediately) {
            println(t("command.launch.useGui", listOf(gui)))
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
                println(t("command.launch.initialisedAndExited"))
            } else{
                val process = ProcessBuilder(command)
                    .directory(instancePath)
                    .inheritIO()
                    .start()
                process.waitFor()
                println(t("command.launch.serverExited", listOf(instanceName, process.exitValue())))
            }

        } else {
            val process = ProcessBuilder(command)
                .directory(instancePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .start()


            thread(start = true, isDaemon = true) {
                println(t("command.launch.serverStarted", listOf(process.pid())))
                process.waitFor()
                println(t("command.launch.serverExited", listOf(instanceName, process.exitValue())))
            }
        }

    } catch (e: Exception) {
        when (e) {
            is java.net.ConnectException, is java.net.UnknownHostException -> println(t("command.launch.networkError", listOf(e)))
            is java.io.FileNotFoundException -> println(t("command.launch.javaNotFound", listOf(javaExec)))
            else -> println(t("command.launch.unexpectedError", listOf(e)))
        }
    }
}
