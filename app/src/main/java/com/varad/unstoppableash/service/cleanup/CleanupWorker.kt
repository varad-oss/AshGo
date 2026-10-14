package com.varad.unstoppableash.service.cleanup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.varad.unstoppableash.AshGoApplication
import java.util.concurrent.TimeUnit

/** D-14 retention, at most once a day, when online and the battery isn't low. */
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as AshGoApplication).container
        if (c.auth.current() == null) return Result.success()
        Cleanup(c.rtdb, c.pairs, c.clock).run()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("ashgo-cleanup", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
