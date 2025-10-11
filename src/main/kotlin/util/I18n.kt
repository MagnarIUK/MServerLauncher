package com.magnariuk.util

import com.magnariuk.DEFAULT_LANGUAGE
import com.magnariuk.GITHUB_LANG_URL
import com.magnariuk.configFilePath
import com.magnariuk.configPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap


object I18n {
    private var currentLocale: String = DEFAULT_LANGUAGE
    private val translations: MutableMap<String, JsonObject> = ConcurrentHashMap()


    fun setLocale(lang: String) {
        if (!translations.containsKey(lang)) {
            println("Locale '$lang' not found locally, attempting download...")
            if(!downloadLocale(lang)) {
                println("Falling back to default ($DEFAULT_LANGUAGE)")
                currentLocale = DEFAULT_LANGUAGE
                return
            }
        }
        currentLocale = lang
    }
    fun getLocale(): String = currentLocale

    fun loadAllLocales(resourceDir: String = "lang"){
        loadBuiltInLocale(resourceDir)
        loadExternalLocales()
    }

    private fun loadExternalLocales(){
        val localesDir = ".lang"
        val localesPath = configPath.resolve(localesDir).toFile()

        val localeFiles = localesPath.listFiles().filter { it.extension == "json" }
        localeFiles.forEach { file ->
            val lang = file.nameWithoutExtension
            val text = file.readText()
            translations[lang] = Json.parseToJsonElement(text).jsonObject
        }
    }

    private fun loadBuiltInLocale(resourceDir: String){
        val classLoader = Thread.currentThread().contextClassLoader
        val resource = classLoader.getResource(resourceDir) ?: return
        val uri = resource.toURI()

        val baseDir = if (uri.scheme == "jar") {
            val fs = java.nio.file.FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fs.getPath(resourceDir)
        } else{
            java.nio.file.Paths.get(uri)
        }

        Files.list(baseDir).use { paths ->
            paths.filter { it.toString().endsWith(".json") }.forEach { path ->
                val lang = path.fileName.toString().substringBefore(".json")
                val text = Files.newBufferedReader(path).readText()
                translations[lang] = Json.parseToJsonElement(text).jsonObject
            }
        }
    }

    private fun downloadLocale(lang: String): Boolean {
        val url = "$GITHUB_LANG_URL/$lang.json"
        val localesDir = ".lang"
        val localesPath = configPath.resolve(localesDir).toFile()
        localesPath.mkdirs()
        val target = localesPath.resolve("$lang.json")

        try {
            val con = URI.create(url).toURL().openConnection()
            con.connectTimeout = 5000
            con.readTimeout = 5000

            con.getInputStream().use {input ->
                Files.copy(input, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            loadExternalLocales()
            return true
        } catch (e: Exception) {
            println("Failed to download language '$lang': ${e.message}")
            return false
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



    fun tr(key: String, vars: List<Any?> = emptyList(), lang: String? = null): String {
        val locale = lang ?: currentLocale
        val text = resolveNested(translations[locale] ?: JsonObject(emptyMap()), key)
            ?: resolveNested(translations[DEFAULT_LANGUAGE] ?: JsonObject(emptyMap()), key)
            ?: key

        return replaceVars(text, vars)
    }

    private fun replaceVars(text: String, vars: List<Any?>): String {
        var result = text
        vars.forEach { value ->
            result = result.replaceFirst("%s", value?.toString() ?: "null")
        }
        return result
    }

}

fun t(key: String, vars: List<Any?> = emptyList()): String {
    return I18n.tr(key, vars)
}
fun t(key: String, vararg vars: Any?): String {
    return I18n.tr(key, vars.toList())
}
