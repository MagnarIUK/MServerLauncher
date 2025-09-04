package com.magnariuk.data.configs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class LAUNCHED_CONFIG(
    @SerialName("instance") val instance: String = "",
    @SerialName("pid") val pid: Int = 0,
    @SerialName("launched_at") val launchedAt: String = Instant.now().toString(),
)
