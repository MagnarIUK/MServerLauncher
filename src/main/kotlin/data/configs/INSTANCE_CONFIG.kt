package com.magnariuk.data.configs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class INSTANCE_CONFIG(
    @SerialName("cfg_version") val cfgVersion: Int = 2,
    @SerialName("version") val version: Version = Version(),
    @SerialName("name") val name: String = "",
    @SerialName("memory") val memory: String = "2048M",
    @SerialName("auto_backup") val autoBackup: Boolean = false,
    @SerialName("resourcepack") var resourcepack: String = "",
    @SerialName("resourcepack_port") var resourcepackPort: Int = 2548,
    @SerialName("backups") val backups: Map<String, Backup> = emptyMap(),
    @SerialName("modrinth") val modrinth: Map<String, String> = emptyMap(),
)

@Serializable
data class Version(
    @SerialName("minecraft") val minecraft: String = "latest",
    @SerialName("loader") val loader: Loader = Loader(),
)

@Serializable
data class Loader(
    @SerialName("type") val type: String = "vanilla",
    @SerialName("version") val version: String = "latest",
)

@Serializable
data class Backup(
    @SerialName("version") val version: String = "",
    @SerialName("datetime") val dateTime: String = "",
    @SerialName("desc") val desc: String = "",
)


