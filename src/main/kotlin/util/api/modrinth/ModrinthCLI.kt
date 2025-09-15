package com.magnariuk.util.api.modrinth

import com.magnariuk.util.Network
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

const val TIMEOUT = 3
val client = Network()

enum class SearchIndex(val value: String) {
    RELEVANCE("relevance"),
    DOWNLOADS("downloads"),
    FOLLOWS("follows"),
    NEWEST("newest"),
    UPDATED("updated"),
}

enum class Algorithm(val value: String) {
    SHA1("sha1"),
    SHA512("sha512"),
}

enum class Tag(val value: String) {
    CATEGORY("category"),
    LOADER("loader"),
    GAME_VERSION("game_version"),
    DONATION_PLATFORM("platform"),
    PROJECT_TYPE("project_type"),
    SIDE_TYPE("side_type"),
}

class Client(
    val token: String? = null,
    val endpoint: String = "https://api.modrinth.com",
){
    val headers: Map<String, String> = mapOf(
        "Authorization" to if (!token.isNullOrEmpty()) "Bearer $token" else ""
    )


    suspend fun getProject(projectId: String? = null, slug: String? = null): Project {
        if (projectId == null && slug == null) {
            throw IllegalArgumentException("project_id and slug cannot be both None.")
        }

        val url = "${endpoint }/v2/project/${projectId ?: slug}"
        val res = client.get(url, headers)

        return Json.decodeFromString<Project>(res.bodyAsText())
    }

    suspend fun getProjects(ids: List<String>): List<Project> {
        val url = "${endpoint }/v2/projects"


        val res = client.get(url, headers, mapOf( "ids" to Json.encodeToString(ids)))

        return Json.decodeFromString<List<Project>>(res.bodyAsText())
    }

    suspend fun searchProject(
        query: String? = null,
        facets: String? = null,
        index: String? = null,
        offset: Int? = null,
        limit: Int = 10,
    ): SearchResult {
        val url = "${endpoint}/v2/search"
        val res = client.get(url, headers, mapOf(
            "query" to query,
            "facets" to facets,
            "index" to index,
            "offset" to offset,
            "limit" to limit,
        ))
        return Json.decodeFromString<SearchResult>(res.bodyAsText())
    }

    suspend fun listProjectVersions(projectId: String): List<Version> {
        val url = "${endpoint }/v2/projects/$projectId/version"
        val res = client.get(url, headers)
        return Json.decodeFromString<List<Version>>(res.bodyAsText())
    }

    suspend fun getVersions(versionIds: List<String>): List<Version> {
        val url = "$endpoint/v2/versions"
        val res = client.get(url, headers, mapOf("ids" to Json.encodeToString(versionIds)))
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getVersionFromHash(
        sha1: String? = null,
        sha512: String? = null
    ): Version {
        if (sha1 == null && sha512 == null) {
            throw IllegalArgumentException("sha1 and sha512 cannot be both null.")
        }
        val url = "$endpoint/v2/version_file/${sha1 ?: sha512}"
        val res = client.get(
            url,
            headers,
            mapOf(
                "algorithm" to if (sha1 != null) "sha1" else "sha512",
                "multiple" to "false"
            )
        )
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getVersionsFromHashes(
        hashes: List<String>,
        algorithm: Algorithm
    ): Map<String, Version> {
        val url = "$endpoint/v2/version_files"
        val body = Json.encodeToString(
            mapOf(
                "hashes" to hashes,
                "algorithm" to algorithm.value
            )
        )
        val res = client.post(url, body, headers)
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getLatestVersionFromHash(
        sha1: String? = null,
        sha512: String? = null,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null
    ): Version {
        if (sha1 == null && sha512 == null) {
            throw IllegalArgumentException("sha1 and sha512 cannot be both null.")
        }
        val url = "$endpoint/v2/version_file/${sha1 ?: sha512}"
        val body = Json.encodeToString(
            mapOf(
                "loaders" to loaders,
                "game_versions" to gameVersions
            )
        )
        val res = client.get(
            url,
            headers,
            mapOf("algorithm" to if (sha1 != null) "sha1" else "sha512"),
            body
        )
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getLatestVersionsFromHashes(
        hashes: List<String>,
        algorithm: Algorithm,
        loaders: List<String>? = null,
        gameVersions: List<String>? = null
    ): Map<String, Version> {
        val url = "$endpoint/v2/version_files"
        val body = Json.encodeToString(
            mapOf(
                "hashes" to hashes,
                "algorithm" to algorithm.value,
                "loaders" to loaders,
                "game_versions" to gameVersions
            )
        )
        val res = client.post(url, body, headers)
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getTag(tag: Tag): Any {
        val url = "$endpoint/v2/tag/${tag.value}"
        val res = client.get(url, headers)
        return Json.decodeFromString(res.bodyAsText())
    }

    suspend fun getTag(tag: String): Any {
        val url = "$endpoint/v2/tag/$tag"
        val res = client.get(url, headers)
        return Json.decodeFromString(res.bodyAsText())
    }

}