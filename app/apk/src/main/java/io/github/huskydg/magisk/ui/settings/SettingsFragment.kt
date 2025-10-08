package io.github.huskydg.magisk.ui.settings

import android.os.Bundle
import android.view.View
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.BaseFragment
import io.github.huskydg.magisk.arch.viewModel
import io.github.huskydg.magisk.databinding.FragmentSettingsMd2Binding
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.addItemSpacing
import rikka.recyclerview.fixEdgeEffect
import io.github.huskydg.magisk.core.R as CoreR

class SettingsFragment : BaseFragment<FragmentSettingsMd2Binding>() {

    override val layoutRes = R.layout.fragment_settings_md2
    override val viewModel by viewModel<SettingsViewModel>()
    override val snackbarView: View get() = binding.snackbarContainer

    override fun onStart() {
        super.onStart()

        activity?.title = resources.getString(CoreR.string.settings)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsList.apply {
            addEdgeSpacing(bottom = R.dimen.l1)
            addItemSpacing(R.dimen.l1, R.dimen.l_50, R.dimen.l1)
            fixEdgeEffect()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.items.forEach { it.refresh() }
    }

}
