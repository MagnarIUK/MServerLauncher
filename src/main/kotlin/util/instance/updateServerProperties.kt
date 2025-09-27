package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import java.nio.file.Path

fun updateServerProperties(instanceName: String, key: String, value: String, internal: Boolean = false): Boolean {
    val instancesFolder = readConfig().instancesFolder
    val serverPropertiesPath = Path.of(instancesFolder, instanceName, "server.properties").toFile()

    if (!serverPropertiesPath.exists()) {
        println("Error: server.properties not found at '${serverPropertiesPath.path}'.")
        return false
    }

    val lines = serverPropertiesPath.readLines().toMutableList()
    val properties = mutableMapOf<String, String>()

    for (line in lines) {
        val stripped = line.trim()
        if (stripped.isNotEmpty() && '=' in stripped && !stripped.startsWith("#")) {
            val (k, v) = stripped.split('=', limit = 2)
            properties[k.trim()] = v.trim()
        }
    }

    if (key !in properties) {
        println("Key '$key' not found in server.properties. No changes made.")
        return false
    }

    if (key == "resource-pack" && !internal) {
        println("Resource pack was set in instance config. " +
            "In the future, use 'attach -rp' to add resource pack to the instance")
        return true
    }

    val newLines = lines.map { line ->
        val stripped = line.trim()
        if (stripped.startsWith("$key=") && !stripped.startsWith("#")) {
            "$key=$value"
        } else {
            line
        }
    }

    serverPropertiesPath.writeText(newLines.joinToString("\n") + "\n")
    println("Updated server.properties: $key=$value")
    return true
}
