package com.are.distribuidora.core.network

/**
 * Contrato mínimo para consultar conectividad.
 *
 * Nota: se mantiene en `core` para que los casos de uso no dependan de Android.
 * La implementación concreta vive en data/infrastructure.
 */
interface NetworkMonitor {
    val isOnline: kotlinx.coroutines.flow.Flow<Boolean>
    fun isOnline(): Boolean
}
