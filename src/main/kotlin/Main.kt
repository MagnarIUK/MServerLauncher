package com.magnariuk

import com.github.ajalt.clikt.completion.completionOption
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.pair
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.configs.editGlobalConfig
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.configsApi.attachResourcePack
import com.magnariuk.util.instance.backupApi.backupInstance
import com.magnariuk.util.instance.checkInstance
import com.magnariuk.util.instance.createInstance
import com.magnariuk.util.instance.deleteInstance
import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.launchServer
import com.magnariuk.util.instance.backupApi.listBackups
import com.magnariuk.util.instance.backupApi.removeAllBackups
import com.magnariuk.util.instance.backupApi.removeBackups
import com.magnariuk.util.instance.listInstances
import com.magnariuk.util.instance.openInstanceFolder
import com.magnariuk.util.instance.outputInstance
import com.magnariuk.util.instance.backupApi.rollbackInstance
import com.magnariuk.util.instance.configsApi.updateServerProperties
import com.magnariuk.util.instance.worldApi.resetWorld
import com.magnariuk.util.openInDefaultEditor
import com.magnariuk.util.uploadFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

val def = INSTANCE_CONFIG()

abstract class InstanceCommand(
    val name: String,
    val helpText: String
) : CliktCommand(name = name) {
    override fun help(context: Context) = helpText.trimIndent()
    val instance by argument("instance", help = "Name of the instance")
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
    val instance by argument("instance", help = "(OPTIONAL) Name of the instance").optional()
    fun withOptionalInstance(
        onInstance: (inst: String) -> Unit,
        onNoInstance: () -> Unit = {}
    ) {
        instance?.let { inst ->
            if (inst.isEmpty()) {
                onNoInstance()
                return
            }

            if (checkInstance(inst)) {
                onInstance(inst)
            } else {
                throw CliktError("Instance '$inst' does not exist")
            }
        } ?: onNoInstance()
    }
}


abstract class Command(
    val name: String,
    val helpString: String
) : CliktCommand(name = name) {
    override fun help(context: Context): String = helpString
}

class CheckInstanceCommand : InstanceCommand("check", "Checks if instance exists.") {
    override fun run() {
        outputInstance(validatedInstance)
    }
}

class CreateInstanceCommand : InstanceCommand("create", "Creates a new instance.") {
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

class EditInstanceCommand : InstanceCommand("edit", "Edits an existing instance.") {
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

class ListCommand : OptionalInstanceCommand("list", "Lists all instances, or backup in instance if instance is specified.") {
    override fun run() {
        withOptionalInstance(
            onInstance = { instance ->
                listBackups(instance)
            },
            onNoInstance = { listInstances() }
        )
    }
}

class LaunchCommand : InstanceCommand("launch", "Launches an existing instance.") {
    val gui by option("-gui", "--gui",
        help="If used, server will launch with GUI").flag()

    override fun run() {
        runBlocking {
            launchServer(validatedInstance, gui)
        }
    }
}

class BackupInstanceCommand : InstanceCommand("backup", "Backups an existing instance.") {
    override val invokeWithoutSubcommand = true
    val backupDesc by argument("desc", "Optional backup description.").default("")

    init {
        subcommands(
            RollbackInstanceCommand {validatedInstance},
            RemoveBackupCommand {validatedInstance},
        )

    }
    override fun run() {
        if(currentContext.invokedSubcommand == null) {
            backupInstance(validatedInstance, backupDesc)
        }
    }
}
class RemoveBackupCommand(val instanceProvider: () -> String) : Command("remove", "Removes backup of an existing instance.") {
    val backupIds by argument("ids", "IDs of a backups you want to restore \n(use 'list <instance>' to check backups)\nIf omitted, will delete all backups").multiple().optional()
    override fun run() {
        backupIds?.let {
            removeBackups(instanceProvider(), it)
        } ?: removeAllBackups(instanceProvider())
    }
}

class RollbackInstanceCommand(val instanceProvider: () -> String) : Command("rollback", "Restores backup of an existing instance.") {
    val backupId by argument("id", "Id of a backup you want to restore \n(use 'list <instance>' to check backups)")
    override fun run() {
        rollbackInstance(instanceProvider(), backupId)
    }
}

class DeleteInstanceCommand : InstanceCommand("delete", "Deletes an existing instance.") {
    override fun run() {
        deleteInstance(validatedInstance)
    }
}
class OpenInstanceFolderCommand : InstanceCommand("open", "Opens folder with an existing instance.") {
    override fun run() {
        openInstanceFolder(validatedInstance)
    }
}
class AttachResourcepackCommand : InstanceCommand("attach", "Attaches an resourcepack to existing instance.") {
    val resourcepack by argument("resource pack", "Path or link to the resource pack .zip file to attach")
    val resourcepackPort by option("-rpp", "--resourcepack-port", help="Port for the resource pack HTTP server (for 'attach', 'create', and 'edit' commands). Default is 2548.").int()
    val upload by option("-u","--upload", help="Upload the file to upload server.").flag()


    override fun run() {
        if(upload) {
            try{
                val link = runBlocking { uploadFile(Path.of(resourcepack))!! }
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

class EditConfigCommand : Command("config",
    "Edits global config.\n\n" +
            "If <key value> is omitted, the config file will open in your default editor") {
    val map: Pair<String, String>? by argument("key value" ,help="(OPTIONAL) Key and value for editing config.").pair().optional()
    override fun run() {
        map?.let {
            editGlobalConfig(it.first, it.second)
        } ?: run {
            openInDefaultEditor(configPath.toFile())
        }
    }
}

class EditServerPropertiesCommand : InstanceCommand("sp",
    "Edits server properties of an instance.\n\nIf <key value> is omitted, the server.properties file will open in your default editor") {
    val map: Pair<String, String>? by argument("key value", "(OPTIONAL) Key and value for editing config.").pair().optional()
    override fun run() {
        val file = Path.of(readConfig().instancesFolder, validatedInstance, "server.properties")
        map?.let {
            updateServerProperties(validatedInstance, it.first, it.second)
        } ?: run {
            openInDefaultEditor(file.toFile(), "server.properties file does not exist for $validatedInstance, launch this instance at least once.")
        }
    }
}

class WorldCommand : Command("world", "World control api (WIP)"){
    init {
        subcommands(
            WorldResetCommand()
        )
    }

    override fun run() {}

}
class WorldResetCommand : InstanceCommand("reset", "Resets world in given instance"){
    override fun run() {
        resetWorld(validatedInstance)
    }
}

class ModrinthCommand : OptionalInstanceCommand("modrinth", "Not yet implemented") {
    override fun run() {
        TODO("Not yet implemented")
    }
}
class MS : CliktCommand() {
    override fun run() {}

    init {
        completionOption()
        subcommands(
            CheckInstanceCommand(),
            CreateInstanceCommand(), EditInstanceCommand(),
            ListCommand(), LaunchCommand(),
            BackupInstanceCommand(),
            DeleteInstanceCommand(), OpenInstanceFolderCommand(),
            AttachResourcepackCommand(), EditConfigCommand(),
            EditServerPropertiesCommand(), ModrinthCommand(),
            WorldCommand(),
        )
    }
}

object Test{
    fun main() {
        val ms = MS()
        while (true){
            val input = readln()
            if (input == "q") { break }
            try {
                ms.main(input.split(" "))
            } catch(e: Exception){
                println(e.message)
            }

        }
    }
}


fun main(args: Array<String>) = MS().main(args)
//fun main() = Test.main()