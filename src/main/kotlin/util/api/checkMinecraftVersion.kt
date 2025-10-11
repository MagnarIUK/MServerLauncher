package com.magnariuk.util.api

import com.magnariuk.util.t
import kotlinx.coroutines.runBlocking

fun checkMinecraftVersion(ver: String): Boolean {
    return when (ver.lowercase()) {
        "latest", "l", "snapshot", "s" -> true
        else -> {
            try {
                runBlocking { getVersion(ver) }
                println(t("util.api.versionFound", ver))
                true
            } catch (e: Exception) {
                println(t("util.api.versionDoesNotExist", ver, e.message))
                false
            }
        }
    }
}