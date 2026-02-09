package com.are.distribuidora

import android.app.Application
import android.util.Log
import com.are.distribuidora.client.sync.ClientSyncCoordinator
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
        fun clientSyncCoordinator(): ClientSyncCoordinator
    }

    override fun onCreate() {
        super.onCreate()

        // IMPORTANTE (2026-01): durante el arranque no debemos ejecutar flujos de debug que puedan
        // disparar UI (Toast/chooser/dialog) desde Dispatchers.IO/Default.
        // El runner queda disponible para invocación manual en debug, pero NO se ejecuta aquí.
        Log.i("DistribuidoraApplication", "DebugSaleRunner deshabilitado en startup")

        // Instanciar ClientSyncCoordinator inmediatamente al arrancar la app.
        // Esto fuerza:
        // 1. La ejecución de su bloque init {}
        // 2. El registro del Flow de conectividad (networkMonitor.isOnline)
        // 3. La sincronización inicial si el dispositivo está online
        //
        // POR QUÉ FUNCIONA:
        // - ClientSyncCoordinator tiene @Inject constructor, por lo que Hilt puede crearlo.
        // - Al acceder via EntryPoint desde Application.onCreate(), Hilt crea la instancia
        //   como Singleton (está en SingletonComponent) y la mantiene viva durante toda la app.
        // - El init {} se ejecuta automáticamente al crear la instancia.
        // - No es un hack: es el patrón correcto para inicializar componentes "eager" en Hilt.
        val entryPoint = EntryPointAccessors.fromApplication(
            this@DistribuidoraApplication,
            ProductSyncEntryPoint::class.java,
        )

        // Esta línea fuerza la creación del Singleton.
        // No necesitamos guardar la referencia porque Hilt la mantiene viva.
        entryPoint.clientSyncCoordinator()
        Log.d("ClientSync", "ClientSyncCoordinator instanciado y registrado al arranque")

        // Sync de catálogo (productos) en background, sin UI.
        // ORDEN CRÍTICO: Routes -> Products -> Clients
        // - Routes no tiene FK a nada
        // - Products tiene FK a Routes
        // - Clients tiene FK a Routes
        // - Inventario solo lee Room.
        appScope.launch {
            try {
                Log.d("Sync", "=== Iniciando sincronización de datos en startup ===")

                // 1. PRIMERO: Descargar rutas (sin FK dependencies)
                Log.d("RouteSync", "Descargando rutas desde Firestore...")
                entryPoint.downloadRoutesUseCase().invoke()
                Log.d("RouteSync", "Rutas descargadas exitosamente")

                // 2. SEGUNDO: Sincronizar productos (dependen de Routes)
                Log.d("ProductSync", "Sincronizando productos desde Firestore...")
                entryPoint.syncProductsUseCase().invoke()
                Log.d("ProductSync", "Productos sincronizados exitosamente")

                Log.d("Sync", "=== Sincronización de startup completada ===")

            } catch (e: Exception) {
                Log.e("Sync", "Error durante sincronización de startup", e)
            }
        }
    }
}
