package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler centralizado para encolar el Worker de subida de imágenes.
 *
 * Usa ExistingWorkPolicy.KEEP: si ya hay un work encolado/corriendo con el mismo
 * nombre único, NO se encola otro. Esto evita duplicados y es idempotente.
 *
 * El Worker se ejecutará cuando haya red disponible (NetworkType.CONNECTED)
 * con backoff exponencial de 30 segundos.
 */
@Singleton
class ImageUploadSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Encola el Worker de subida de imágenes pendientes.
     * Seguro de llamar múltiples veces: usa KEEP para no duplicar.
     */
    fun enqueueUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadPendingProductImagesWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UploadPendingProductImagesWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )

        Log.d(
            UploadPendingProductImagesWorker.TAG,
            "Image upload worker enqueued (KEEP policy)"
        )
    }
}

