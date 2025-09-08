package com.magnariuk.data.configs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

@Serializable
data class CONFIG(
    @SerialName("instances_folder") val instancesFolder: String = (Path(System.getProperty("user.home")) / ".minecraft/server_instances").absolutePathString(),
    @SerialName("api") val api: String = "",
    @SerialName("api_login") val apiLogin: String = "",
    @SerialName("api_password") val apiPassword: String = "",
    @SerialName("backup_on_rollback") val backupOnRollback: Boolean = true,
    @SerialName("launched") val launched: List<LAUNCHED_CONFIG> = emptyList(),
    @SerialName("default_editor") val defaultEditor: String = "",
    )