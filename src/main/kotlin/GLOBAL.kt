package com.magnariuk

import java.nio.file.Path

val configPath: Path = Path.of(System.getProperty("user.home"))
    .resolve(".minecraft/server_instances")
val configFilePath: Path = configPath.resolve(".config.json")
const val DEFAULT_LANGUAGE = "en"
const val GITHUB_LANG_URL = "IN PROGRESS"