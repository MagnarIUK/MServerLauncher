package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
import com.magnariuk.util.prompt
import com.magnariuk.util.t
import java.nio.file.Path

fun deleteInstance(name: String): Boolean {
    val cfg = readConfig()
    val instancesPath = Path.of(cfg.instancesFolder)
    val instancePath = instancesPath.resolve(name)


    val input = prompt(t("prompts.deleteInstance", name), t("prompts.deletingInstanceTimeout"), 600000, "n")
    if (input != "y" && input != "yes") {
        println(t("prompts.deletionAborted"))
        return false
    }

    return try {
        instancePath.toFile().deleteRecursivelyWithProgress(t("instance.deleting"))
        println(t("instance.deleted", name, instancesPath))
        true
    } catch (e: Exception) {
        println(t("instance.errorDeleting", name, e.message))
        false
    }
}

