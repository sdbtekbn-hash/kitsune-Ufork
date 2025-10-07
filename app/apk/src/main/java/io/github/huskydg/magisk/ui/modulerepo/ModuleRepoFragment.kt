package io.github.huskydg.magisk.ui.modulerepo

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.BaseFragment
import io.github.huskydg.magisk.arch.viewModel
import io.github.huskydg.magisk.databinding.FragmentModuleRepoBinding

class ModuleRepoFragment : BaseFragment<FragmentModuleRepoBinding>() {

    override val layoutRes = R.layout.fragment_module_repo
    override val viewModel by viewModel<ModuleRepoViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewModel = viewModel

        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.moduleRepoList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (totalItemCount <= lastVisibleItem + 5) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.module_repo)
        viewModel.updateRepoNow()
    }
}

