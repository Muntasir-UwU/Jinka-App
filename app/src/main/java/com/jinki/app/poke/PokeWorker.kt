package com.jinki.app.poke

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Missed-poke sweep: reads pokes/{myUid} once, notifies, deletes. */
class PokeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            PokeNotifier.drainOnce(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("JinkiPush", "poke worker failed", e)
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE = "pokes"

        fun schedule(context: Context) {
            try {
                val req = PeriodicWorkRequestBuilder<PokeWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext)
                    .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
            } catch (e: Exception) {
                Log.e("JinkiPush", "poke worker schedule failed", e)
            }
        }
    }
}
