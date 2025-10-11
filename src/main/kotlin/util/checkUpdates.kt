package com.magnariuk.util

import com.magnariuk.GITHUB_UPDATES
import com.magnariuk.MServerLauncher.BuildConfig
import com.magnariuk.data.configs.AppCache
import com.magnariuk.reset
import com.magnariuk.util.configs.readConfig
import com.magnariuk.yellow
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
)

fun isItTimeToCheckForAnUpdate(lastChecked: Long, interval: Long): Boolean {
    val now = Instant.now().toEpochMilli()
    val elapsedMilli = now - lastChecked

    return elapsedMilli >= interval
}


suspend fun getLatestRelease(network: Network = Network()): GitHubRelease = network.get(GITHUB_UPDATES).body<GitHubRelease>()
suspend fun checkUpdates(network: Network = Network(), justChecking: Boolean = false): String {
    val currentVersion = BuildConfig.APP_VERSION
    val cache = withContext(Dispatchers.IO) { AppCache.load() }

    if(isItTimeToCheckForAnUpdate(cache.update.lastChecked, readConfig().checkUpdateInterval)){
        try {
            val release = getLatestRelease(network)
            val latestTag = release.tagName.removePrefix("v")

            if(!justChecking){
                if (currentVersion != latestTag) {
                    println("\n$yellow${"#".repeat(50)}\n\tNEW VERSION (${latestTag}) IS AVAILABLE!\n\tYou currently on version $currentVersion.\n${"#".repeat(50)}$reset\n")
                } else {
                    println("\n$yellow${"#".repeat(50)}\n\tNEW VERSION (${latestTag}) IS NOT AVAILABLE!\n\tYou currently on version $currentVersion.\n${"#".repeat(50)}$reset\n")
                }
            }

            withContext(Dispatchers.IO) {
                cache.update {
                    update.lastChecked = Instant.now().toEpochMilli()
                    update.version = latestTag
                }
            }
            return latestTag
        } catch (e: Exception) {
            println(e)
        }
    }
    return cache.update.version
}