package io.github.huskydg.magisk.core.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.github.huskydg.magisk.core.model.webui.WebUISession
import io.github.huskydg.magisk.core.model.webui.WebUIModule
import io.github.huskydg.magisk.core.model.webui.WebUICommandResult
import io.github.huskydg.magisk.core.model.webui.WebUIFileInfo
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebUI Service
 * Manages WebUI sessions and provides backend functionality
 */
class WebUIService : Service() {
    
    companion object {
        private const val TAG = "WebUIService"
        private const val DEFAULT_PORT = 8080
        private const val MAX_SESSIONS = 10
    }
    
    private val binder = WebUIBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Session management
    private val activeSessions = ConcurrentHashMap<String, WebUISession>()
    private val sessionCounter = AtomicInteger(0)
    
    // Module management
    private val webuiModules = ConcurrentHashMap<String, WebUIModule>()
    
    inner class WebUIBinder : Binder() {
        fun getService(): WebUIService = this@WebUIService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WebUI Service created")
        initializeWebUIModules()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WebUI Service destroyed")
        serviceScope.cancel()
        cleanupAllSessions()
    }
    
    /**
     * Initialize WebUI modules from installed modules
     */
    private fun initializeWebUIModules() {
        serviceScope.launch {
            try {
                // Scan installed modules for WebUI support
                val modulesDir = File("/data/adb/modules")
                if (modulesDir.exists()) {
                    modulesDir.listFiles()?.forEach { moduleDir ->
                        if (moduleDir.isDirectory) {
                            val moduleId = moduleDir.name
                            val webuiConfig = checkModuleWebUISupport(moduleDir)
                            if (webuiConfig != null) {
                                webuiModules[moduleId] = webuiConfig
                                Log.d(TAG, "Found WebUI module: $moduleId")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing WebUI modules", e)
            }
        }
    }
    
    /**
     * Check if a module supports WebUI
     */
    private fun checkModuleWebUISupport(moduleDir: File): WebUIModule? {
        try {
            val webuiProp = File(moduleDir, "webui.prop")
            if (webuiProp.exists()) {
                val props = webuiProp.readText().split("\n")
                val moduleName = moduleDir.name
                var port = DEFAULT_PORT
                var path = "/"
                var enabled = true
                
                props.forEach { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        when (parts[0].trim()) {
                            "port" -> port = parts[1].trim().toIntOrNull() ?: DEFAULT_PORT
                            "path" -> path = parts[1].trim()
                            "enabled" -> enabled = parts[1].trim().toBoolean()
                        }
                    }
                }
                
                return WebUIModule(
                    moduleId = moduleDir.name,
                    moduleName = moduleName,
                    webuiPort = port,
                    webuiPath = path,
                    webuiEnabled = enabled
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WebUI support for ${moduleDir.name}", e)
        }
        return null
    }
    
    /**
     * Start a WebUI session for a module
     */
    fun startWebUISession(moduleId: String): String? {
        val module = webuiModules[moduleId] ?: return null
        
        if (activeSessions.size >= MAX_SESSIONS) {
            Log.w(TAG, "Maximum sessions reached")
            return null
        }
        
        val sessionId = "webui_${moduleId}_${sessionCounter.incrementAndGet()}"
        val session = WebUISession(
            sessionId = sessionId,
            moduleId = moduleId,
            startTime = System.currentTimeMillis()
        )
        
        activeSessions[sessionId] = session
        
        serviceScope.launch {
            try {
                startWebUIServer(module, session)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting WebUI server for $moduleId", e)
                activeSessions.remove(sessionId)
            }
        }
        
        Log.d(TAG, "Started WebUI session: $sessionId")
        return sessionId
    }
    
    /**
     * Stop a WebUI session
     */
    fun stopWebUISession(sessionId: String): Boolean {
        val session = activeSessions.remove(sessionId) ?: return false
        
        serviceScope.launch {
            try {
                stopWebUIServer(session)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping WebUI server for $sessionId", e)
            }
        }
        
        Log.d(TAG, "Stopped WebUI session: $sessionId")
        return true
    }
    
    /**
     * Start WebUI server for a module
     */
    private suspend fun startWebUIServer(module: WebUIModule, session: WebUISession) {
        withContext(Dispatchers.IO) {
            try {
                // Create WebUI server process
                val moduleDir = File("/data/adb/modules/${module.moduleId}")
                val webuiScript = File(moduleDir, "webui.sh")
                
                if (webuiScript.exists()) {
                    // Execute WebUI script
                    val process = ProcessBuilder()
                        .command("sh", webuiScript.absolutePath, "start")
                        .directory(moduleDir)
                        .start()
                    
                    // Update session with process ID (use reflection for compatibility)
                    val pid = try {
                        val pidMethod = process.javaClass.getMethod("pid")
                        (pidMethod.invoke(process) as? Long)?.toInt()
                    } catch (e: Exception) {
                        null
                    }
                    val updatedSession = session.copy(processId = pid)
                    activeSessions[session.sessionId] = updatedSession
                    
                    Log.d(TAG, "Started WebUI server for ${module.moduleId} on port ${module.webuiPort}")
                } else {
                    Log.w(TAG, "No WebUI script found for ${module.moduleId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting WebUI server", e)
                throw e
            }
        }
    }
    
    /**
     * Stop WebUI server for a session
     */
    private suspend fun stopWebUIServer(session: WebUISession) {
        withContext(Dispatchers.IO) {
            try {
                session.processId?.let { pid ->
                    // Kill the WebUI server process
                    ProcessBuilder()
                        .command("kill", "-TERM", pid.toString())
                        .start()
                        .waitFor()
                }
                
                // Also try to stop via script
                val moduleDir = File("/data/adb/modules/${session.moduleId}")
                val webuiScript = File(moduleDir, "webui.sh")
                
                if (webuiScript.exists()) {
                    ProcessBuilder()
                        .command("sh", webuiScript.absolutePath, "stop")
                        .directory(moduleDir)
                        .start()
                        .waitFor()
                }
                
                Log.d(TAG, "Stopped WebUI server for ${session.moduleId}")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping WebUI server", e)
            }
        }
    }
    
    /**
     * Execute command through WebUI
     */
    suspend fun executeCommand(sessionId: String, command: String): WebUICommandResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                val process = ProcessBuilder()
                    .command("sh", "-c", command)
                    .start()
                
                val exitCode = process.waitFor()
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val executionTime = System.currentTimeMillis() - startTime
                
                WebUICommandResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    executionTime = executionTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
                WebUICommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = e.message ?: "Unknown error",
                    executionTime = System.currentTimeMillis() - startTime
                )
            }
        }
    }
    
    /**
     * Get file information
     */
    suspend fun getFileInfo(sessionId: String, path: String): WebUIFileInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    WebUIFileInfo(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        permissions = getFilePermissions(file),
                        isReadable = file.canRead(),
                        isWritable = file.canWrite()
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file info for: $path", e)
                null
            }
        }
    }
    
    /**
     * Get file permissions string
     */
    private fun getFilePermissions(file: File): String {
        return try {
            val process = ProcessBuilder()
                .command("ls", "-l", file.absolutePath)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val parts = output.trim().split("\\s+".toRegex())
            if (parts.size > 0) parts[0] else "----------"
        } catch (e: Exception) {
            "----------"
        }
    }
    
    /**
     * Get all WebUI modules
     */
    fun getWebUIModules(): List<WebUIModule> {
        return webuiModules.values.toList()
    }
    
    /**
     * Get active sessions
     */
    fun getActiveSessions(): List<WebUISession> {
        return activeSessions.values.toList()
    }
    
    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): WebUISession? {
        return activeSessions[sessionId]
    }
    
    /**
     * Cleanup all sessions
     */
    private fun cleanupAllSessions() {
        activeSessions.keys.forEach { sessionId ->
            stopWebUISession(sessionId)
        }
    }
    
    /**
     * Update session access time
     */
    fun updateSessionAccess(sessionId: String) {
        activeSessions[sessionId]?.let { session ->
            activeSessions[sessionId] = session.copy(lastAccess = System.currentTimeMillis())
        }
    }
}
