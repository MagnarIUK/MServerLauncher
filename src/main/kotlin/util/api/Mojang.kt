package com.magnariuk.util.api

import com.magnariuk.data.minecraft.Manifest
import com.magnariuk.data.minecraft.Package
import com.magnariuk.util.Network
import com.magnariuk.util.VersionNotFoundException
import com.magnariuk.util.t
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

suspend fun getVersions(network: Network = Network()): Manifest {
    val url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    val response: HttpResponse = network.get(url)

    if (response.status == HttpStatusCode.OK) {
        val bodyText = response.bodyAsText()
        return json.decodeFromString<Manifest>(bodyText)
    } else {
        throw Exception(t("util.api.failedToFetchVersionsMinecraft", response.status))
    }
}
suspend fun getVersion(versionId: String, network: Network = Network()): Package {
    val manifest = getVersions(network)
    val versions = manifest.versions

    val ver = when (versionId.lowercase()) {
        "latest", "l" -> manifest.latest.release
        "snapshot", "s" -> manifest.latest.snapshot
        else -> versionId
    }

    val versionEntry = versions.firstOrNull { it.id == ver }
        ?: throw VersionNotFoundException(t("util.api.versionNotFound", ver))

    val response = network.get(versionEntry.url)
    if (response.status == HttpStatusCode.OK) {
        val bodyText = response.bodyAsText()
        return json.decodeFromString<Package>(bodyText)
    } else {
        throw Exception(t("util.api.failedToFetchVersion", ver, response.status))
    }
}
