package com.magnariuk.util.api

import com.magnariuk.data.minecraft.Manifest
import com.magnariuk.data.minecraft.Package
import com.magnariuk.util.Network
import com.magnariuk.util.VersionNotFoundException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

suspend fun getVersions(network: Network = Network()): Manifest {
    val url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    val response: HttpResponse = network.get(url)

    if (response.status == HttpStatusCode.OK) {
        val bodyText = response.bodyAsText()
        return Json.decodeFromString<Manifest>(bodyText)
    } else {
        throw Exception("Failed to fetch versions: ${response.status}")
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
        ?: throw VersionNotFoundException("Version '$ver' not found.")

    val response = network.get(versionEntry.url)
    if (response.status == HttpStatusCode.OK) {
        val bodyText = response.bodyAsText()
        return Json.decodeFromString<Package>(bodyText)
    } else {
        throw Exception("Failed to fetch version '$ver': ${response.status}")
    }
}

