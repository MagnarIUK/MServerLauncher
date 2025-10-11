package com.magnariuk.util.configs

import com.magnariuk.cacheFilePath
import com.magnariuk.configFilePath
import com.magnariuk.data.configs.AppCache
import com.magnariuk.data.configs.CONFIG
import com.magnariuk.util.I18n
import com.magnariuk.util.t
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*


fun editGlobalConfig(key: String? = null, value: String? = null) {
    val config = CONFIG.load()
    var updated = false

    if (key != null && value != null) {
        val prop = CONFIG::class.members.find { it.name == key } as? kotlin.reflect.KMutableProperty1<CONFIG, Any?>
        if (prop == null) {
            println(t("util.primitives.keyNotFound", mapOf("key" to key)))
        } else {
            try {
                val parsedValue = when (prop.returnType.classifier) {
                    Boolean::class -> value.toBoolean()
                    Int::class -> value.toInt()
                    Long::class -> value.toLong()
                    Double::class -> value.toDouble()
                    String::class -> value
                    else -> value
                }
                prop.set(config, parsedValue)
                updated = true
            } catch (e: Exception) {
                println("Failed to set value: ${e.message}")
            }
        }
    }

    if (updated) {
        config.save()
        I18n.setLocale(config.lang)
        println(t("util.primitives.updatedGlobal"))
    } else {
        println(t("util.primitives.noSettingsProvided"))
    }
}

