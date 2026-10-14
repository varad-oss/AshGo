package com.varad.unstoppableash.service.connect

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

/** Every 15 minutes: a heartbeat, and a nudge to bring the connection service back if Android stopped it. */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as AshGoApplication).container
        if (c.auth.current() == null) return Result.success()
        heartbeatOnce(c.pairs, c.presence)
        StayConnectedService.start(applicationContext) // may be refused in the background; that's fine
        return Result.success()
    }

    companion object {
        private const val NAME = "ashgo-heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
