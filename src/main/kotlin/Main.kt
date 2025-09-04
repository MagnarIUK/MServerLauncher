package com.magnariuk

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.configs.editGlobalConfig
import com.magnariuk.util.instance.attachResourcePack
import com.magnariuk.util.instance.backupInstance
import com.magnariuk.util.instance.checkInstance
import com.magnariuk.util.instance.createInstance
import com.magnariuk.util.instance.deleteInstance
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.launchServer
import com.magnariuk.util.instance.listBackups
import com.magnariuk.util.instance.listInstances
import com.magnariuk.util.instance.openInstanceFolder
import com.magnariuk.util.instance.rollbackInstance
import com.magnariuk.util.instance.updateServerProperties
import com.magnariuk.util.uploadFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

val def = INSTANCE_CONFIG()

abstract class InstanceCommand(
    val name: String,
    val helpText: String
) : CliktCommand(name = name) {
    override fun help(context: Context) = helpText.trimIndent()
    val instance by option("-i", "--instance", help = "Name of the instance").required()
    val validatedInstance: String by lazy {
        if (!checkInstance(instance)) {
            throw CliktError("Instance '$instance' does not exist")
        }
        instance
    }
    val inValidatedInstance: String by lazy {
        if (checkInstance(instance)) {
            throw CliktError("Instance '$instance' already exists")
        }
        instance
    }
}
abstract class OptionalInstanceCommand(
    val name: String,
    val helpText: String
) : CliktCommand(name = name) {
    override fun help(context: Context) = helpText.trimIndent()
    val instance by option("-i", "--instance", help = "Name of the instance")
    fun withOptionalInstance(
        onInstance: (inst: String) -> Unit,
        onNoInstance: () -> Unit = {}
    ) {
        instance?.let { inst ->
            if (checkInstance(inst)) {
                onInstance(inst)
            } else {
                throw CliktError("Instance '$inst' does not exist")
            }
        } ?: onNoInstance()
    }
}

class CheckInstanceCommand : InstanceCommand("check", "This command checks if instance exists.") {
    override fun run() {
        echo("Instance '$validatedInstance' exists.")
    }
}

class CreateInstanceCommand : InstanceCommand("create", "This command creates a new instance.") {
    val def = INSTANCE_CONFIG()
    val version by option("-ver", "--version", help="Instance Version").default(def.version.minecraft, defaultForHelp = def.version.minecraft)
    val memory by option("-mem", "--memory", help="Memory allocation for the server (e.g., 1024M, 2G). Default is 2048M.").default(def.memory, defaultForHelp = def.memory)
    val loader by option("-load", "--loader", help="Instance loader. Default is vanilla").default(def.version.loader.type, defaultForHelp = def.version.loader.type)
    val loaderVersion by option("-lver", "--loader-version", help="Version of loader. Default is latest").default(def.version.loader.version, defaultForHelp = def.version.loader.version)
    val autoBackup by option("-ab","--auto-backup", help = "Enable auto backup")
        .flag("--no-auto-backup","-nab", default = def.autoBackup, defaultForHelp = def.autoBackup.toString())

    val resourcepack by option("-rp", "--resourcepack",
        help="Path or link to the resource pack .zip file to attach (for 'attach' and 'create' commands).").default(def.resourcepack, defaultForHelp = def.resourcepack)
    val resourcepackPort by option("-rpp", "--resourcepack-port", help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands). Default is 2548.").int().default(def.resourcepackPort, def.resourcepackPort.toString())

    override fun run() {
        createInstance(
            name = inValidatedInstance,
            version = version,
            memory = memory,
            autoBackup = autoBackup,
            resourcePack = resourcepack,
            resourcePackPort = resourcepackPort,
            loader = loader,
            loaderVersion = loaderVersion
        )
    }

}

class EditInstanceCommand : InstanceCommand("edit", "This command edits an existing instance.") {
    val version by option("-ver", "--version", help="Instance Version")
    val memory by option("-mem", "--memory", help="Memory allocation for the server (e.g., 1024M, 2G).")
    val loader by option("-load", "--loader", help="Instance loader.")
    val loaderVersion by option("-lver", "--loader-version", help="Version of loader.")
    val autoBackup by option("-ab","--auto-backup", help = "Enable auto backup").flag("-nab","--no-auto-backup")

