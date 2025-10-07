package io.github.huskydg.magisk.ui.install

import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.BaseFragment
import io.github.huskydg.magisk.arch.viewModel
import io.github.huskydg.magisk.databinding.FragmentInstallMd2Binding
import io.github.huskydg.magisk.core.R as CoreR

class InstallFragment : BaseFragment<FragmentInstallMd2Binding>() {

    override val layoutRes = R.layout.fragment_install_md2
    override val viewModel by viewModel<InstallViewModel>()

    override fun onStart() {
        super.onStart()
        requireActivity().setTitle(CoreR.string.install)
    }
}
