package io.github.huskydg.magisk.core.model.module

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.huskydg.magisk.core.model.RepoModuleInfo
import io.github.huskydg.magisk.core.repository.NetworkService
import io.github.huskydg.magisk.di.ServiceLocator
import kotlinx.parcelize.Parcelize
import java.text.DateFormat
import java.util.*

@Entity(tableName = "modules")
@Parcelize
data class OnlineModule(
    @PrimaryKey override var id: String,
    override var name: String = "",
    override var author: String = "",
    override var version: String = "",
    override var versionCode: Int = -1,
    override var description: String = "",
    val last_update: Long,
    val prop_url: String,
    val zip_url: String,
    val notes_url: String,
    
    // Enhanced fields
    val categories: List<String>? = null,
    val icon: String? = null,
    val cover: String? = null,
    val screenshots: List<String>? = null,
    val homepage: String? = null,
    val donate: String? = null,
    val support: String? = null,
    val license: String? = null,
    val readme: String? = null,
    val verified: Boolean? = null,
    val size: Int? = null,
    val minApi: Int? = null,
    val maxApi: Int? = null,
    val require: List<String>? = null,
    val permissions: List<String>? = null,
    val devices: List<String>? = null,
    val arch: List<String>? = null
) : Module(), Parcelable {

    constructor(info: RepoModuleInfo) : this(
        id = info.id,
        last_update = info.last_update,
        prop_url = info.prop_url,
        zip_url = info.zip_url,
        notes_url = info.notes_url
    )

    val lastUpdate get() = Date(last_update)
    val lastUpdateString: String get() = DATE_FORMAT.format(lastUpdate)
    val downloadFilename get() = "$name-$version($versionCode).zip".legalFilename()
    
    // Enhanced properties
    val hasIcon get() = !icon.isNullOrBlank()
    val hasCover get() = !cover.isNullOrBlank()
    val hasScreenshots get() = !screenshots.isNullOrEmpty()
    val hasHomepage get() = !homepage.isNullOrBlank()
    val hasDonate get() = !donate.isNullOrBlank()
    val hasSupport get() = !support.isNullOrBlank()
    val hasLicense get() = !license.isNullOrBlank() && license != "UNKNOWN"
    val hasReadme get() = !readme.isNullOrBlank()
    val hasCategories get() = !categories.isNullOrEmpty()
    val hasRequire get() = !require.isNullOrEmpty()
    val hasPermissions get() = !permissions.isNullOrEmpty()
    val hasDevices get() = !devices.isNullOrEmpty()
    val hasArch get() = !arch.isNullOrEmpty()
    val isVerified get() = verified == true
    val hasSize get() = size != null
    val sizeFormatted get() = if (hasSize) formatFileSize(size!!) else null

    suspend fun notes(): String {
        val svc: NetworkService = ServiceLocator.networkService
        return try {
            svc.fetchString(notes_url)
        } catch (e: Exception) {
            ""
        }
    }

    @Throws(IllegalRepoException::class)
    suspend fun load() {
        val svc: NetworkService = ServiceLocator.networkService
        try {
            val rawProps = svc.fetchString(prop_url)
            val props = rawProps.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
            parseProps(props)
        } catch (e: Exception) {
            throw IllegalRepoException("Repo [$id] parse error:", e)
        }

        if (versionCode < 0) {
            throw IllegalRepoException("Repo [$id] does not contain versionCode")
        }
    }

    class IllegalRepoException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

    companion object {
        private val DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    }

    private fun String.legalFilename() = replace(" ", "_")
        .replace("'", "").replace("\"", "")
        .replace("$", "").replace("`", "")
        .replace("*", "").replace("/", "_")
        .replace("#", "").replace("@", "")
        .replace("\\", "_")
    
    private fun formatFileSize(bytes: Int): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> "%.1f MB".format(mb)
            kb >= 1 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }
}
