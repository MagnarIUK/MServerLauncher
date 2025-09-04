package com.magnariuk.util.interfaces

data class Download(
    val path: String,
    val name: String,
    val contentType: String,
    val contentLength: Long,
    val progress: Float
)

