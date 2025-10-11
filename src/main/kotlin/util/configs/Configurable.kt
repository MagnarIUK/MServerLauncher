@file:Suppress("UNCHECKED_CAST")

package com.magnariuk.util.configs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption


object DataLoader {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun <T> load(configFile: File, serializer: KSerializer<T>, default: () -> T): T {
        return if (configFile.exists()) {
            try {
                json.decodeFromString(serializer, configFile.readText())
            } catch (e: Exception) {
                println("Failed to load ${configFile.name}, using defaults. Error: ${e.message}")
                default()
            }
        } else {
            default()
        }
    }
}

interface Configurable<T> {
    val file: File
    fun serializer(): KSerializer<T>

    fun save() {
        val dataToSave = this as T
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

        val tmpFile = File(file.absolutePath + ".tmp")
        tmpFile.writeText(json.encodeToString(serializer(), dataToSave))
        Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun update(block: T.() -> Unit) {
        (this as T).block()
        save()
    }
}

interface ConfigurableCompanion<T> {
    val file: File
    fun serializer(): KSerializer<T>
    fun default(): T

    fun load(): T {
        return DataLoader.load(file, serializer(), { default() })
    }
}