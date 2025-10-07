package io.github.huskydg.magisk.ui.module

import androidx.databinding.Bindable
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.model.module.LocalModule
import io.github.huskydg.magisk.databinding.DiffItem
import io.github.huskydg.magisk.databinding.ItemWrapper
import io.github.huskydg.magisk.databinding.ObservableRvItem
import io.github.huskydg.magisk.databinding.RvItem
import io.github.huskydg.magisk.databinding.set
import io.github.huskydg.magisk.utils.TextHolder
import io.github.huskydg.magisk.utils.asText
import io.github.huskydg.magisk.core.R as CoreR

object InstallModule : RvItem(), DiffItem<InstallModule> {
    override val layoutRes = R.layout.item_module_download
}

class LocalModuleRvItem(
    override val item: LocalModule
) : ObservableRvItem(), DiffItem<LocalModuleRvItem>, ItemWrapper<LocalModule> {

    override val layoutRes = R.layout.item_module_md2

    val showNotice: Boolean
    val showAction: Boolean
    val noticeText: TextHolder

    init {
        val isZygisk = item.isZygisk
        val isRiru = item.isRiru
        val zygiskUnloaded = isZygisk && item.zygiskUnloaded

        showNotice = zygiskUnloaded ||
            (Info.isZygiskEnabled && isRiru) ||
            (!Info.isZygiskEnabled && isZygisk)
        showAction = item.hasAction && !showNotice
        noticeText =
            when {
                zygiskUnloaded -> CoreR.string.zygisk_module_unloaded.asText()
                isRiru -> CoreR.string.suspend_text_riru.asText(CoreR.string.zygisk.asText())
                else -> CoreR.string.suspend_text_zygisk.asText(CoreR.string.zygisk.asText())
            }
    }

    @get:Bindable
    var isEnabled = item.enable
        set(value) = set(value, field, { field = it }, BR.enabled, BR.updateReady) {
            item.enable = value
        }

    @get:Bindable
    var isRemoved = item.remove
        set(value) = set(value, field, { field = it }, BR.removed, BR.updateReady) {
            item.remove = value
        }

    @get:Bindable
    val showUpdate get() = item.updateInfo != null

    @get:Bindable
    val updateReady get() = item.outdated && !isRemoved && isEnabled

    val isUpdated = item.updated

    fun fetchedUpdateInfo() {
        notifyPropertyChanged(BR.showUpdate)
        notifyPropertyChanged(BR.updateReady)
    }

    fun delete() {
        isRemoved = !isRemoved
    }

    override fun itemSameAs(other: LocalModuleRvItem): Boolean = item.id == other.item.id
}
