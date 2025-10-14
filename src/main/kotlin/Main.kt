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
import com.magnariuk.MServerLauncher.BuildConfig
import com.magnariuk.data.configs.INSTANCE_CONFIG
import com.magnariuk.util.I18n
import com.magnariuk.util.Table
import com.magnariuk.util.checkUpdates
import com.magnariuk.util.configs.editGlobalConfig
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.getLatestRelease
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
import com.magnariuk.util.t
import com.magnariuk.util.uploadFile
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

val def = INSTANCE_CONFIG()

abstract class InstanceCommand(
    val name: String,
    val helpText: String
) : CliktCommand(name = name) {
    override fun help(context: Context) = helpText.trimIndent()
    val instance by argument("instance", help = t("argument.help.instance"))
    val validatedInstance: String by lazy {
        if (!checkInstance(instance)) {
            throw CliktError(t("argument.errors.instanceNotExists", instance))
        }
        instance
    }
    val inValidatedInstance: String by lazy {
        if (checkInstance(instance)) {
            throw CliktError(t("argument.errors.instanceAlreadyExists", instance))
        }
        instance
    }
}
abstract class OptionalInstanceCommand(
    val name: String,
    val helpText: String
) : CliktCommand(name = name) {
    override fun help(context: Context) = helpText.trimIndent()
    val instance by argument("instance", help = "${t("argument.help.optionalArgument")}${t("argument.help.instance")}").optional()
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
                throw CliktError(t("argument.errors.instanceNotExists"))
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

class CheckInstanceCommand : InstanceCommand("check", t("command.check")) {
    override fun run() {
        outputInstance(validatedInstance)
    }
}

class CreateInstanceCommand : InstanceCommand("create", t("command.create._")) {
    val def = INSTANCE_CONFIG()
    val version by option("-ver", "--version", help=t("option.version")).default(def.version.minecraft, defaultForHelp = def.version.minecraft)
    val memory by option("-mem", "--memory", help=t("option.memory")).default(def.memory, defaultForHelp = def.memory)
    val loader by option("-load", "--loader", help=t("option.loader")).default(def.version.loader.type, defaultForHelp = def.version.loader.type)
    val loaderVersion by option("-lver", "--loader-version", help=t("option.loaderVersion")).default(def.version.loader.version, defaultForHelp = def.version.loader.version)
    val autoBackup by option("-ab","--auto-backup", help = t("option.autoBackup"))
        .flag("--no-auto-backup","-nab", default = def.autoBackup, defaultForHelp = def.autoBackup.toString())

    val resourcepack by option("-rp", "--resourcepack",
        help=t("option.resourcepack")).default(def.resourcepack, defaultForHelp = def.resourcepack)

    override fun run() {
        createInstance(
            name = inValidatedInstance,
            version = version,
            memory = memory,
            autoBackup = autoBackup,
            resourcePack = resourcepack,
            loader = loader,
            loaderVersion = loaderVersion
        )
    }

}

class EditInstanceCommand : InstanceCommand("edit", t("command.edit._")) {
    val version by option("-ver", "--version", help=t("option.version")).default(def.version.minecraft, defaultForHelp = def.version.minecraft)
    val memory by option("-mem", "--memory", help=t("option.memory")).default(def.memory, defaultForHelp = def.memory)
    val loader by option("-load", "--loader", help=t("option.loader")).default(def.version.loader.type, defaultForHelp = def.version.loader.type)
    val loaderVersion by option("-lver", "--loader-version", help=t("option.loaderVersion")).default(def.version.loader.version, defaultForHelp = def.version.loader.version)
    val autoBackup by option("-ab","--auto-backup", help = t("option.autoBackup"))
        .flag("--no-auto-backup","-nab", default = def.autoBackup, defaultForHelp = def.autoBackup.toString())

    val resourcepack by option("-rp", "--resourcepack",
        help=t("option.resourcepack")).default(def.resourcepack, defaultForHelp = def.resourcepack)


    override fun run() {
        editInstance(
            name = validatedInstance,
            version = version,
            memory = memory,
            autoBackup = autoBackup,
            resourcepack = resourcepack,
            loaderType = loader,
            loaderVersion = loaderVersion
        )
    }
}

class ListCommand : OptionalInstanceCommand("list", t("command.list._")) {
    override fun run() {
        withOptionalInstance(
            onInstance = { instance ->
                listBackups(instance)
            },
            onNoInstance = { listInstances() }
        )
    }
}

class LaunchCommand : InstanceCommand("launch", t("command.launch._")) {
    val gui by option("-gui", "--gui",
        help=t("option.gui")).flag()

    override fun run() {
        runBlocking {
            launchServer(validatedInstance, gui)
        }
    }
}

class BackupInstanceCommand : InstanceCommand("backup", t("command.backup._")) {
    override val invokeWithoutSubcommand = true
    val backupDesc by argument("desc", t("option.backupDesc")).default("")

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
class RemoveBackupCommand(val instanceProvider: () -> String) : Command("remove", t("command.backup.remove")) {
    val backupIds by argument("ids", t("option.backupIds")).multiple().optional()
    override fun run() {
        backupIds?.let {
            removeBackups(instanceProvider(), it)
        } ?: removeAllBackups(instanceProvider())
    }
}

class RollbackInstanceCommand(val instanceProvider: () -> String) : Command("rollback", t("command.backup.rollback")) {
    val backupId by argument("id", t("option.backupId"))
    override fun run() {
        rollbackInstance(instanceProvider(), backupId)
    }
}

class DeleteInstanceCommand : InstanceCommand("delete", t("command.delete")) {
    override fun run() {
        deleteInstance(validatedInstance)
    }
}
class OpenInstanceFolderCommand : InstanceCommand("open", t("command.open._")) {
    override fun run() {
        openInstanceFolder(validatedInstance)
    }
}
class AttachResourcepackCommand : InstanceCommand("attach", t("command.attach._")) {
    val resourcepack by argument("resource pack", t("option.resourcepack"))
    val upload by option("-u","--upload", help=t("option.upload")).flag()


    override fun run() {
        if(upload) {
            try{
                val link = runBlocking { uploadFile(Path.of(resourcepack))!! }
                attachResourcePack(instance, link)
            }catch(e: Exception){
                echo(t("command.attach.failedUpload", resourcepack, e), err=true)
            }

        }else{
            attachResourcePack(instance, resourcepack)
        }
    }
}

class EditConfigCommand : Command("config", t("command.config")) {
    val map: Pair<String, String>? by argument("key value" ,help="${t("argument.help.optionalArgument")} ${t("option.map")}").pair().optional()
    override fun run() {
        map?.let {
            editGlobalConfig(it.first, it.second)
        } ?: run {
            openInDefaultEditor(configFilePath.toFile())
        }
    }
}

class EditServerPropertiesCommand : InstanceCommand("sp",
    t("command.sp._")) {
    val map: Pair<String, String>? by argument("key value", "${t("argument.help.optionalArgument")} ${t("option.map")}").pair().optional()
    override fun run() {
        val file = Path.of(readConfig().instancesFolder, validatedInstance, "server.properties")
        map?.let {
            updateServerProperties(validatedInstance, it.first, it.second)
        } ?: run {
            openInDefaultEditor(file.toFile(), t("command.sp.fileNotExists", validatedInstance))
        }
    }
}

class WorldCommand : Command("world", t("command.world._")){
    init {
        subcommands(
            WorldResetCommand()
        )
    }

    override fun run() {}

}
class WorldResetCommand : InstanceCommand("reset", t("command.world.reset")){
    override fun run() {
        resetWorld(validatedInstance)
    }
}

class ModrinthCommand : OptionalInstanceCommand("modrinth", t("argument.nyi")) {
    override fun run() {
        TODO(t("argument.nyi"))
    }
}
class InfoCommand : Command("info", t("command.info._")) {
    override fun run() {
        val table = Table(t("command.info.tableTitle", BuildConfig.APP_NAME))
        var verStr = "${BuildConfig.APP_VERSION} ${t("command.info.buildNumber")} ${BuildConfig.BUILD_NUMBER}"
        val latestVer = runBlocking { checkUpdates(justChecking = true) }
        if(latestVer != BuildConfig.APP_VERSION) verStr+=" | ${yellow}UPDATE TO $latestVer IS AVAILABLE$reset"
        table.printVertical(mapOf(
            t("command.info.ver") to verStr,
            t("command.info.buildTime") to BuildConfig.BUILD_TIME,
            t("command.info.author") to BuildConfig.AUTHOR,
            t("command.info.translations") to BuildConfig.Tranlators,
            "GitHub" to BuildConfig.GITHUB,
        ))
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
            WorldCommand(), InfoCommand()
        )
    }
}




fun main(args: Array<String>) {
    runBlocking { checkUpdates() }
    I18n.loadAllLocales()
    I18n.setLocale(readConfig().lang)
    MS().main(args)
}
