package com.are.distribuidora.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.data.local.dao.ProductDao
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Worker encargado de subir imágenes pendientes a Firebase Storage
 * y actualizar Firestore + Room con la URL remota.
 *
 * Constraints: NetworkType.CONNECTED
 * Backoff: EXPONENTIAL, 30s
 *
 * Ruta determinística en Storage: products/{productId}/main.jpg
 * → Idempotente: si se reintenta, sobreescribe el mismo archivo.
 */
@HiltWorker
class UploadPendingProductImagesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingUploadDao: PendingUploadDao,
    private val productDao: ProductDao,
    private val pendingAccountDao: PendingAccountDao,
    private val firebaseStorage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    private val pendingAccountSyncScheduler: PendingAccountSyncScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started. attempt=$runAttemptCount")

        val pendingList = pendingUploadDao.getNextPending(limit = 10)

        if (pendingList.isEmpty()) {
            Log.d(TAG, "No pending uploads found. Finishing with success.")
            return Result.success()
        }

        var hasFailures = false

        for (pending in pendingList) {
            try {
                Log.d(TAG, "Processing upload: id=${pending.id} entityId=${pending.entityId} state=${pending.state}")

                // Mark as uploading
                pendingUploadDao.markUploading(pending.id)

                val localFile = File(pending.localUri)
                if (!localFile.exists()) {
                    val errorMsg = "Local file not found: ${pending.localUri}"
                    Log.e(TAG, errorMsg)
                    pendingUploadDao.markFailed(pending.id, errorMsg, pending.attemptCount + 1)
                    hasFailures = true
                    continue
                }

                // Upload to Firebase Storage using deterministic path
                val storageRef = firebaseStorage.reference.child(pending.storagePath)
                Log.d(TAG, "Uploading to Storage: ${pending.storagePath}")

                storageRef.putFile(Uri.fromFile(localFile)).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                Log.d(TAG, "Upload success. downloadUrl=$downloadUrl")

                // Handle post-upload based on entity type
                when (pending.entityType) {
                    "PRODUCT" -> {
                        // Update Firestore document with remote URL (NEVER local)
                        val firestoreUpdate = mapOf<String, Any>(
                            "imageUrl" to downloadUrl
                        )
                        firestore.collection("productos")
                            .document(pending.entityId)
                            .update(firestoreUpdate)
                            .await()

                        Log.d(TAG, "Firestore updated for product ${pending.entityId}")

                        // Update Room product with remote URL in imageUrl
                        productDao.updateImageRemoteUrl(pending.entityId, downloadUrl)
                        Log.d(TAG, "Room product updated: imageUrl=$downloadUrl for ${pending.entityId}")
                    }

                    "PENDING_ACCOUNT" -> {
                        // Update Room pending_account with remote URL
                        pendingAccountDao.updateInvoiceRemoteUrl(
                            id = pending.entityId,
                            remoteUrl = downloadUrl,
                            now = System.currentTimeMillis(),
                        )
                        Log.d(TAG, "Room pending_account updated: invoiceRemoteUrl=$downloadUrl for ${pending.entityId}")

                        // Re-encolar sync de Firestore para que invoiceRemoteUrl llegue a otros dispositivos
                        pendingAccountSyncScheduler.enqueueSync()
                        Log.d(TAG, "Firestore sync re-enqueued after image upload for ${pending.entityId}")
                    }

                    else -> {
                        Log.w(TAG, "Unknown entityType: ${pending.entityType} for id=${pending.id}")
                    }
                }

                // Mark upload as done
                pendingUploadDao.markDone(pending.id)
                Log.d(TAG, "Upload marked as DONE: id=${pending.id}")

            } catch (e: Exception) {
                val errorMsg = "${e::class.java.simpleName}: ${e.message}"
                Log.e(TAG, "Upload failed for id=${pending.id} entityId=${pending.entityId}: $errorMsg", e)
                pendingUploadDao.markFailed(pending.id, errorMsg, pending.attemptCount + 1)
                hasFailures = true
            }
        }

        // Clean up old DONE uploads (older than 7 days)
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        pendingUploadDao.deleteDoneOlderThan(sevenDaysAgo)

        return if (hasFailures) {
            Log.d(TAG, "Some uploads failed. Scheduling retry.")
            Result.retry()
        } else {
            // Check if there are more pending after this batch
            val remainingCount = pendingUploadDao.countPending()
            if (remainingCount > 0) {
                Log.d(TAG, "More pending uploads ($remainingCount). Scheduling retry to continue.")
                Result.retry()
            } else {
                Log.d(TAG, "All uploads completed successfully.")
                Result.success()
            }
        }
    }

    companion object {
        const val TAG = "ImageSync"
        const val UNIQUE_WORK_NAME = "upload_product_images"
    }
}

