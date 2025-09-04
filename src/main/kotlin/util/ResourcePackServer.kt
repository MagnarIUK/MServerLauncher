package com.magnariuk.util

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import kotlinx.coroutines.*

object ResourcePackServer {
    private var server: HttpServer? = null
    private var serverJob: Job? = null

    fun start(filePath: String, ip: String = "0.0.0.0", port: Int = 2548): Boolean {
        val rpFile = File(filePath)
        if (!rpFile.isFile) {
            println("Error: Resource pack file not found at '$filePath'. Cannot start HTTP server.")
            return false
        }

        val directory = rpFile.parentFile ?: return false

        try {
            server = HttpServer.create(InetSocketAddress(ip, port), 0)
            server?.createContext("/") { exchange ->
                handleRequest(exchange, directory)
            }
            server?.executor = null

            serverJob = CoroutineScope(Dispatchers.IO).launch {
                server?.start()
                println("Resource pack HTTP server serving files from '${directory.absolutePath}' at http://$ip:$port")
            }
            return true
        } catch (e: Exception) {
            println("Error starting HTTP server on $ip:$port: ${e.message}")
            return false
        }
    }

    private fun handleRequest(exchange: HttpExchange, directory: File) {
        try {
            val requestedPath = directory.resolve(exchange.requestURI.path.removePrefix("/"))
            if (requestedPath.exists() && requestedPath.isFile) {
                val bytes = requestedPath.readBytes()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                val os: OutputStream = exchange.responseBody
                os.write(bytes)
                os.close()
            } else {
                val notFound = "File not found".toByteArray()
                exchange.sendResponseHeaders(404, notFound.size.toLong())
                exchange.responseBody.write(notFound)
                exchange.responseBody.close()
            }
        } catch (_: Exception) {
            exchange.sendResponseHeaders(500, -1)
        }
    }

    fun stop() {
        if (server != null) {
            println("Stopping resource pack HTTP server...")
            server?.stop(0)
            server = null
        }
        serverJob?.cancel()
        serverJob = null
    }
}
