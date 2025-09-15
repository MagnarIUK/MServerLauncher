package com.magnariuk.util.interfaces

import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import java.io.File

interface Network {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, Any?> = emptyMap(),
        body: Any? = null
    ): HttpResponse
    suspend fun post(
        url: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        contentType: ContentType = ContentType.Application.Json
    ): HttpResponse
    suspend fun put(
        url: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
        contentType: ContentType = ContentType.Application.Json
    ): HttpResponse
    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse
    suspend fun downloadFile(
        url: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Download) -> Unit = {}
    ): Boolean

}