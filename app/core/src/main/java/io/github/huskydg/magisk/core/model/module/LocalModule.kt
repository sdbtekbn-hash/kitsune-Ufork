package io.github.huskydg.magisk.core.model.module

import com.squareup.moshi.JsonDataException
import io.github.huskydg.magisk.core.Const
import io.github.huskydg.magisk.core.di.ServiceLocator
import io.github.huskydg.magisk.core.utils.RootUtils
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.nio.ExtendedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale

data class LocalModule(
    private val base: ExtendedFile,
) : Module() {
    private val svc get() = ServiceLocator.networkService

    override var id: String = ""
    override var name: String = ""
    override var version: String = ""
    override var versionCode: Int = -1
    var author: String = ""
    var description: String = ""
    var updateInfo: OnlineModule? = null
    var outdated = false
    private var updateUrl: String = ""
    
    // Enhanced fields for local modules
    var icon: String? = null
    var cover: String? = null
    var screenshots: List<String>? = null
    var homepage: String? = null
    var donate: String? = null
    var support: String? = null
    var license: String? = null
    var readme: String? = null
    var categories: List<String>? = null
    var verified: Boolean? = null
    
    // WebUI support
    var webuiPort: Int = 8080
    var webuiPath: String = "/"
    var webuiEnabled: Boolean = true

    private val removeFile = base.getChildFile("remove")
    private val disableFile = base.getChildFile("disable")
    private val updateFile = base.getChildFile("update")
    val zygiskFolder = base.getChildFile("zygisk")

    val updated get() = updateFile.exists()
    val isRiru = (id == "riru-core") || base.getChildFile("riru").exists()
    val isZygisk = zygiskFolder.exists()
    val zygiskUnloaded = zygiskFolder.getChildFile("unloaded").exists()
    val hasAction = base.getChildFile("action.sh").exists()
    
    // Enhanced properties for local modules
    val hasIcon get() = !icon.isNullOrBlank() || base.getChildFile("icon.png").exists()
    val hasCover get() = !cover.isNullOrBlank() || base.getChildFile("cover.png").exists()
    val hasScreenshots get() = !screenshots.isNullOrEmpty() || base.getChildFile("screenshots").exists()
    val hasHomepage get() = !homepage.isNullOrBlank()
    val hasDonate get() = !donate.isNullOrBlank()
    val hasSupport get() = !support.isNullOrBlank()
    val hasLicense get() = !license.isNullOrBlank() && license != "UNKNOWN"
    val hasReadme get() = !readme.isNullOrBlank() || base.getChildFile("README.md").exists()
    val hasCategories get() = !categories.isNullOrEmpty()
    val isVerified get() = verified == true
    
    // Get local icon path
    val localIconPath get() = if (base.getChildFile("icon.png").exists()) {
        base.getChildFile("icon.png").absolutePath
    } else null
    
    // Get local cover path
    val localCoverPath get() = if (base.getChildFile("cover.png").exists()) {
        base.getChildFile("cover.png").absolutePath
    } else null
    
    // Get local screenshots
    val localScreenshots get() = base.getChildFile("screenshots")
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
        .map { it.absolutePath }
    
    // WebUI properties
    val hasWebUI get() = webuiEnabled && File(base, "webui.sh").exists()
    val webuiScriptPath get() = File(base, "webui.sh").absolutePath

    var enable: Boolean
        get() = !disableFile.exists()
        set(enable) {
            if (enable) {
                disableFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                !disableFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    var remove: Boolean
        get() = removeFile.exists()
        set(remove) {
            if (remove) {
                if (updateFile.exists()) return
                removeFile.createNewFile()
                Shell.cmd("copy_preinit_files").submit()
            } else {
                removeFile.delete()
                Shell.cmd("copy_preinit_files").submit()
            }
        }

    @Throws(NumberFormatException::class)
    private fun parseProps(props: List<String>) {
        for (line in props) {
            val prop = line.split("=".toRegex(), 2).map { it.trim() }
            if (prop.size != 2)
                continue

            val key = prop[0]
            val value = prop[1]
            if (key.isEmpty() || key[0] == '#')
                continue

            when (key) {
                "id" -> id = value
                "name" -> name = value
                "version" -> version = value
                "versionCode" -> versionCode = value.toInt()
                "author" -> author = value
                "description" -> description = value
                "updateJson" -> updateUrl = value
                "icon" -> icon = value
                "cover" -> cover = value
                "homepage" -> homepage = value
                "donate" -> donate = value
                "support" -> support = value
                "license" -> license = value
                "readme" -> readme = value
                "verified" -> verified = value.toBoolean()
                "categories" -> categories = value.split(",").map { it.trim() }
                "webui_port" -> webuiPort = value.toIntOrNull() ?: 8080
                "webui_path" -> webuiPath = value
                "webui_enabled" -> webuiEnabled = value.toBoolean()
            }
        }
    }

    init {
        runCatching {
            parseProps(Shell.cmd("dos2unix < $base/module.prop").exec().out)
        }

        if (id.isEmpty()) {
            id = base.name
        }

        if (name.isEmpty()) {
            name = id
        }
    }

    suspend fun fetch(): Boolean {
        if (updateUrl.isEmpty())
            return false

        try {
            val json = svc.fetchModuleJson(updateUrl)
            updateInfo = OnlineModule(this, json)
            outdated = json.versionCode > versionCode
            return true
        } catch (e: IOException) {
            Timber.w(e)
        } catch (e: JsonDataException) {
            Timber.w(e)
        }

        return false
    }

    companion object {

        fun loaded() = RootUtils.fs.getFile(Const.MODULE_PATH).exists()

        suspend fun installed() = withContext(Dispatchers.IO) {
            RootUtils.fs.getFile(Const.MODULE_PATH)
                .listFiles()
                .orEmpty()
                .filter { !it.isFile && !it.isHidden }
                .map { LocalModule(it) }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        }
    }
}
