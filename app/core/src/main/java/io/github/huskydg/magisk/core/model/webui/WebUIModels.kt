package io.github.huskydg.magisk.core.model.webui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * WebUI Module Configuration
 * Contains configuration for modules that support WebUI
 */
@Parcelize
data class WebUIModule(
    val moduleId: String,
    val moduleName: String,
    val webuiPort: Int = 8080,
    val webuiPath: String = "/",
    val webuiEnabled: Boolean = true,
    val autoStart: Boolean = false,
    val webuiUrl: String? = null,
    val webuiConfig: WebUIConfig? = null
) : Parcelable

/**
 * WebUI Configuration
 * Contains WebUI specific settings
 */
@Parcelize
data class WebUIConfig(
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val theme: WebUITheme = WebUITheme.AUTO,
    val fullscreen: Boolean = false,
    val allowRoot: Boolean = true,
    val allowFileAccess: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap(),
    val javascriptInterfaces: List<String> = emptyList()
) : Parcelable

/**
 * WebUI Theme Options
 */
@Parcelize
enum class WebUITheme : Parcelable {
    AUTO,       // Follow system theme
    LIGHT,      // Light theme
    DARK,       // Dark theme
    SYSTEM      // Use system theme
}

/**
 * WebUI Session
 * Represents an active WebUI session
 */
@Parcelize
data class WebUISession(
    val sessionId: String,
    val moduleId: String,
    val startTime: Long,
    val isActive: Boolean = true,
    val lastAccess: Long = System.currentTimeMillis(),
    val processId: Int? = null
) : Parcelable

/**
 * WebUI Command Result
 * Result of executing a command through WebUI
 */
@Parcelize
data class WebUICommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTime: Long
) : Parcelable

/**
 * WebUI File Info
 * Information about files accessible through WebUI
 */
@Parcelize
data class WebUIFileInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val permissions: String,
    val isReadable: Boolean = true,
    val isWritable: Boolean = false
) : Parcelable
