package com.varad.unstoppableash

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit

/**
 * Debug builds only: which Firebase backend this process uses. `--ez useEmulators true|false` on any v2
 * entry point is remembered, so the SOS tile and hold screen (which can't carry the extra) use the same
 * backend as the last dev session. Release builds are never debuggable, so this is a no-op there.
 */
internal object DevBackend {
    private const val PREFS = "v2_dev"
    private const val KEY = "use_emulators"
    const val EXTRA_USE_EMULATORS = "useEmulators"

    fun apply(activity: Activity, container: AppContainer) {
        if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (activity.intent.hasExtra(EXTRA_USE_EMULATORS)) {
            prefs.edit { putBoolean(KEY, activity.intent.getBooleanExtra(EXTRA_USE_EMULATORS, false)) }
        }
        if (prefs.getBoolean(KEY, false) && !container.usesEmulators) runCatching { container.useLocalEmulators() }
    }
}
