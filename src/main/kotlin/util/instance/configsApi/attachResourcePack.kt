package com.magnariuk.util.instance.configsApi

import com.magnariuk.util.instance.editInstance
import com.magnariuk.util.instance.getInstance
import com.magnariuk.util.t
import java.nio.file.Path

fun attachResourcePack(
    instanceName: String,
    resourcePackValue: String,
    resourcePackPort: Int? = 2548
): Boolean {
    val instanceCfg = getInstance(instanceName)!!


    var rpValue = resourcePackValue
    val isUrl = rpValue.startsWith("http://") || rpValue.startsWith("https://")

    if (!isUrl) {
        val rpPath = Path.of(rpValue).toFile()
        if (!rpPath.isFile) {
            println(t("command.attach.subs.rpNotFound", resourcePackValue))
            return false
        }
        rpValue = rpPath.absolutePath
    }
    editInstance(
        name = instanceName,
        resourcepack = rpValue,
        resourcepackPort = resourcePackPort,
        isInternal = true
    )

    println(t("command.attach.subs.attached", instanceName))
    return true
}
