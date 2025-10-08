package io.github.huskydg.magisk.core.repository

import io.github.huskydg.magisk.core.Config
import io.github.huskydg.magisk.core.Config.Value.BETA_CHANNEL
import io.github.huskydg.magisk.core.Config.Value.CANARY_CHANNEL
import io.github.huskydg.magisk.core.Config.Value.CUSTOM_CHANNEL
import io.github.huskydg.magisk.core.Config.Value.DEBUG_CHANNEL
import io.github.huskydg.magisk.core.Config.Value.DEFAULT_CHANNEL
import io.github.huskydg.magisk.core.Config.Value.STABLE_CHANNEL
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.data.GithubPageServices
import io.github.huskydg.magisk.core.data.RawServices
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

class NetworkService(
    private val pages: GithubPageServices,
    private val raw: RawServices
) {
    suspend fun fetchUpdate() = safe {
        var info = when (Config.updateChannel) {
            DEFAULT_CHANNEL, STABLE_CHANNEL -> fetchStableUpdate()
            BETA_CHANNEL -> fetchBetaUpdate()
            CANARY_CHANNEL -> fetchCanaryUpdate()
            DEBUG_CHANNEL -> fetchDebugUpdate()
            CUSTOM_CHANNEL -> fetchCustomUpdate(Config.customChannelUrl)
            else -> throw IllegalArgumentException()
        }
        if (info.magisk.versionCode < Info.env.versionCode &&
            Config.updateChannel == DEFAULT_CHANNEL) {
            Config.updateChannel = BETA_CHANNEL
            info = fetchBetaUpdate()
        }
        info
    }

    // UpdateInfo
    private suspend fun fetchStableUpdate() = pages.fetchUpdateJSON("stable.json")
    private suspend fun fetchBetaUpdate() = pages.fetchUpdateJSON("beta.json")
    private suspend fun fetchCanaryUpdate() = pages.fetchUpdateJSON("canary.json")
    private suspend fun fetchDebugUpdate() = pages.fetchUpdateJSON("debug.json")
    private suspend fun fetchCustomUpdate(url: String) = pages.fetchUpdateJSON(url)

    private inline fun <T> safe(factory: () -> T): T? {
        return try {
            if (Info.isConnected.value == true)
                factory()
            else
                null
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    private inline fun <T> wrap(factory: () -> T): T {
        return try {
            factory()
        } catch (e: HttpException) {
            throw IOException(e)
        }
    }

    // Modules related
    suspend fun fetchRepoInfo(url: String = io.github.huskydg.magisk.core.Const.Url.OFFICIAL_REPO) = safe {
        raw.fetchRepoInfo(url)
    }

    // Fetch files
    suspend fun fetchFile(url: String) = wrap { raw.fetchFile(url) }
    suspend fun fetchString(url: String) = wrap { raw.fetchString(url) }
    suspend fun fetchModuleJson(url: String) = wrap { raw.fetchModuleJson(url) }
}
