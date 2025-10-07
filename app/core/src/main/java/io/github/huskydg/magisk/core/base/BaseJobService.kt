package io.github.huskydg.magisk.core.base

import android.app.job.JobService
import android.content.Context
import io.github.huskydg.magisk.core.patch

abstract class BaseJobService : JobService() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.patch())
    }
}