    val resourcepack by option("-rp", "--resourcepack",
        help="Path or link to the resource pack .zip file to attach (for 'attach' and 'create' commands).")
    val resourcepackPort by option("-rpp", "--resourcepack-port", help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands).").int()


    override fun run() {
        editInstance(
            name = validatedInstance,
            version = version,
            memory = memory,
            autoBackup = autoBackup,
            resourcepack = resourcepack,
            resourcepackPort = resourcepackPort,
            loaderType = loader,
            loaderVersion = loaderVersion
        )
    }
}

class ListCommand : OptionalInstanceCommand("list", "This command lists all instances, or backup in instance if instance is specified.") {
    override fun run() {
        withOptionalInstance(
            onInstance = { instance ->
                listBackups(instance)
            },
            onNoInstance = { listInstances() }
        )
    }
}

class LaunchCommand : InstanceCommand("launch", "This command launches an existing instance.") {
    val gui by option("-gui", "--gui",
        help="If used, server will launch with GUI").flag()

    override fun run() {
        runBlocking {
            launchServer(validatedInstance,gui)
        }
    }
}

class BackupInstanceCommand : InstanceCommand("backup", "This command backups an existing instance.") {
    val backup by option("-b", "--backup",
        help="Optional backup description.").default("")
    override fun run() {
        backupInstance(validatedInstance, backup)
    }
}

class RollbackInstanceCommand : InstanceCommand("rollback", "This command restores backup of an existing instance.") {
    val backup by option("-b", "--backup",
        help="Id of a backup you want to restore").required()
    override fun run() {
        rollbackInstance(validatedInstance, backup)
    }
}

class DeleteInstanceCommand : InstanceCommand("delete", "This command deletes an existing instance.") {
    override fun run() {
        deleteInstance(validatedInstance)
    }
}
class OpenInstanceFolderCommand : InstanceCommand("open", "This command opens folder with an existing instance.") {
    override fun run() {
        openInstanceFolder(validatedInstance)
    }
}
class AttachResourcepackCommand : InstanceCommand("attach", "This command attaches an existing instance.") {
    val resourcepack by option("-rp", "--resourcepack",
        help="Path or link to the resource pack .zip file to attach.").required()
    val resourcepackPort by option("-rpp", "--resourcepack-port", help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands). Default is 2548.").int()
    val upload by option("-u","--upload", help="Upload the file to upload server.").flag()


    override fun run() {
        if(upload) {
            try{
                val link =runBlocking { uploadFile(Path.of(resourcepack))!! }
                attachResourcePack(instance, link, resourcepackPort)
            }catch(e: Exception){
                echo("Failed to upload resourcepack '${resourcepack}': ${e}. Trying to attach file.", err=true)
                attachResourcePack(instance, resourcepack, resourcepackPort)
            }
        }else{
            attachResourcePack(instance, resourcepack, resourcepackPort)
        }
    }
}

class EditConfigCommand : CliktCommand("edit-config") {
    override fun help(context: Context): String = "This command edits global config."
    val map: Map<String, String> by option("-m", "--map",
        help="Key and value (map) for editing config.").associate()

    override fun run() {
        for (item in map) {
            editGlobalConfig(item.key, item.value)
        }
    }
}

class EditServerPropertiesCommand : InstanceCommand("edit-sp", "Edits server properties of an instance.") {
    val map: Map<String, String> by option("-m", "--map",
        help="Key and value (map) for editing config.").associate()

    override fun run() {
        for (item in map) {
            updateServerProperties(validatedInstance, item.key, item.value)
        }
    }
}

class ModrinthCommand : OptionalInstanceCommand("modrinth", "Not yet implemented") {
    override fun run() {
        echo("Not yet implemented")
    }
}
class MS : CliktCommand() {
    override fun run() {
        echo("Use --help to see available commands")
    }

    init {
        completionOption()
        subcommands(
            CheckInstanceCommand(),
            CreateInstanceCommand(), EditInstanceCommand(),
            ListCommand(), LaunchCommand(),
            BackupInstanceCommand(), RollbackInstanceCommand(),
            DeleteInstanceCommand(), OpenInstanceFolderCommand(),
            AttachResourcepackCommand(), EditConfigCommand(),
            EditServerPropertiesCommand(), ModrinthCommand()

        )
    }
}

fun main(args: Array<String>) = MS().main(args)