package io.github.huskydg.magisk.ui.log

import android.system.Os
import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.arch.AsyncLoadViewModel
import io.github.huskydg.magisk.core.BuildConfig
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.R
import io.github.huskydg.magisk.core.ktx.timeFormatStandard
import io.github.huskydg.magisk.core.ktx.toTime
import io.github.huskydg.magisk.core.repository.LogRepository
import io.github.huskydg.magisk.core.utils.MediaStoreUtils
import io.github.huskydg.magisk.core.utils.MediaStoreUtils.outputStream
import io.github.huskydg.magisk.databinding.bindExtra
import io.github.huskydg.magisk.databinding.diffList
import io.github.huskydg.magisk.databinding.set
import io.github.huskydg.magisk.events.SnackbarEvent
import io.github.huskydg.magisk.view.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

class LogViewModel(
    private val repo: LogRepository
) : AsyncLoadViewModel() {
    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    // --- empty view

    val itemEmpty = TextItem(R.string.log_data_none)
    val itemMagiskEmpty = TextItem(R.string.log_data_magisk_none)

    // --- su log

    val items = diffList<SuLogRvItem>()
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    // --- magisk log
    val logs = diffList<LogRvItem>()
    var magiskLogRaw = " "

    override suspend fun doLoadWork() {
        loading = true

        val (suLogs, suDiff) = withContext(Dispatchers.Default) {
            magiskLogRaw = repo.fetchMagiskLogs()
            val newLogs = magiskLogRaw.split('\n').map { LogRvItem(it) }
            logs.update(newLogs)
            val suLogs = repo.fetchSuLogs().map { SuLogRvItem(it) }
            suLogs to items.calculateDiff(suLogs)
        }

        items.firstOrNull()?.isTop = false
        items.lastOrNull()?.isBottom = false
        items.update(suLogs, suDiff)
        items.firstOrNull()?.isTop = true
        items.lastOrNull()?.isBottom = true
        loading = false
    }

    fun saveMagiskLog() = withExternalRW {
        viewModelScope.launch(Dispatchers.IO) {
            val filename = "magisk_log_%s.log".format(
                System.currentTimeMillis().toTime(timeFormatStandard))
            val logFile = MediaStoreUtils.getFile(filename)
            logFile.uri.outputStream().bufferedWriter().use { file ->
                file.write("---Detected Device Info---\n\n")
                file.write("isAB=${Info.isAB}\n")
                file.write("isSAR=${Info.isSAR}\n")
                file.write("ramdisk=${Info.ramdisk}\n")
                val uname = Os.uname()
                file.write("kernel=${uname.sysname} ${uname.machine} ${uname.release} ${uname.version}\n")

                file.write("\n\n---System Properties---\n\n")
                ProcessBuilder("getprop").start()
                    .inputStream.reader().use { it.copyTo(file) }

                file.write("\n\n---Environment Variables---\n\n")
                System.getenv().forEach { (key, value) -> file.write("${key}=${value}\n") }

                file.write("\n\n---System MountInfo---\n\n")
                FileInputStream("/proc/self/mountinfo").reader().use { it.copyTo(file) }

                file.write("\n---Magisk Logs---\n")
                file.write("${Info.env.versionString} (${Info.env.versionCode})\n\n")
                if (Info.env.isActive) file.write(magiskLogRaw)

                file.write("\n---Manager Logs---\n")
                file.write("${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})\n\n")
                ProcessBuilder("logcat", "-d").start()
                    .inputStream.reader().use { it.copyTo(file) }
            }
            SnackbarEvent(logFile.toString()).publish()
        }
    }

    fun clearMagiskLog() = repo.clearMagiskLogs {
        SnackbarEvent(R.string.logs_cleared).publish()
        startLoading()
    }

    fun clearLog() = viewModelScope.launch {
        repo.clearLogs()
        SnackbarEvent(R.string.logs_cleared).publish()
        startLoading()
    }
}
