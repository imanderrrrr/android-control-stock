package com.are.distribuidora.workers

import android.util.Log
import com.are.distribuidora.core.di.ApplicationScope
import com.are.distribuidora.core.network.NetworkMonitor
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.core.sync.SyncDebug
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductSyncCoordinator @Inject constructor(
    private val scheduler: ProductSyncScheduler,
    private val networkMonitor: NetworkMonitor,
    private val firebaseAuth: FirebaseAuth,
    private val productDao: ProductDao,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private var debounceJob: kotlinx.coroutines.Job? = null
    private val mutex = Mutex()

    init {
        // Auto-sync when coming online if pending items exist
        applicationScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                if (isOnline) {
                    Log.d(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Network restored. Checking for pending products...")
                    checkAndTriggerSync("NETWORK_RESTORED")
                }
            }
        }
    }

    fun notifyLocalChange(source: String) {
        applicationScope.launch {
            mutex.withLock {
                // Cancel previous job if active
                debounceJob?.cancel()
                
                debounceJob = launch {
                    // Debounce: wait 2 seconds for further changes
                    kotlinx.coroutines.delay(2000)
                    
                    checkAndTriggerSync(source)
                }
            }
        }
    }

    private suspend fun checkAndTriggerSync(source: String) {
        try {
            // 1. Auth Check
            if (firebaseAuth.currentUser == null) {
                Log.d(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Skip sync: User not authenticated")
                return
            }

            // 2. Network Check (Interent + Validated)
            // Note: networkMonitor.isOnline usually implies validated/internet access depending on implementation.
            if (!networkMonitor.isOnline()) {
                Log.d(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Skip sync: Offline")
                return
            }

            // 3. Pending Products Check
            val hasPending = productDao.countPending() > 0
            if (!hasPending) {
                Log.d(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Skip sync: No pending products")
                return
            }

            // 4. Trigger Sync
            Log.i(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Triggering immediate sync. Source=$source")
            scheduler.scheduleOneTimeNow(source)

        } catch (e: Exception) {
            Log.e(SyncDebug.TAG_SYNC, "[ProductSyncCoordinator] Error in checkAndTriggerSync", e)
        }
    }
}
