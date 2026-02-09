package com.are.distribuidora.client.sync

import android.util.Log
import com.are.distribuidora.client.domain.usecase.SyncClientsUseCase
import com.are.distribuidora.core.di.ApplicationScope
import com.are.distribuidora.core.network.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject

class ClientSyncCoordinator @Inject constructor(
    private val syncUseCase: SyncClientsUseCase,
    private val networkMonitor: NetworkMonitor,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val mutex = Mutex()

    private companion object {
        private const val TAG = "ClientSyncCoordinator"
    }

    init {
        Log.d(TAG, "ClientSyncCoordinator iniciado - Monitoreando cambios de conectividad")

        firebaseAuth.addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                Log.d(TAG, "Usuario autenticado detectado - Disparando sincronización")
                triggerSync()
            }
        }

        if (networkMonitor.isOnline() == true && firebaseAuth.currentUser != null) {
            Log.d(TAG, "Dispositivo online y usuario autenticado al inicio - Disparando sincronización inicial")
            triggerSync()
        }

        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            networkMonitor.isOnline
                .collect { isOnline ->
                    if (isOnline == true && firebaseAuth.currentUser != null) {
                        Log.d(TAG, "Conectividad detectada (ONLINE) y usuario autenticado - Disparando sync")
                        triggerSync()
                    }
                }
        }
    }

    fun notifyLocalChange() {
        Log.d(TAG, "notifyLocalChange() llamado - isOnline=${networkMonitor.isOnline()}")
        if (firebaseAuth.currentUser == null) {
            Log.d(TAG, "notifyLocalChange() - Usuario no autenticado, sincronización bloqueada")
            return
        }
        if (networkMonitor.isOnline() == true) {
            triggerSync()
        } else {
            Log.d(TAG, "notifyLocalChange() - Offline, sincronización pospuesta")
        }
    }

    private fun triggerSync() {
        Log.d(TAG, "triggerSync() - Intentando iniciar sincronización")

        if (firebaseAuth.currentUser == null) {
            Log.d(TAG, "triggerSync() - Usuario no autenticado, sincronización abortada")
            return
        }

        applicationScope.launch {
            if (!mutex.tryLock()) {
                Log.d(TAG, "triggerSync() - Sincronización ya en progreso (mutex bloqueado), saltando")
                return@launch
            }
            try {
                Log.d(TAG, "triggerSync() - Ejecutando SyncClientsUseCase")
                syncUseCase()
                Log.d(TAG, "triggerSync() - SyncClientsUseCase completado exitosamente")
            } catch (e: Exception) {
                Log.e(TAG, "triggerSync() - Error durante sincronización", e)
                val handler = coroutineContext[CoroutineExceptionHandler]
                if (handler != null) {
                    handler.handleException(coroutineContext, e)
                } else {
                    e.printStackTrace()
                }
            } finally {
                mutex.unlock()
                Log.d(TAG, "triggerSync() - Mutex liberado")
            }
        }
    }
}
