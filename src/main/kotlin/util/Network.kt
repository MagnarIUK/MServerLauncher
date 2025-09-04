package com.magnariuk.util

import com.magnariuk.util.interfaces.Download
import com.magnariuk.util.interfaces.Network
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import okio.buffer
import okio.sink
import java.io.File

class Network(
    val bufferSize: Long = DEFAULT_BUFFER_SIZE.toLong(),
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
): Network {
    override suspend fun get(
        url: String,
        headers: Map<String, String>
    ): HttpResponse {
        return client.get(url){
            headers.forEach { (k,v) -> header(k,v) }
        }
    }

    override suspend fun post(
        url: String,
        body: Any?,
        headers: Map<String, String>,
        contentType: ContentType
    ): HttpResponse {
        return client.post(url) {
            headers.forEach { (k,v) -> header(k,v) }
            contentType(contentType)
            if (body != null) setBody(body)
        }
    }

    override suspend fun put(
        url: String,
        body: Any?,
        headers: Map<String, String>,
        contentType: ContentType
    ): HttpResponse {
        return client.put(url) {
            headers.forEach { (k,v) -> header(k,v) }
            contentType(contentType)
            if (body != null) setBody(body)
        }
    }

    override suspend fun delete(
        url: String,
        headers: Map<String, String>
    ): HttpResponse {
        return client.delete(url) {
            headers.forEach { (k,v) -> header(k,v) }
        }
    }

    override suspend fun downloadFile(
        url: String,
        target: File,
        headers: Map<String, String>,
        onProgress: (Download) -> Unit
    ): Boolean {
        val response = get(url, headers)

        val channel = response.bodyAsChannel()
        val sink = target.sink().buffer()

        val contentLength = response.contentLength() ?: -1
        var downloaded = 0L

        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(bufferSize)
            while (!packet.exhausted()) {
                val bytes = packet.readByteArray()
                sink.write(bytes)
                downloaded += bytes.size
                if(contentLength > 0){
                    onProgress(Download(
                        path = target.path,
                        name = target.name,
                        contentType = target.extension,
                        contentLength = bytes.size.toLong(),
                        progress = downloaded.toFloat()
                    ))
                }
            }
        }

        sink.close()
        return true
    }


}