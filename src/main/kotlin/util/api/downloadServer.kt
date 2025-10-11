package com.magnariuk.util.api

import com.magnariuk.util.Network
import com.magnariuk.util.interfaces.Download
import com.magnariuk.util.t
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest

suspend fun downloadServer(
    url: String,
    expectedSha: String,
    destination: File,
    headers: Map<String, String> = emptyMap(),
    onProgress: (Download) -> Unit = {},
    network: Network = Network()
): Boolean {
    try {
        val sha1 = MessageDigest.getInstance("SHA-1")

        val response = network.get(url, headers)
        val channel = response.bodyAsChannel()
        val sink = destination.sink().buffer()

        val contentLength = response.contentLength() ?: -1L
        var downloaded = 0L

        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(network.bufferSize)
            while (!packet.exhausted()) {
                val bytes = packet.readByteArray()
                sink.write(bytes)
                sha1.update(bytes)
                downloaded += bytes.size

                if (contentLength > 0) {
                    onProgress(
                        Download(
                            path = destination.path,
                            name = destination.name,
                            contentType = destination.extension,
                            contentLength = bytes.size.toLong(),
                            progress = downloaded.toFloat() / contentLength
                        )
                    )
                }
            }
        }

        sink.close()

        val downloadedSha = sha1.digest().joinToString("") { "%02x".format(it) }
        if (!downloadedSha.equals(expectedSha, ignoreCase = true)) {
            throw IllegalStateException(t("util.api.SHAMismatch", destination.name, expectedSha, downloadedSha))
        }

        println(t("util.api.SHAVerified", destination.name))
        return true

    } catch (e: Exception) {
        throw Exception(t("util.api.failedDownloading", url, e.message), e)
    }
}