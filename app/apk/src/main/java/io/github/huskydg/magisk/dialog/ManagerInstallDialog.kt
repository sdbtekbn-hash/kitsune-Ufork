package io.github.huskydg.magisk.dialog

import io.github.huskydg.magisk.core.AppContext
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.R
import io.github.huskydg.magisk.core.di.ServiceLocator
import io.github.huskydg.magisk.core.download.DownloadEngine
import io.github.huskydg.magisk.core.download.Subject
import io.github.huskydg.magisk.view.MagiskDialog
import java.io.File

class ManagerInstallDialog : MarkDownDialog() {

    private val svc get() = ServiceLocator.networkService

    override suspend fun getMarkdownText(): String {
        val text = svc.fetchString(Info.remote.magisk.note)
        // Cache the changelog
        AppContext.cacheDir.listFiles { _, name -> name.endsWith(".md") }.orEmpty().forEach {
            it.delete()
        }
        File(AppContext.cacheDir, "${Info.remote.magisk.versionCode}.md").writeText(text)
        return text
    }

    override fun build(dialog: MagiskDialog) {
        super.build(dialog)
        dialog.apply {
            setCancelable(true)
            setButton(MagiskDialog.ButtonType.POSITIVE) {
                text = R.string.install
                onClick { DownloadEngine.startWithActivity(activity, Subject.App()) }
            }
            setButton(MagiskDialog.ButtonType.NEGATIVE) {
                text = android.R.string.cancel
            }
        }
    }

}
