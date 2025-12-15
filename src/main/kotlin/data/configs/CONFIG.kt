package com.magnariuk.data.configs

import com.magnariuk.cacheFilePath
import com.magnariuk.configFilePath
import com.magnariuk.util.configs.Configurable
import com.magnariuk.util.configs.ConfigurableCompanion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

@Serializable
data class CONFIG(
    @SerialName("instances_folder") var instancesFolder: String = (Path(System.getProperty("user.home")) / ".minecraft/server_instances").absolutePathString(),
    @SerialName("api") var api: String = "",
    @SerialName("api_login") var apiLogin: String = "",
    @SerialName("api_password") var apiPassword: String = "",
    @SerialName("backup_on_rollback") var backupOnRollback: Boolean = true,
    @SerialName("launched") var launched: List<LAUNCHED_CONFIG> = emptyList(),
    @SerialName("default_editor") var defaultEditor: String = "",
    @SerialName("show_full_progress") var showFullProgress: Boolean = true,
    @SerialName("lang") var lang: String = "en",
    @SerialName("checkUpdateInterval") var checkUpdateInterval: Long = 3_600_000,
    @SerialName("logMaxLines") var logMaxLines: Int = 4,
    @SerialName("exec") var exec: String = "java",
) : Configurable<CONFIG> {
    override val file: File
        get() = configFilePath.toFile()

    override fun serializer(): KSerializer<CONFIG> = CONFIG.serializer()


    companion object: ConfigurableCompanion<CONFIG> {
        override val file: File
            get() = configFilePath.toFile()
        override fun default(): CONFIG = CONFIG()
    }
}
@Serializable
data class AppCache(
    @SerialName("update") var update: CacheGithubRelease = CacheGithubRelease(),
) : Configurable<AppCache> {
    override val file: File
        get() = cacheFilePath.toFile()

    override fun serializer(): KSerializer<AppCache> = AppCache.serializer()


    companion object: ConfigurableCompanion<AppCache> {
        override val file: File
            get() = cacheFilePath.toFile()
        override fun default(): AppCache = AppCache()
    }
}
@Serializable
data class CacheGithubRelease(
    @SerialName("version") var version: String = "",
    @SerialName("lastChecked") var lastChecked: Long = 0,
)