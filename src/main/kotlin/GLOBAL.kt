package com.magnariuk

import java.nio.file.Path


val configPath: Path = Path.of(System.getProperty("user.home"))
    .resolve(".minecraft/server_instances/.config.json")
const val DEFAULT_LANGUAGE = "en"