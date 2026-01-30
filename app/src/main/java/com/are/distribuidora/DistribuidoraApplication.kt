package com.are.distribuidora

import android.app.Application
import android.util.Log
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

@HiltAndroidApp
class DistribuidoraApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProductSyncEntryPoint {
        fun syncProductsUseCase(): SyncProductsUseCase
        fun downloadRoutesUseCase(): DownloadRoutesUseCase
    }

    override fun onCreate() {
        super.onCreate()

        // IMPORTANTE (2026-01): durante el arranque no debemos ejecutar flujos de debug que puedan
        // disparar UI (Toast/chooser/dialog) desde Dispatchers.IO/Default.
        // El runner queda disponible para invocación manual en debug, pero NO se ejecuta aquí.
        Log.i("DistribuidoraApplication", "DebugSaleRunner deshabilitado en startup")

        // Sync de catálogo (productos) en background, sin UI.
        // - Inventario solo lee Room.
        // - Este arranque solo ejecuta el flujo existente remoto->local.
        appScope.launch {
            try {
                Log.d("ProductSync", "Starting product sync on app startup")
                Log.d("ProductSync", "Resolving Hilt EntryPoint for SyncProductsUseCase")
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@DistribuidoraApplication,
                    ProductSyncEntryPoint::class.java,
                )
                Log.d("ProductSync", "EntryPoint resolved. Calling usecase()")
                entryPoint.syncProductsUseCase().invoke()
                Log.d("ProductSync", "Product sync finished")

                // Ejecutar descarga de rutas en el mismo flujo de arranque.
                entryPoint.downloadRoutesUseCase().invoke()

            } catch (e: Exception) {
                Log.e("ProductSync", "Product sync failed during app startup", e)
            }
        }
    }
}
