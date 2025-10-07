package io.github.huskydg.magisk.ui.superuser

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.arch.AsyncLoadViewModel
import io.github.huskydg.magisk.core.AppContext
import io.github.huskydg.magisk.core.Config
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.R
import io.github.huskydg.magisk.core.data.magiskdb.PolicyDao
import io.github.huskydg.magisk.core.ktx.getLabel
import io.github.huskydg.magisk.core.model.su.SuPolicy
import io.github.huskydg.magisk.databinding.MergeObservableList
import io.github.huskydg.magisk.databinding.RvItem
import io.github.huskydg.magisk.databinding.bindExtra
import io.github.huskydg.magisk.databinding.diffList
import io.github.huskydg.magisk.databinding.set
import io.github.huskydg.magisk.dialog.SuperuserRevokeDialog
import io.github.huskydg.magisk.events.AuthEvent
import io.github.huskydg.magisk.events.SnackbarEvent
import io.github.huskydg.magisk.utils.asText
import io.github.huskydg.magisk.view.TextItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SuperuserViewModel(
    private val db: PolicyDao
) : AsyncLoadViewModel() {

    private val itemNoData = TextItem(R.string.superuser_policy_none)

    private val itemsHelpers = ObservableArrayList<TextItem>()
    private val itemsPolicies = diffList<PolicyRvItem>()

    val items = MergeObservableList<RvItem>()
        .insertList(itemsHelpers)
        .insertList(itemsPolicies)
    val extraBindings = bindExtra {
        it.put(BR.listener, this)
    }

    @get:Bindable
    var loading = true
        private set(value) = set(value, field, { field = it }, BR.loading)

    @SuppressLint("InlinedApi")
    override suspend fun doLoadWork() {
        if (!Info.showSuperUser) {
            loading = false
            return
        }
        loading = true
        withContext(Dispatchers.IO) {
            db.deleteOutdated()
            db.delete(AppContext.applicationInfo.uid)
            val policies = ArrayList<PolicyRvItem>()
            val pm = AppContext.packageManager
            for (policy in db.fetchAll()) {
                val pkgs =
                    if (policy.uid == Process.SYSTEM_UID) arrayOf("android")
                    else pm.getPackagesForUid(policy.uid)
                if (pkgs == null) {
                    db.delete(policy.uid)
                    continue
                }
                val map = pkgs.mapNotNull { pkg ->
                    try {
                        val info = pm.getPackageInfo(pkg, MATCH_UNINSTALLED_PACKAGES)
                        PolicyRvItem(
                            this@SuperuserViewModel, policy,
                            info.packageName,
                            info.sharedUserId != null,
                            info.applicationInfo?.loadIcon(pm) ?: pm.defaultActivityIcon,
                            info.applicationInfo?.getLabel(pm) ?: info.packageName
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                }
                if (map.isEmpty()) {
                    db.delete(policy.uid)
                    continue
                }
                policies.addAll(map)
            }
            policies.sortWith(compareBy(
                { it.appName.lowercase(Locale.ROOT) },
                { it.packageName }
            ))
            itemsPolicies.update(policies)
        }
        if (itemsPolicies.isNotEmpty())
            itemsHelpers.clear()
        else if (itemsHelpers.isEmpty())
            itemsHelpers.add(itemNoData)
        loading = false
    }

    // ---

    fun deletePressed(item: PolicyRvItem) {
        fun updateState() = viewModelScope.launch {
            db.delete(item.item.uid)
            val list = ArrayList(itemsPolicies)
            list.removeAll { it.item.uid == item.item.uid }
            itemsPolicies.update(list)
            if (list.isEmpty() && itemsHelpers.isEmpty()) {
                itemsHelpers.add(itemNoData)
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            SuperuserRevokeDialog(item.title) { updateState() }.show()
        }
    }

    fun updateNotify(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.notification -> R.string.su_snack_notif_on
                else -> R.string.su_snack_notif_off
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.notifyPropertyChanged(BR.shouldNotify)
                }
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun updateLogging(item: PolicyRvItem) {
        viewModelScope.launch {
            db.update(item.item)
            val res = when {
                item.item.logging -> R.string.su_snack_log_on
                else -> R.string.su_snack_log_off
            }
            itemsPolicies.forEach {
                if (it.item.uid == item.item.uid) {
                    it.notifyPropertyChanged(BR.shouldLog)
                }
            }
            SnackbarEvent(res.asText(item.appName)).publish()
        }
    }

    fun togglePolicy(item: PolicyRvItem, enable: Boolean) {
        val items = itemsPolicies.filter { it.item.uid == item.item.uid }
        fun updateState() {
            viewModelScope.launch {
                val res = if (enable) R.string.su_snack_grant else R.string.su_snack_deny
                item.item.policy = if (enable) SuPolicy.ALLOW else SuPolicy.DENY
                db.update(item.item)
                items.forEach {
                    it.notifyPropertyChanged(BR.enabled)
                }
                SnackbarEvent(res.asText(item.appName)).publish()
            }
        }

        if (Config.suAuth) {
            AuthEvent { updateState() }.publish()
        } else {
            updateState()
        }
    }
}
