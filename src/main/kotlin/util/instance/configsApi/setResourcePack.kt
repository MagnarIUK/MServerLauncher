package com.magnariuk.util.instance.configsApi

import com.magnariuk.util.Network
import com.magnariuk.util.api.calculateRemoteSha1
import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.instance.getInstance

suspend fun setResourcePack(instanceName: String, network: Network = Network()): Boolean {
    val instanceCfg = getInstance(instanceName) ?: return false
    val rpValue = instanceCfg.resourcepack

    if (rpValue.isEmpty()) {
        println("No resource pack specified for this instance. Disabling resource pack settings in server.properties.")
        updateServerProperties(instanceName, "require-resource-pack", "false", internal = true)
        updateServerProperties(instanceName, "resource-pack", "",internal = true)
        updateServerProperties(instanceName, "resource-pack-sha1", "",internal = true)
        return true
    }

    val isUrl = rpValue.startsWith("http://") || rpValue.startsWith("https://")


    if (isUrl) {
        val rpSha1 = calculateRemoteSha1(rpValue, network = network)
        if (rpSha1 == null) {
            println("Failed to calculate resource pack SHA1 for remote file. Aborting attachment.")
            return false
        }

        updateServerProperties(instanceName, "resource-pack", rpValue,internal = true)
        updateServerProperties(instanceName, "resource-pack-sha1", rpSha1,internal = true)

        println("Resource pack configuration for '$instanceName' updated in server.properties.")
        return true

    } else {
        println("Resource pack should be a valid URL.")
        return false
    }
}
