package io.github.huskydg.magisk.core.repository

import io.github.huskydg.magisk.core.Const
import io.github.huskydg.magisk.core.Info
import io.github.huskydg.magisk.core.data.SuLogDao
import io.github.huskydg.magisk.core.ktx.await
import io.github.huskydg.magisk.core.model.su.SuLog
import com.topjohnwu.superuser.Shell


class LogRepository(
    private val logDao: SuLogDao
) {

    suspend fun fetchSuLogs() = logDao.fetchAll()

    suspend fun fetchMagiskLogs(): String {
        val list = object : AbstractMutableList<String>() {
            val buf = StringBuilder()
            override val size get() = 0
            override fun get(index: Int): String = ""
            override fun removeAt(index: Int): String = ""
            override fun set(index: Int, element: String): String = ""
            override fun add(index: Int, element: String) {
                if (element.isNotEmpty()) {
                    buf.append(element)
                    buf.append('\n')
                }
            }
        }
        if (Info.env.isActive) {
            Shell.cmd("cat ${Const.MAGISK_LOG} || logcat -d -s Magisk").to(list).await()
        } else {
            Shell.cmd("logcat -d").to(list).await()
        }
        return list.buf.toString()
    }

    suspend fun clearLogs() = logDao.deleteAll()

    fun clearMagiskLogs(cb: (Shell.Result) -> Unit) =
        Shell.cmd("echo -n > ${Const.MAGISK_LOG}").submit(cb)

    suspend fun insert(log: SuLog) = logDao.insert(log)

}
