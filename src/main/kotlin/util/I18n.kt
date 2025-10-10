package com.magnariuk.util

import com.magnariuk.DEFAULT_LANGUAGE
import com.magnariuk.util.configs.readConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap


object I18n {
    private var currentLocale: String = DEFAULT_LANGUAGE
    private val translations: MutableMap<String, JsonObject> = ConcurrentHashMap()


    fun setLocale(lang: String) {
        currentLocale = lang
    }
    fun getLocale(): String = currentLocale

    fun loadAllLocales(resourceDir: String = "lang"){

        val classLoader = Thread.currentThread().contextClassLoader
        val resource = classLoader.getResource(resourceDir) ?: return
        val uri = resource.toURI()

        val baseDir = if (uri.scheme == "jar") {
            val fs = java.nio.file.FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fs.getPath(resourceDir)
        } else{
            java.nio.file.Paths.get(uri)
        }

        java.nio.file.Files.list(baseDir).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.forEach { path ->
                val lang = path.fileName.toString().substringBefore(".json")
                val text = java.nio.file.Files.newBufferedReader(path).readText()
                translations[lang] = Json.parseToJsonElement(text).jsonObject
            }
        }
    }
    private fun resolveNested(data: JsonObject, key: String): String? {
        var current: JsonElement? = data
        for (part in key.split(".")) {
            current = (current as? JsonObject)?.get(part) ?: return null
        }

        return when {
            current is JsonPrimitive -> current.contentOrNull
            current is JsonObject || current is JsonArray -> current.toString()
            else -> null
        }
    }



    fun tr(key: String, vars: Map<String, Any?> = emptyMap(), lang: String? = null): String {
        val locale = lang ?: currentLocale
        val text = resolveNested(translations[locale] ?: JsonObject(emptyMap()), key)
            ?: resolveNested(translations[DEFAULT_LANGUAGE] ?: JsonObject(emptyMap()), key)
            ?: key

        return text
    }

    private fun replaceVars(text: String, vars: Map<String, Any?>): String {
        var result = text
        vars.forEach { (k, v) ->
            result = result.replace("{$k}", v?.toString() ?: "")
        }
        return result
    }
}

fun t(key: String, vars: Map<String, Any?> = emptyMap()): String {
    return I18n.tr(key, vars)
}