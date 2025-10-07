package io.github.huskydg.magisk.ui.modulerepo

import android.os.Bundle
import io.github.huskydg.magisk.R
import io.github.huskydg.magisk.arch.BaseUIActivity

class ModuleRepoActivity : BaseUIActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_module_repo)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ModuleRepoFragment())
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(io.github.huskydg.magisk.core.R.string.module_repo)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

