package io.github.huskydg.magisk.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.databinding.Bindable
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.ActivityExecutor
import io.github.huskydg.magisk.arch.AsyncLoadViewModel
import io.github.huskydg.magisk.arch.ContextExecutor
import io.github.huskydg.magisk.arch.UIActivity
import io.github.huskydg.magisk.arch.ViewEvent
import io.github.huskydg.magisk.core.BuildConfig
import io.github.huskydg.magisk.core.Config
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.download.Subject
import io.github.huskydg.magisk.core.download.Subject.App
import io.github.huskydg.magisk.core.ktx.await
import io.github.huskydg.magisk.core.ktx.toast
import io.github.huskydg.magisk.core.repository.NetworkService
import io.github.huskydg.magisk.databinding.bindExtra
import io.github.huskydg.magisk.databinding.set
import io.github.huskydg.magisk.dialog.EnvFixDialog
import io.github.huskydg.magisk.dialog.ManagerInstallDialog
import io.github.huskydg.magisk.dialog.UninstallDialog
import io.github.huskydg.magisk.events.SnackbarEvent
import io.github.huskydg.magisk.utils.asText
import com.topjohnwu.superuser.Shell
import kotlin.math.roundToInt
import io.github.huskydg.magisk.core.R as CoreR

class HomeViewModel(
    private val svc: NetworkService
) : AsyncLoadViewModel() {

    enum class State {
        LOADING, INVALID, OUTDATED, UP_TO_DATE
    }

    val magiskTitleBarrierIds =
        intArrayOf(R.id.home_magisk_icon, R.id.home_magisk_title, R.id.home_magisk_button)
    val appTitleBarrierIds =
        intArrayOf(R.id.home_manager_icon, R.id.home_manager_title, R.id.home_manager_button)

    @get:Bindable
    var isNoticeVisible = Config.safetyNotice
        set(value) = set(value, field, { field = it }, BR.noticeVisible)

    val magiskState
        get() = when {
            Info.isRooted && Info.env.isUnsupported -> State.OUTDATED
            !Info.env.isActive -> State.INVALID
            Info.env.versionCode < BuildConfig.APP_VERSION_CODE -> State.OUTDATED
            else -> State.UP_TO_DATE
        }

    @get:Bindable
    var appState = State.LOADING
        set(value) = set(value, field, { field = it }, BR.appState)

    val magiskInstalledVersion
        get() = Info.env.run {
            if (isActive)
                ("$versionString ($versionCode)" + if (isDebug) " (D)" else "").asText()
            else
                CoreR.string.not_available.asText()
        }

    @get:Bindable
    var managerRemoteVersion = CoreR.string.loading.asText()
        set(value) = set(value, field, { field = it }, BR.managerRemoteVersion)

    val managerInstalledVersion
        get() = "${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})" +
            if (BuildConfig.DEBUG) " (D)" else ""

    @get:Bindable
    var stateManagerProgress = 0
        set(value) = set(value, field, { field = it }, BR.stateManagerProgress)

    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    companion object {
        private var checkedEnv = false
    }

    override suspend fun doLoadWork() {
        appState = State.LOADING
        Info.getRemote(svc)?.apply {
            appState = when {
                BuildConfig.APP_VERSION_CODE < magisk.versionCode -> State.OUTDATED
                else -> State.UP_TO_DATE
            }

            val isDebug = Config.updateChannel == Config.Value.DEBUG_CHANNEL
            managerRemoteVersion =
                ("${magisk.version} (${magisk.versionCode})" +
                    if (isDebug) " (D)" else "").asText()
        } ?: run {
            appState = State.INVALID
            managerRemoteVersion = CoreR.string.not_available.asText()
        }
        ensureEnv()
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    fun onProgressUpdate(progress: Float, subject: Subject) {
        if (subject is App)
            stateManagerProgress = progress.times(100f).roundToInt()
    }

    fun onLinkPressed(link: String) = object : ViewEvent(), ContextExecutor {
        override fun invoke(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, link.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                context.toast(CoreR.string.open_link_failed_toast, Toast.LENGTH_SHORT)
            }
        }
    }.publish()

    fun onDeletePressed() = UninstallDialog().show()

    fun onManagerPressed() = when (appState) {
        State.LOADING -> SnackbarEvent(CoreR.string.loading).publish()
        State.INVALID -> SnackbarEvent(CoreR.string.no_connection).publish()
        else -> withExternalRW {
            withInstallPermission {
                ManagerInstallDialog().show()
            }
        }
    }

    fun onMagiskPressed() = withExternalRW {
        HomeFragmentDirections.actionHomeFragmentToInstallFragment().navigate()
    }

    fun hideNotice() {
        Config.safetyNotice = false
        isNoticeVisible = false
    }

    private suspend fun ensureEnv() {
        if (magiskState == State.INVALID || checkedEnv) return
        val cmd = "env_check ${Info.env.versionString} ${Info.env.versionCode}"
        val code = Shell.cmd(cmd).await().code
        if (code != 0) {
            EnvFixDialog(this, code).show()
        }
        checkedEnv = true
    }

    val showTest = false
    fun onTestPressed() = object : ViewEvent(), ActivityExecutor {
        override fun invoke(activity: UIActivity<*>) {
            /* Entry point to trigger test events within the app */
        }
    }.publish()
}
