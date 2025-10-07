package io.github.huskydg.magisk.ui.webui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.core.model.webui.WebUIModule
import io.github.huskydg.magisk.core.model.webui.WebUICommandResult
import io.github.huskydg.magisk.core.service.WebUIService
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * WebUI Activity
 * Displays WebUI interface for modules
 */
class WebUIActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_MODULE_ID = "module_id"
        private const val EXTRA_MODULE_NAME = "module_name"
        private const val EXTRA_WEBUI_URL = "webui_url"
        
        fun createIntent(context: Context, moduleId: String, moduleName: String, webuiUrl: String? = null): Intent {
            return Intent(context, WebUIActivity::class.java).apply {
                putExtra(EXTRA_MODULE_ID, moduleId)
                putExtra(EXTRA_MODULE_NAME, moduleName)
                putExtra(EXTRA_WEBUI_URL, webuiUrl)
            }
        }
    }
    
    private lateinit var webView: WebView
    private var webUIService: WebUIService? = null
    private var sessionId: String? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WebUIService.WebUIBinder
            webUIService = binder.getService()
            isServiceBound = true
            
            // Start WebUI session
            lifecycleScope.launch {
                sessionId = webUIService?.startWebUISession(getModuleId())
                if (sessionId != null) {
                    loadWebUI()
                } else {
                    showError("Failed to start WebUI session")
                }
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            webUIService = null
            isServiceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webui)
        
        setupWebView()
        setupActionBar()
        
        // Bind to WebUI service
        val intent = Intent(this, WebUIService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop WebUI session
        sessionId?.let { id ->
            webUIService?.stopWebUISession(id)
        }
        
        // Unbind service
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
    
    private fun setupActionBar() {
        supportActionBar?.apply {
            title = getModuleName()
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webview)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            databaseEnabled = true
            setGeolocationEnabled(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                findViewById<View>(R.id.progress_bar)?.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                findViewById<View>(R.id.progress_bar)?.visibility = View.GONE
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                showError("WebView Error: ${error?.description}")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                findViewById<View>(R.id.progress_bar)?.visibility = 
                    if (newProgress == 100) View.GONE else View.VISIBLE
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { setTitle(it) }
            }
        }
        
        // Add JavaScript interface
        webView.addJavascriptInterface(WebUIJavaScriptInterface(), "magisk")
    }
    
    private fun loadWebUI() {
        val webuiUrl = intent.getStringExtra(EXTRA_WEBUI_URL)
        if (webuiUrl != null) {
            webView.loadUrl(webuiUrl)
        } else {
            // Try to load from module's WebUI
            val moduleId = getModuleId()
            webUIService?.getWebUIModules()?.find { it.moduleId == moduleId }?.let { module ->
                val url = "http://localhost:${module.webuiPort}${module.webuiPath}"
                webView.loadUrl(url)
            } ?: showError("WebUI not available for this module")
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }
    
    private fun getModuleId(): String {
        return intent.getStringExtra(EXTRA_MODULE_ID) ?: ""
    }
    
    private fun getModuleName(): String {
        return intent.getStringExtra(EXTRA_MODULE_NAME) ?: "WebUI"
    }
    
    /**
     * JavaScript Interface for WebUI
     */
    inner class WebUIJavaScriptInterface {
        
        @JavascriptInterface
        fun executeCommand(command: String, callback: String) {
            lifecycleScope.launch {
                try {
                    val result = webUIService?.executeCommand(sessionId ?: "", command)
                    val jsonResult = JSONObject().apply {
                        put("exitCode", result?.exitCode ?: -1)
                        put("stdout", result?.stdout ?: "")
                        put("stderr", result?.stderr ?: "")
                        put("executionTime", result?.executionTime ?: 0)
                    }
                    
                    webView.post {
                        webView.evaluateJavascript("$callback($jsonResult);", null)
                    }
                } catch (e: Exception) {
                    webView.post {
                        webView.evaluateJavascript("$callback({exitCode: -1, stdout: '', stderr: '${e.message}', executionTime: 0});", null)
                    }
                }
            }
        }
        
        @JavascriptInterface
        fun getFileInfo(path: String, callback: String) {
            lifecycleScope.launch {
                try {
                    val fileInfo = webUIService?.getFileInfo(sessionId ?: "", path)
                    val jsonResult = if (fileInfo != null) {
                        JSONObject().apply {
                            put("path", fileInfo.path)
                            put("name", fileInfo.name)
                            put("isDirectory", fileInfo.isDirectory)
                            put("size", fileInfo.size)
                            put("lastModified", fileInfo.lastModified)
                            put("permissions", fileInfo.permissions)
                            put("isReadable", fileInfo.isReadable)
                            put("isWritable", fileInfo.isWritable)
                        }
                    } else {
                        JSONObject().put("error", "File not found")
                    }
                    
                    webView.post {
                        webView.evaluateJavascript("$callback($jsonResult);", null)
                    }
                } catch (e: Exception) {
                    webView.post {
                        webView.evaluateJavascript("$callback({error: '${e.message}'});", null)
                    }
                }
            }
        }
        
        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread {
                Toast.makeText(this@WebUIActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun fullscreen(enable: Boolean) {
            runOnUiThread {
                val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
                if (enable) {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior = 
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        
        @JavascriptInterface
        fun getModuleInfo(): String {
            return JSONObject().apply {
                put("moduleId", getModuleId())
                put("moduleName", getModuleName())
                put("sessionId", sessionId)
            }.toString()
        }
        
        @JavascriptInterface
        fun close() {
            runOnUiThread {
                finish()
            }
        }
    }
}
