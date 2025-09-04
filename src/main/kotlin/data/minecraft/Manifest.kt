package com.magnariuk.data.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(
    val latest: ManifestLatest,
    val versions: List<ManifestVersion>
)
@Serializable
data class ManifestLatest(
    val release: String,
    val snapshot: String
)
@Serializable
data class ManifestVersion(
    val id: String,
    val type: String,
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String,
    val complianceLevel: Int,
)
