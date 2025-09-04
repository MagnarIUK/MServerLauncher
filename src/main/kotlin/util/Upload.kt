package com.magnariuk.util

import com.magnariuk.util.configs.readConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.random.Random

suspend fun uploadFile(file: Path): String? {
    val config = readConfig()
    if (!file.exists()) return null

    val client = HttpClient(CIO)
    try {
        println("Logging into the API...")

        val api = config.api
        val loginResponse: HttpResponse = client.submitForm(
            url = "${api}api/auth/login",
            formParameters = Parameters.build {
                append("username", config.apiLogin)
                append("password", config.apiPassword)
            }
        )
        if (!loginResponse.status.isSuccess()) {
            error("Login failed: ${loginResponse.status}")
        }

        println("Uploading file...")

        val password = generateUploadPassword()
        val expiryDate = generateExpiryDate()

        val uploadResponse: HttpResponse = client.submitFormWithBinaryData(
            url = "${api}api/files/upload",
            formData = formData {
                append("file", file.inputStream().readBytes(),
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"${file.fileName}\"")
                        append(HttpHeaders.ContentType, "application/zip")
                    }
                )
                append("expires_at", expiryDate)
                append("allow_direct_link", "true")
                append("create_short_link", "false")
                append("downloads_limit", "0")
                append("password", password)
            }
        )

        if (!uploadResponse.status.isSuccess()) {
            error("Upload failed: ${uploadResponse.status}")
        }

        val bodyText = uploadResponse.bodyAsText()
        val json = Json.parseToJsonElement(bodyText).jsonObject

        println(json["message"]?.jsonPrimitive?.content ?: "Upload done.")
        val fileToken = json["file_token"]?.jsonPrimitive?.content
            ?: error("No file token returned!")

        return "${api}api/files/direct/$fileToken.zip?password=$password"

    } finally {
        client.close()
    }
}

fun generateExpiryDate(days: Long = 30): String {
    val instant = Instant.now().plusSeconds(days * 24 * 60 * 60)
    val formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    return formatter.format(instant)
}

fun generateUploadPassword(n: Int = 14): String {
    val symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return buildString {
        repeat(n) {
            append(symbols[Random.nextInt(symbols.length)])
        }
    }
}