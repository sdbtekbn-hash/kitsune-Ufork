package io.github.huskydg.magisk.ui.modulerepo

import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.viewModelScope
import io.github.huskydg.magisk.BR
import io.github.huskydg.magisk.core.tasks.RepoUpdater
import io.github.huskydg.magisk.data.database.RepoDao
import io.github.huskydg.magisk.databinding.ObservableRvItem
import io.github.huskydg.magisk.databinding.diffList
import io.github.huskydg.magisk.databinding.filterList
import io.github.huskydg.magisk.databinding.bindExtra
import io.github.huskydg.magisk.databinding.DiffItem
import io.github.huskydg.magisk.arch.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModuleRepoViewModel(
    private val repoDB: RepoDao,
    private val repoUpdater: RepoUpdater
) : BaseViewModel() {

    val items = diffList<ModuleRepoRvItem>()
    val itemsSearch = filterList<ModuleRepoRvItem>(viewModelScope)
    val extraBindings = bindExtra {
        // Add any extra bindings needed for the RecyclerView items
    }

    @get:Bindable
    var query = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.query)
            submitQuery()
        }

    @get:Bindable
    var isRemoteLoading = false
        set(value) {
            field = value
            notifyPropertyChanged(BR.remoteLoading)
        }

    private var queryJob: Job? = null
    private var loadJob: Job? = null
    private var currentPage = 0
    private var canLoadMore = true
    private var isSearchMode = false

    init {
        // Initial load
        loadModules()
    }

    fun loadModules(offset: Int = 0) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val newItems = withContext(Dispatchers.IO) {
                repoDB.getModules(offset, RepoDao.LIMIT)
            }.map { ModuleRepoRvItem(it, this@ModuleRepoViewModel) }

            if (offset == 0) {
                items.update(newItems)
            } else {
                items.update(items + newItems)
            }
            canLoadMore = newItems.size == RepoDao.LIMIT
            currentPage = if (offset == 0) 1 else currentPage + 1
        }
    }

    fun loadNextPage() {
        if (canLoadMore && !isSearchMode) {
            loadModules(currentPage * RepoDao.LIMIT)
        } else if (canLoadMore && isSearchMode) {
            searchNextPage()
        }
    }

    private fun submitQuery() {
        queryJob?.cancel()
        currentPage = 0
        canLoadMore = true

        if (query.isBlank()) {
            isSearchMode = false
            loadModules()
            return
        }

        isSearchMode = true
        queryJob = viewModelScope.launch {
            val searched = withContext(Dispatchers.IO) {
                repoDB.searchModules(query, 0, RepoDao.LIMIT)
            }.map { ModuleRepoRvItem(it, this@ModuleRepoViewModel) }

            items.update(searched)
            canLoadMore = searched.size == RepoDao.LIMIT
            currentPage = 1
        }
    }

    private fun searchNextPage() {
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val searched = withContext(Dispatchers.IO) {
                repoDB.searchModules(query, currentPage * RepoDao.LIMIT, RepoDao.LIMIT)
            }.map { ModuleRepoRvItem(it, this@ModuleRepoViewModel) }

            items.update(items + searched)
            canLoadMore = searched.size == RepoDao.LIMIT
            currentPage++
        }
    }

    fun forceReload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isRemoteLoading = true
            withContext(Dispatchers.IO) {
                try {
                    repoUpdater.run(true)
                } catch (e: Exception) {
                    // Handle error
                }
            }
            isRemoteLoading = false
            loadModules()
        }
    }

    fun updateRepoNow() {
        viewModelScope.launch {
            isRemoteLoading = true
            withContext(Dispatchers.IO) {
                try {
                    repoUpdater.run(false)
                } catch (e: Exception) {
                    // Handle error
                }
            }
            isRemoteLoading = false
        }
    }
}

class ModuleRepoRvItem(
    val item: io.github.huskydg.magisk.core.model.module.OnlineModule,
    private val viewModel: ModuleRepoViewModel
) : ObservableRvItem(), DiffItem<ModuleRepoRvItem> {
    override val layoutRes = io.github.huskydg.magisk.R.layout.item_module_repo

    val name get() = item.name
    val version get() = item.version
    val author get() = item.author
    val description get() = item.description
    val lastUpdate get() = item.lastUpdateString
    
    // Enhanced properties
    val hasIcon get() = item.hasIcon
    val hasCover get() = item.hasCover
    val hasScreenshots get() = item.hasScreenshots
    val hasCategories get() = item.hasCategories
    val hasSize get() = item.hasSize
    val isVerified get() = item.isVerified
    
    val iconUrl get() = item.icon
    val coverUrl get() = item.cover
    val sizeFormatted get() = item.sizeFormatted
    val categoriesText get() = item.categories?.joinToString(", ") ?: ""

    fun download() {
        // TODO: Implement download functionality
    }

    fun openInfo() {
        // TODO: Implement info dialog
    }
}

