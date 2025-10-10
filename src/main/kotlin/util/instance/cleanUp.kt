package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import java.nio.file.Path
import kotlin.io.path.div

fun cleanUp(instanceName: String) {
    val cfg = readConfig()
    val instancePath = Path.of(cfg.instancesFolder, instanceName)

    val oldServerFile = (instancePath / "server.jar").toFile()
    if (oldServerFile.exists()) {
        oldServerFile.delete()
    }

}