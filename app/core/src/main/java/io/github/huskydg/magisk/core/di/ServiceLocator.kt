package io.github.huskydg.magisk.core.di

import android.annotation.SuppressLint
import android.content.Context
import android.text.method.LinkMovementMethod
import androidx.room.Room
import io.github.huskydg.magisk.core.AppContext
import io.github.huskydg.magisk.core.Const
import io.github.huskydg.magisk.core.data.SuLogDatabase
import io.github.huskydg.magisk.core.data.magiskdb.PolicyDao
import io.github.huskydg.magisk.core.data.magiskdb.SettingsDao
import io.github.huskydg.magisk.core.data.magiskdb.StringDao
import io.github.huskydg.magisk.core.ktx.deviceProtectedContext
import io.github.huskydg.magisk.core.repository.LogRepository
import io.github.huskydg.magisk.core.repository.NetworkService
import io.github.huskydg.magisk.core.tasks.RepoUpdater
import io.github.huskydg.magisk.data.database.RepoDatabase
import io.noties.markwon.Markwon
import io.noties.markwon.utils.NoCopySpannableFactory

@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    val deContext by lazy { AppContext.deviceProtectedContext }
    val timeoutPrefs by lazy { deContext.getSharedPreferences("su_timeout", 0) }

    // Database
    val policyDB = PolicyDao()
    val settingsDB = SettingsDao()
    val stringDB = StringDao()
    val sulogDB by lazy { createSuLogDatabase(deContext).suLogDao() }
    val logRepo by lazy { LogRepository(sulogDB) }
    val repoDB by lazy { RepoDatabase.getInstance() }
    val repoDao by lazy { repoDB.repoDao() }
    val blacklistDao by lazy { repoDB.blacklistDao() }

    // Networking
    val okhttp by lazy { createOkHttpClient(AppContext) }
    val retrofit by lazy { createRetrofit(okhttp) }
    val markwon by lazy { createMarkwon(AppContext) }
    val networkService by lazy {
        NetworkService(
            createApiService(retrofit, Const.Url.GITHUB_PAGE_URL),
            createApiService(retrofit, Const.Url.GITHUB_RAW_URL),
        )
    }
    
    // Tasks
    val repoUpdater by lazy { RepoUpdater(networkService, repoDao) }
}

private fun createSuLogDatabase(context: Context) =
    Room.databaseBuilder(context, SuLogDatabase::class.java, "sulogs.db")
        .addMigrations(SuLogDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

private fun createMarkwon(context: Context) =
    Markwon.builder(context).textSetter { textView, spanned, bufferType, onComplete ->
        textView.apply {
            movementMethod = LinkMovementMethod.getInstance()
            setSpannableFactory(NoCopySpannableFactory.getInstance())
            setText(spanned, bufferType)
            onComplete.run()
        }
    }.build()
