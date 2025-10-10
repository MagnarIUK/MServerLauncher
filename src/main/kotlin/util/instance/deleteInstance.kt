package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
import com.magnariuk.util.prompt
import java.nio.file.Path

fun deleteInstance(name: String): Boolean {
    val cfg = readConfig()
    val instancesPath = Path.of(cfg.instancesFolder)
    val instancePath = instancesPath.resolve(name)


    val input = prompt("Are you sure you want to delete instance '$name'? This action cannot be undone. (y/N): ", "Aborting deletion.", 600000, "n")
    if (input != "y" && input != "yes") {
        println("Deletion aborted.")
        return false
    }

    return try {
        instancePath.toFile().deleteRecursivelyWithProgress("Deleting instance")
        println("Instance '$name' deleted successfully from '$instancePath'.")
        true
    } catch (e: Exception) {
        println("Error deleting instance '$name': ${e.message}")
        false
    }
}

