package com.are.distribuidora.screenaccess.data.repository

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.screenaccess.data.local.dao.ScreenAccessDao
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import com.are.distribuidora.screenaccess.data.remote.FirestoreScreenAccessDataSource
import com.are.distribuidora.screenaccess.domain.model.AppScreen
import com.are.distribuidora.screenaccess.domain.model.ScreenAccess
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Implementación offline-first del control de acceso por pantalla.
 *
 * - La cache local (Room) es la ÚNICA fuente que alimenta a la UI.
 * - En paralelo, un listener de Firestore actualiza esa cache en tiempo real
 *   (best-effort). Si falla (sin red, permisos), se ignora y se sigue sirviendo
 *   la última configuración conocida.
 * - Default-allow: sin fila en cache (o claves nulas) ⇒ todo permitido.
 */
class ScreenAccessRepositoryImpl(
    private val dao: ScreenAccessDao,
    private val remote: FirestoreScreenAccessDataSource,
    private val currentUserIdProvider: CurrentUserIdProvider,
) : ScreenAccessRepository {

    override fun observeScreenAccess(): Flow<ScreenAccess> {
        val uid = currentUserIdProvider.get()
            ?: return flowOf(ScreenAccess.allowAll()) // Sin sesión: no restringir.

        return callbackFlow {
            // 1) Fuente de verdad para la UI: la cache local.
            val cacheJob = launch {
                dao.observeByUid(uid).collect { entity ->
                    trySend(entity?.toScreenAccess() ?: ScreenAccess.allowAll())
                }
            }
            // 2) Sincronización en tiempo real Firestore → Room (best-effort).
            val syncJob = launch {
                remote.observe(uid)
                    .catch { /* sin red / error: conservar cache */ }
                    .collect { remoteEntity -> dao.upsert(remoteEntity) }
            }
            awaitClose {
                cacheJob.cancel()
                syncJob.cancel()
            }
        }
    }
}

/** Convierte la fila cacheada en el modelo de dominio (claves nulas = ausentes). */
private fun ScreenAccessEntity.toScreenAccess(): ScreenAccess {
    val flags = buildMap {
        inicio?.let { put(AppScreen.INICIO, it) }
        inventario?.let { put(AppScreen.INVENTARIO, it) }
        pedidos?.let { put(AppScreen.PEDIDOS, it) }
        clientes?.let { put(AppScreen.CLIENTES, it) }
        reportes?.let { put(AppScreen.REPORTES, it) }
        cuentasPendientes?.let { put(AppScreen.CUENTAS_PENDIENTES, it) }
    }
    return ScreenAccess(flags)
}
