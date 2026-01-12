package com.magnariuk.util.instance.configsApi

import com.magnariuk.util.Network
import com.magnariuk.util.api.calculateRemoteSha1
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.t

suspend fun setResourcePack(instanceName: String, network: Network = Network(), apiMode: Boolean = false): Boolean {
    val instanceCfg = getInstance(instanceName) ?: return false
    val rpValue = instanceCfg.resourcepack

    if (rpValue.isEmpty()) {
        if(!apiMode)println(t("command.attach.subs.disablingResourcePack"))
        updateServerProperties(instanceName, "require-resource-pack", "false", internal = true)
        updateServerProperties(instanceName, "resource-pack", "", internal = true)
        updateServerProperties(instanceName, "resource-pack-sha1", "", internal = true)
        return true
    }

    val isUrl = rpValue.startsWith("http://") || rpValue.startsWith("https://")


    if (isUrl) {
        val rpSha1 = calculateRemoteSha1(rpValue, network = network)
        if (rpSha1 == null) {
            if(!apiMode)println(t("command.attach.subs.failedToCalculateSHA"))
            return false
        }

        updateServerProperties(instanceName, "resource-pack", rpValue, internal = true)
        updateServerProperties(instanceName, "resource-pack-sha1", rpSha1, internal = true)

        if(!apiMode)println(t("command.attach.subs.propertiesUpdated", instanceName))
        return true

    } else {
        println(t("command.attach.subs.notValidUrl"))
        return false
    }
}