package com.magnariuk.util.api

import com.magnariuk.util.Network
import com.magnariuk.util.t
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun getFabric(
    version: String,
    loader: String = "l",
    installer: String = "l",
    network: Network = Network()
): String? {
    val loaders = getFabricLoaderVersions(network)

    val installerResponse: HttpResponse = network.get("https://meta.fabricmc.net/v2/versions/installer")
    if (!installerResponse.status.isSuccess()) {
        println(t("util.api.failedFetchInstaller"))
        return null
    }
    val installers = Json.parseToJsonElement(installerResponse.bodyAsText()).jsonArray

    val loaderVer: FabricLoaderVersion? = when (loader.lowercase()) {
        "latest", "l" -> loaders.firstOrNull()
        else -> loaders.firstOrNull { it.version == loader }
    }
    if (loaderVer == null) {
        println(t("util.api.fabricLoaderNotFound", loader))
        return null
    }

    val installerVer: String? = when (installer.lowercase()) {
        "latest", "l" -> installers.firstOrNull()?.jsonObject?.get("version")?.jsonPrimitive?.content
        else -> installer
    }

    if (installerVer == null) {
        println(t("util.api.fabricInstallerNotFound", installer))
        return null
    }

    val loaderVersionStr = loaderVer.version
    val minecraftVersion = getVersion(version, network).id
    println(t("util.api.fabricVersion", minecraftVersion, loaderVersionStr, installerVer))

    val url = "https://meta.fabricmc.net/v2/versions/loader/$minecraftVersion/$loaderVersionStr/$installerVer/server/jar"
    return url
}
suspend fun getFabricLoaderVersions(network: Network = Network()): List<FabricLoaderVersion> {
    val loaderResponse: HttpResponse = network.get("https://meta.fabricmc.net/v2/versions/loader")
    if (!loaderResponse.status.isSuccess()) {
        throw Exception(t("util.api.failedFetchFabricLoader", loaderResponse.status))
    }
    val jsonArray = Json.decodeFromString<List<FabricLoaderVersion>>(loaderResponse.bodyAsText())
    return jsonArray
}
@Serializable
data class FabricLoaderVersion(
    val separator: String,
    val build: Int,
    val maven: String,
    val version: String,
    val stable: Boolean,
)

