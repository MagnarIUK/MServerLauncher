package com.magnariuk.data.minecraft

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(
    val latest: ManifestLatest,
    val versions: List<ManifestVersion>
){
    fun getList(): List<String>{
        val list = mutableListOf<String>()
        versions.forEach{version ->
            list.add("latest")
            list.add("snapshot")
            list.add(version.id)
        }
        return list
    }
}
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
