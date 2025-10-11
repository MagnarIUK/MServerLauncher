package com.magnariuk.util.instance.worldApi

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
import com.magnariuk.util.t
import java.nio.file.Path
import kotlin.io.path.div

fun resetWorld(instanceName: String) {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val worldFolder = (instancePath / "world").toFile()
    if (worldFolder.exists()) {
        worldFolder.deleteRecursivelyWithProgress(t("command.world.subs.deleting"))
    }
    println(t("command.world.subs.success", instanceName))
}