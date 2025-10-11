package com.magnariuk.util.api

import com.magnariuk.util.Network
import com.magnariuk.util.t
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import java.io.File
import java.security.MessageDigest
import kotlinx.io.readByteArray

fun calculateFileSha1(filePath: String): String? {
    val file = File(filePath)
    if (!file.exists()) {
        println(t("util.api.fileNotFoundSha", filePath))
        return null
    }

    return try {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        println(t("util.api.errorCalculatingSha", filePath, e.message))
        null
    }
}

suspend fun calculateRemoteSha1(
    url: String,
    chunkSize: Int = 8192,
    network: Network = Network()
): String? {
    return try {
        val digest = MessageDigest.getInstance("SHA-1")
        val response = network.get(url, headers = emptyMap())
        val channel = response.bodyAsChannel()

        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(chunkSize.toLong())
            while (!packet.exhausted()) {
                val bytes = packet.readByteArray()
                digest.update(bytes)
            }
        }

        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        println(t("util.api.errorDownloadingFile",url, e.message))
        null
    }
}
