package com.magnariuk.util.instance.worldApi

import com.magnariuk.util.configs.readConfig
import com.magnariuk.util.plugIns.deleteRecursivelyWithProgress
import java.nio.file.Path
import kotlin.io.path.div

fun resetWorld(instanceName: String) {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)
    val worldFolder = (instancePath / "world").toFile()
    if (worldFolder.exists()) {
        worldFolder.deleteRecursivelyWithProgress("Deleting world folder")
    }
    println("World for instance '$instanceName' has been removed")
}