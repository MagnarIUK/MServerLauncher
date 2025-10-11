package com.magnariuk.util.instance

import com.magnariuk.util.configs.readConfig
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

fun checkInstance(name: String): Boolean{
    val config = readConfig()
    val instancePath = Path(config.instancesFolder) / name
    return instancePath.exists() && instancePath.isDirectory() && (instancePath / "cfg.json").toFile().exists()
}