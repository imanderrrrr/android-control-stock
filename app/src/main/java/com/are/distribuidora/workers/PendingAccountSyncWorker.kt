package com.are.distribuidora.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.are.distribuidora.data.remote.firestore.FirestorePendingAccountDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker que sincroniza cuentas pendientes con Firestore:
 * 1. Upload: sube cuentas con syncStatus != SYNCED
 * 2. Download: descarga cuentas de Firestore y hace upsert local
 */
@HiltWorker
class PendingAccountSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingAccountDao: PendingAccountDao,
    private val firestoreDataSource: FirestorePendingAccountDataSource,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "PendingAccSync"
        const val UNIQUE_WORK_NAME = "pending_account_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "PendingAccountSyncWorker started")
        return try {
            // 1. Upload: subir cambios locales a Firestore
            uploadPending()

            // 2. Download: descargar cambios remotos
            downloadRemote()

            Log.d(TAG, "PendingAccountSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PendingAccountSyncWorker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun uploadPending() {
        val pending = pendingAccountDao.getPendingSync()
        Log.d(TAG, "Upload: ${pending.size} cuentas pendientes de sync")

        for (entity in pending) {
            try {
                firestoreDataSource.upsert(entity)
                pendingAccountDao.markSynced(entity.id)
                Log.d(TAG, "Uploaded: ${entity.id} (${entity.resolvedAction ?: "active"})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload ${entity.id}", e)
                throw e // Will trigger retry
            }
        }
    }

    private suspend fun downloadRemote() {
        val remoteAccounts = firestoreDataSource.fetchAll()
        Log.d(TAG, "Download: ${remoteAccounts.size} cuentas remotas")

        val localIds = pendingAccountDao.getAllIds().toSet()

        for (remote in remoteAccounts) {
            // Si existe localmente, preservar invoicePhotoUri local
            val local = if (remote.id in localIds) {
                pendingAccountDao.getById(remote.id)
            } else null

            val merged = remote.copy(
                invoicePhotoUri = local?.invoicePhotoUri ?: remote.invoicePhotoUri,
                syncStatus = SyncStatus.SYNCED,
            )

            // Solo actualizar si el remoto es más reciente
            if (local == null || remote.updatedAt >= local.updatedAt) {
                pendingAccountDao.upsertFromRemote(merged)
                Log.d(TAG, "Merged remote: ${remote.id} action=${remote.resolvedAction}")
            }
        }
    }
}
