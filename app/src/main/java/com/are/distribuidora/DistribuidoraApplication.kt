package com.are.distribuidora

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.are.distribuidora.client.sync.ClientSyncCoordinator
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.product.SyncProductsUseCase
import com.are.distribuidora.route.domain.usecase.DownloadRoutesUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DistribuidoraApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var productSyncScheduler: com.are.distribuidora.workers.ProductSyncScheduler
    @Inject lateinit var pedidoExpireScheduler: com.are.distribuidora.workers.PedidoExpireScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProductSyncEntryPoint {
        fun syncProductsUseCase(): SyncProductsUseCase
        fun downloadRoutesUseCase(): DownloadRoutesUseCase
        fun clientSyncCoordinator(): ClientSyncCoordinator
        fun pedidoRepository(): PedidoRepository
    }

    override val workManagerConfiguration: Configuration
        get() {
            Log.d("WorkManagerConfig", "workManagerConfiguration called, workerFactory=$workerFactory")
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
        }

    override fun onCreate() {
        super.onCreate()

        Log.d("DistribuidoraApplication", "onCreate() started, workerFactory injected: ${::workerFactory.isInitialized}")

        // IMPORTANTE (2026-01): durante el arranque no debemos ejecutar flujos de debug que puedan
        // disparar UI (Toast/chooser/dialog) desde Dispatchers.IO/Default.
        // El runner queda disponible para invocación manual en debug, pero NO se ejecuta aquí.
        Log.i("DistribuidoraApplication", "DebugSaleRunner deshabilitado en startup")

        // Instanciar ClientSyncCoordinator inmediatamente al arrancar la app.
        val entryPoint = EntryPointAccessors.fromApplication(
            this@DistribuidoraApplication,
            ProductSyncEntryPoint::class.java,
        )

        entryPoint.clientSyncCoordinator()
        Log.d("ClientSync", "ClientSyncCoordinator instanciado y registrado al arranque")

        // Enqueue Workers (Enterprise-Grade Background Sync)
        // Use Scheduler to keep consistency
        productSyncScheduler.schedulePeriodic()

        // Expira y borra pedidos con ≥ 14 días de antigüedad (local + Firestore)
        pedidoExpireScheduler.schedule()

        // Sync manual logic for Routes (could be moved to Worker too in future)
        // Leaving it here to ensure Routes are present before Products if possible,
        // though Worker is async.
        appScope.launch {
            try {
                Log.d("Sync", "=== Iniciando sincronización de startup ===")

                // 0. Recuperar pedidos atascados en SYNCING por crash/kill de proceso previo.
                //    Deben revertirse a PENDING_CREATE para que el Worker los reintente.
                try {
                    entryPoint.pedidoRepository().recoverStuckSyncingPedidos()
                    Log.d("PedidoSync", "recoverStuckSyncingPedidos: completado")
                } catch (e: Exception) {
                    Log.e("PedidoSync", "recoverStuckSyncingPedidos: error (no bloqueante)", e)
                }

                // 1. Descargar rutas
                Log.d("RouteSync", "Descargando rutas desde Firestore...")
                entryPoint.downloadRoutesUseCase().invoke()
                Log.d("RouteSync", "Rutas descargadas exitosamente")

                // Products are now handled by WorkManager
                Log.d("Sync", "WorkManager encargado de ProductSync")

                Log.d("Sync", "=== Sincronización de startup completada (Rutas ok, Productos en background) ===")

            } catch (e: Exception) {
                Log.e("Sync", "Error durante sincronización de startup", e)
            }
        }
    }
}
