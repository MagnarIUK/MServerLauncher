package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import java.nio.file.Path

fun deleteInstance(name: String): Boolean {
    val cfg = readConfig()
    val instancesPath = Path.of(cfg.instancesFolder)
    val instancePath = instancesPath.resolve(name)

    if (!checkInstance(name)) {
        println("Error: Instance '$name' does not exist.")
        return false
    }

    print("Are you sure you want to delete instance '$name'? This action cannot be undone. (y/N): ")
    val input = readLine()?.trim()?.lowercase()
    if (input != "y" && input != "yes") {
        println("Deletion aborted.")
        return false
    }

    return try {
        instancePath.toFile().deleteRecursively()
        println("Instance '$name' deleted successfully from '$instancePath'.")
        true
    } catch (e: Exception) {
        println("Error deleting instance '$name': ${e.message}")
        false
    }
}

