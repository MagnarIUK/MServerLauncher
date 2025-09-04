package com.magnariuk.util.instance

import com.magnariuk.util.Network
import com.magnariuk.util.ResourcePackServer
import com.magnariuk.util.api.calculateFileSha1
import com.magnariuk.util.api.calculateRemoteSha1
import com.magnariuk.util.configs.readConfig
import java.nio.file.Path

suspend fun setResourcePack(instanceName: String, network: Network = Network()): Boolean {
    val config = readConfig()
    val instanceCfg = getInstance(instanceName) ?: return false
    val rpValue = instanceCfg.resourcepack

    if (rpValue.isEmpty()) {
        println("No resource pack specified for this instance. Disabling resource pack settings in server.properties.")
        updateServerProperties(instanceName, "require-resource-pack", "false")
        updateServerProperties(instanceName, "resource-pack", "")
        updateServerProperties(instanceName, "resource-pack-sha1", "")
        return true
    }

    val isUrl = rpValue.startsWith("http://") || rpValue.startsWith("https://")
    val rpUrl: String
    val rpSha1: String?

    if (isUrl) {
        rpUrl = rpValue
        println("Resource pack is a URL: $rpUrl")
        println("No local hosting required.")

        rpSha1 = calculateRemoteSha1(rpUrl, network = network)
        if (rpSha1 == null) {
            println("Failed to calculate resource pack SHA1 for remote file. Aborting attachment.")
            return false
        }
    } else {
        val rpPath = Path.of(rpValue).toFile()
        if (!rpPath.isFile) {
            println("Error: Resource pack file '${rpPath.absolutePath}' not found. Cannot attach.")
            return false
        }

        rpSha1 = calculateFileSha1(rpPath.absolutePath)
        if (rpSha1 == null) {
            println("Failed to calculate resource pack SHA1 for local file. Aborting attachment.")
            return false
        }


        rpUrl = "http://0.0.0.0:${instanceCfg.resourcepackPort}/${rpPath.name}"
        println("Constructed resource pack URL for server.properties (local file): $rpUrl")

        println("Resource pack '${rpPath.name}' attached to instance '$instanceName'.")
        println("Minecraft clients will attempt to download it from: $rpUrl")

        println("Starting local HTTP server for resource pack: ${rpPath.name}")
        if (!ResourcePackServer.start(rpPath.absolutePath, ip = "0.0.0.0", port = instanceCfg.resourcepackPort)) {
            println("Failed to start resource pack HTTP server. Server might not function correctly regarding resource packs.")
        }
    }

    updateServerProperties(instanceName, "resource-pack", rpUrl)
    updateServerProperties(instanceName, "resource-pack-sha1", rpSha1)

    println("Resource pack configuration for '$instanceName' updated in server.properties.")
    return true
}
