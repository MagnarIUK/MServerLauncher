package com.magnariuk.util.api

import com.magnariuk.util.t
import kotlinx.coroutines.runBlocking

fun checkMinecraftVersion(ver: String, apiMode: Boolean = false): Boolean {
    return when (ver.lowercase()) {
        "latest", "l", "snapshot", "s" -> true
        else -> {
            try {
                runBlocking { getVersion(ver) }
                if(!apiMode) println(t("util.api.versionFound", ver))
                true
            } catch (e: Exception) {
                if(!apiMode) println(t("util.api.versionDoesNotExist", ver, e.message))
                false
            }
        }
    }
}