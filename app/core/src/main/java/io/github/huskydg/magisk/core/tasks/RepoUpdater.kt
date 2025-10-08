package io.github.huskydg.magisk.core.tasks

import io.github.huskydg.magisk.core.model.module.OnlineModule
import io.github.huskydg.magisk.data.database.RepoDao
import io.github.huskydg.magisk.core.repository.NetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*

class RepoUpdater(
    private val svc: NetworkService,
    private val repoDB: RepoDao
) {

    suspend fun run(forced: Boolean) = withContext(Dispatchers.IO) {
        val cachedMap = HashMap<String, Date>().apply {
            repoDB.getModuleStubs().forEach { put(it.id, Date(it.last_update)) }
        }
        
        svc.fetchRepoInfo()?.let { info ->
            coroutineScope {
                info.modules.forEach { moduleInfo ->
                    launch {
                        val lastUpdated = cachedMap.remove(moduleInfo.id)
                        if (forced || lastUpdated?.before(Date(moduleInfo.last_update)) != false) {
                            try {
                                val repo = OnlineModule(moduleInfo).apply { load() }
                                repoDB.addModule(repo)
                            } catch (e: OnlineModule.IllegalRepoException) {
                                Timber.e(e)
                            }
                        }
                    }
                }
            }
            repoDB.removeModules(cachedMap.keys)
        }
    }
}

