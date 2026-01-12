package com.magnariuk.util.instance.configsApi

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.t
import java.nio.file.Path

fun updateServerProperties(instanceName: String, key: String, value: String, internal: Boolean = false): Boolean {
    val instancesFolder = readConfig().instancesFolder
    val serverPropertiesPath = Path.of(instancesFolder, instanceName, "server.properties").toFile()

    if (!serverPropertiesPath.exists()) {
        if(!internal) println(t("command.sp.fileNotExists", instanceName))
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
        if(!internal) println(t("command.sp.keyNotFound", key))
        return false
    }

    if (key == "resource-pack" && !internal) {
        println(t("command.sp.rpWarning"))
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
    if(!internal) println(t("command.sp.updated", key, value))
    return true
}