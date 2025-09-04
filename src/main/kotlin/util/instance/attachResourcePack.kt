package com.magnariuk.util.instance

import java.nio.file.Path

fun attachResourcePack(
    instanceName: String,
    resourcePackValue: String,
    resourcePackPort: Int? = 2548
): Boolean {
    val instanceCfg = getInstance(instanceName)
    if (instanceCfg == null) {
        println("Error: Instance '$instanceName' does not exist. Please create it first.")
        return false
    }

    var rpValue = resourcePackValue
    val isUrl = rpValue.startsWith("http://") || rpValue.startsWith("https://")

    if (!isUrl) {
        val rpPath = Path.of(rpValue).toFile()
        if (!rpPath.isFile) {
            println("Error: Resource pack file '$resourcePackValue' not found.")
            return false
        }
        rpValue = rpPath.absolutePath
    }
    editInstance(
        name = instanceName,
        resourcepack = rpValue,
        resourcepackPort = resourcePackPort ?: instanceCfg.resourcepackPort
    )

    println("Resource pack information attached to '$instanceName' successfully.")
    return true
}
