package org.totschnig.myexpenses.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.maybeRepairRequerySchema

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefHandler = (application as MyApplication).appComponent.prefHandler()
        val version = prefHandler.getInt(PrefKey.CURRENT_VERSION, -1)
        if (version == -1) {
            getStarted(OnboardingActivity::class.java)
        }
        else {
            if (!prefHandler.encryptDatabase && Build.VERSION.SDK_INT == 30 && version < 591) {
                maybeRepairRequerySchema(getDatabasePath("data").path)
                prefHandler.putBoolean(PrefKey.DB_SAFE_MODE, false)
            }
            getStarted(MyExpenses::class.java)
        }
    }

    private fun getStarted(clazz: Class<out Activity>) {
        startActivity(Intent(this, clazz).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
        finish()
    }
}