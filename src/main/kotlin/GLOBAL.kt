package com.magnariuk

import java.nio.file.Path

val configPath: Path = Path.of(System.getProperty("user.home"))
    .resolve(".minecraft/server_instances")
val configFilePath: Path = configPath.resolve(".config.json")
val cacheFilePath: Path = configPath.resolve(".cache.json")
const val DEFAULT_LANGUAGE = "en"
const val GITHUB_LANG_URL = "https://raw.githubusercontent.com/MagnarIUK/MServerLauncher/refs/heads/master/lang/"
const val GITHUB_UPDATES = "https://api.github.com/repos/MagnarIUK/MServerLauncher/releases/latest"
const val GET_GITHUB_UPDATE = "https://github.com/MagnarIUK/MServerLauncher/releases/latest"
const val yellow = "\u001B[33m"
const val reset = "\u001B[0m"