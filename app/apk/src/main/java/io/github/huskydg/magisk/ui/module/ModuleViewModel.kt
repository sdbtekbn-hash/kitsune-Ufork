package io.github.huskydg.magisk.ui.module

import android.net.Uri
import androidx.databinding.Bindable
import androidx.lifecycle.MutableLiveData
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.MainDirections
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.AsyncLoadViewModel
import io.github.huskydg.magisk.core.Const
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.base.ContentResultCallback
import io.github.huskydg.magisk.core.model.module.LocalModule
import io.github.huskydg.magisk.core.model.module.OnlineModule
import io.github.huskydg.magisk.databinding.MergeObservableList
import io.github.huskydg.magisk.databinding.RvItem
import io.github.huskydg.magisk.databinding.bindExtra
import io.github.huskydg.magisk.databinding.diffList
import io.github.huskydg.magisk.databinding.set
import io.github.huskydg.magisk.dialog.LocalModuleInstallDialog
import io.github.huskydg.magisk.dialog.OnlineModuleInstallDialog
import io.github.huskydg.magisk.events.GetContentEvent
import io.github.huskydg.magisk.events.SnackbarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import io.github.huskydg.magisk.core.R as CoreR

class ModuleViewModel : AsyncLoadViewModel() {

    val bottomBarBarrierIds = intArrayOf(R.id.module_update, R.id.module_remove)

    private val itemsInstalled = diffList<LocalModuleRvItem>()

    val items = MergeObservableList<RvItem>()
    val extraBindings = bindExtra {
        it.put(BR.viewModel, this)
    }

    val data get() = uri

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    override suspend fun doLoadWork() {
        loading = true
        val moduleLoaded = Info.env.isActive &&
                withContext(Dispatchers.IO) { LocalModule.loaded() }
        if (moduleLoaded) {
            loadInstalled()
            if (items.isEmpty()) {
                items.insertItem(InstallModule)
                    .insertList(itemsInstalled)
            }
        }
        loading = false
        loadUpdateInfo()
    }

    override fun onNetworkChanged(network: Boolean) = startLoading()

    private suspend fun loadInstalled() {
        withContext(Dispatchers.Default) {
            val installed = LocalModule.installed().map { LocalModuleRvItem(it) }
            itemsInstalled.update(installed)
        }
    }

    private suspend fun loadUpdateInfo() {
        withContext(Dispatchers.IO) {
            itemsInstalled.forEach {
                if (it.item.fetch())
                    it.fetchedUpdateInfo()
            }
        }
    }

    fun downloadPressed(item: OnlineModule?) =
        if (item != null && Info.isConnected.value == true) {
            withExternalRW { OnlineModuleInstallDialog(item).show() }
        } else {
            SnackbarEvent(CoreR.string.no_connection).publish()
        }

    fun installPressed() = withExternalRW {
        GetContentEvent("application/zip", UriCallback()).publish()
    }

    fun requestInstallLocalModule(uri: Uri, displayName: String) {
        LocalModuleInstallDialog(this, uri, displayName).show()
    }

    @Parcelize
    class UriCallback : ContentResultCallback {
        override fun onActivityResult(result: Uri) {
            uri.value = result
        }
    }

    fun runAction(id: String, name: String) {
        MainDirections.actionActionFragment(id, name).navigate()
    }

    companion object {
        private val uri = MutableLiveData<Uri?>()
    }
}
