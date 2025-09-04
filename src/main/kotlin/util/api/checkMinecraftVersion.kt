package com.magnariuk.util.api

import kotlinx.coroutines.runBlocking

fun checkMinecraftVersion(ver: String): Boolean {
    return when (ver.lowercase()) {
        "latest", "l", "snapshot", "s" -> true
        else -> {
            try {
                runBlocking { getVersion(ver) }
                println("Version $ver is found")
                true
            } catch (e: Exception) {
                println("Version $ver does not exist: \n${e.message}")
                false
            }
        }
    }
}
