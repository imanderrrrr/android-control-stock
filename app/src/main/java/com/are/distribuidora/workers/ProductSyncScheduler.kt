package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.are.distribuidora.core.sync.SyncDebug
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private fun getWorkManager() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        val runId = UUID.randomUUID().toString().take(8)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(SyncDebug.KEY_RUN_ID, runId)
            .build()

        val request = PeriodicWorkRequestBuilder<ProductSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        getWorkManager().enqueueUniquePeriodicWork(
            SyncDebug.UNIQUE_WORK_NAME_PRODUCTS,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        
        Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} SCHEDULE_PERIODIC_OK uniqueName=${SyncDebug.UNIQUE_WORK_NAME_PRODUCTS} interval=6h policy=KEEP")
    }

    fun scheduleOneTimeNow(reason: String) {
        val runId = UUID.randomUUID().toString().take(8)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(SyncDebug.KEY_RUN_ID, runId)
            .putString("reason", reason)
            .build()

        val request = OneTimeWorkRequestBuilder<ProductSyncWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        val uniqueName = "ProductSyncNow"

        Log.d(SyncDebug.TAG_SYNC, "${SyncDebug.prefix(runId)} ENQUEUE_NOW_START reason='$reason' uniqueName=$uniqueName policy=REPLACE")

        getWorkManager().enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
