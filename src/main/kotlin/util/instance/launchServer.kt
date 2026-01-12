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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.div

suspend fun launchServer(
    instanceName: String,
    gui: Boolean = false,
    exitImmediately: Boolean = false,
    logger: ((String) -> Unit)? = null,
    onProcessStart: ((Process) -> Unit)? = null,
    apiMode: Boolean = false) {

    fun log(msg: String) {
        if (logger != null) logger(msg) else println(msg)
    }
    
    val config = readConfig()
    val instance = getInstance(instanceName)!!

    val instancePath = Path.of(config.instancesFolder, instanceName).toFile()

    val serverJarPath = (Path.of(config.instancesFolder) / ".versions")
    val version = getVersion(instance.version.minecraft)

    val javaExec = when(version.javaVersion.majorVersion){
        25 ->  config.jvm25
        21 ->  config.jvm21
        17 ->  config.jvm17
        16 ->  config.jvm16
        8 ->  config.jvm8
        else -> {"java"}
    }


    try {
        if (instance.autoBackup && !exitImmediately) {
            log(t("command.launch.autobackup"))
            if (!backupInstance(instanceName, t("command.launch.backupDesc"))) {
                log(t("command.launch.autoBackupFailed"))
            } else {
                log(t("command.launch.autoBackupCompleted"))
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
                    log(t("command.launch.failedToGetFabric"))
                    return
                }
                log(serverJarUrl)
                log(instance.version.loader.version)
                serverSha = calculateRemoteSha1(serverJarUrl) ?: run {
                    log(t("command.launch.failedCalculateFabricSha"))
                    return
                }
            }

            else -> {
                log(t("command.launch.unsupportedLoader", listOf(instance.version.loader.type)))
                return
            }
        }

        if (serverJarUrl.isEmpty() || serverSha.isEmpty()) {
            log(t("command.launch.serverDownloadInfoMissing"))
            return
        }
        var versionStr = "${ver.id}.jar"
        if (instance.version.loader.type == "fabric"){
            versionStr = "fabric-${ver.id}.jar"
        }
        val serverJarFile = (serverJarPath / versionStr).toFile()
        serverJarFile.parentFile.mkdirs()
        val currentSha = if (serverJarFile.exists()) {
            log(t("command.launch.checkingServerJarSha"))
            calculateFileSha1(serverJarFile.absolutePath)
        } else null

        if (!serverJarFile.exists() || currentSha != serverSha) {
            log(t("command.launch.downloadingJar"))
            downloadServer(serverJarUrl, serverSha, serverJarFile)
        } else {
            log(t("command.launch.jarUpToDate"))
        }


        val memoryAllocation = instance.memory.ifEmpty { INSTANCE_CONFIG().memory }

        val command = mutableListOf(
            javaExec,
            "-Xmx$memoryAllocation",
            "-Xms$memoryAllocation",
            "-jar",
            serverJarFile.absolutePath
        )
        if (!gui || exitImmediately || logger != null) {
            command.add("nogui")
        }
        val resPack = instance.resourcepack
        if(!exitImmediately) setResourcePack(instanceName, apiMode = apiMode)
        if(!exitImmediately) {
            log(t("command.launch.useGui", listOf(gui)))
        }
        log(t("command.launch.launchingWithCommand", listOf(command.joinToString(" "))))


        cleanUp(instanceName)

        val builder = ProcessBuilder(command)
            .directory(instancePath)

        if (exitImmediately) {
            builder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            builder.redirectError(ProcessBuilder.Redirect.PIPE)
            val process = withContext(Dispatchers.IO) {
                builder.start()
            }

            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val logLineRegex = Regex("""\[\d{2}:\d{2}:\d{2}]\s+\[([^]]+?)/([A-Z]+)]:""")
                    val match = logLineRegex.find(line)
                    if (match != null) log(line)

                    if ("Done (" in line && line.contains("For help, type")) {
                        process.outputStream.bufferedWriter().use { it.write("stop\n"); it.flush() }
                        break
                    }
                }
            }
            withContext(Dispatchers.IO) {
                process.waitFor()
            }
            log(t("command.launch.initialisedAndExited"))
        }
        else if (logger != null) {
            builder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            builder.redirectError(ProcessBuilder.Redirect.PIPE)
            builder.redirectInput(ProcessBuilder.Redirect.PIPE)

            val process = withContext(Dispatchers.IO) {
                builder.start()
            }

            onProcessStart?.invoke(process)

            thread(start = true) {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { log(it) }
                }
            }
            thread(start = true) {
                process.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { log("[ERR] $it") }
                }
            }

            withContext(Dispatchers.IO) {
                process.waitFor()
            }
            log(t("command.launch.serverExited", listOf(instanceName, process.exitValue())))
        }
        else if (!gui) {
            builder.inheritIO()
            val process = withContext(Dispatchers.IO) {
                builder.start()
            }
            withContext(Dispatchers.IO) {
                process.waitFor()
            }
            log(t("command.launch.serverExited", listOf(instanceName, process.exitValue())))
        }
        else {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
            builder.redirectError(ProcessBuilder.Redirect.DISCARD)
            builder.redirectInput(ProcessBuilder.Redirect.PIPE)

            val process = builder.start()

            thread(start = true, isDaemon = true) {
                log(t("command.launch.serverStarted", listOf(process.pid())))
                process.waitFor()
                log(t("command.launch.serverExited", listOf(instanceName, process.exitValue())))
            }
        }

    } catch (e: Exception) {
        log(t("command.launch.unexpectedError", listOf(e)))
    }
}