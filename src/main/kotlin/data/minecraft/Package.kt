package com.magnariuk.data.minecraft

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class ArgumentValue {
    @Serializable
    @SerialName("string")
    data class StringValue(val value: String) : ArgumentValue()

    @Serializable
    @SerialName("dictionary")
    data class DictionaryValue(val value: Map<String, JsonElement>) : ArgumentValue()
}

@Serializable
data class Rule(
    val action: String,
    val os: OS?,
    val features: JsonElement? = null,
) {
    @Serializable
    data class OS(
        val name: String,
        val version: String,
        val arch: String,
    )
}


@Serializable
data class Package(
    val arguments: Arguments,
    val assetIndex: AssetIndex,
    val assets: String,
    val complianceLevel: Int,
    val downloads: Downloads,
    val id: String,
    val javaVersion: JavaVersion,
    val libraries: List<JsonElement>,
    val logging: Logging,
    val mainClass: String,
    val minimumLauncherVersion: Int,
    val releaseTime: String,
    val time: String,
    val type: String,
) {

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Arguments(
        val game: JsonElement,
        val jvm: JsonElement,
        @SerialName("default-user-jvm") val defaultUserJvm: JsonElement? = null,
    )
    @Serializable
    data class AssetIndex(
        val id: String,
        val sha1: String,
        val size: Int,
        val totalSize: Int,
        val url: String,
    )
    @Serializable
    data class Downloads(
        val client: Download,
        @SerialName("client_mappings")
        val clientMappings: Download? = null,
        val server: Download,
        @SerialName("server_mappings")
        val serverMappings: Download? = null,
        @SerialName("windows_server")
        val windowsServer: Download? =null
    ) {
        @Serializable
        data class Download(
            val id: String? = null,
            val sha1: String,
            val size: Int,
            val url: String,
        )
    }
    @Serializable
    data class JavaVersion(
        val component: String,
        val majorVersion: Int,
    )

    @Serializable
    data class Logging(
        val client: Client,
    ) {
        @Serializable
        data class Client(
            val argument: String,
            val file: Downloads.Download,
            val type: String,
        )
    }
}