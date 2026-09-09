package com.are.distribuidora.screenaccess.data.repository

import android.util.Log
import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.screenaccess.data.local.dao.ScreenAccessDao
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import com.are.distribuidora.screenaccess.data.remote.ScreenAccessRemoteDataSource
import com.are.distribuidora.screenaccess.domain.model.AppScreen
import com.are.distribuidora.roles.domain.Role
import com.are.distribuidora.screenaccess.domain.model.UserAccess
import com.are.distribuidora.screenaccess.domain.repository.ScreenAccessRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Implementación offline-first del rol y control de acceso por pantalla.
 *
 * - La cache local (Room) es la ÚNICA fuente que alimenta a la UI y a los casos de uso.
 * - En paralelo, un listener de Firestore actualiza esa cache en tiempo real
 *   (best-effort). Si falla (sin red, permisos), se ignora y se sigue sirviendo
 *   la última configuración conocida.
 * - Mínimo privilegio: sin sesión, sin fila en cache o sin rol ⇒ vendedor.
 *   (Invierte el default-allow de la versión 3.x.)
 */
class ScreenAccessRepositoryImpl(
    private val dao: ScreenAccessDao,
    private val remote: ScreenAccessRemoteDataSource,
    private val currentUserIdProvider: CurrentUserIdProvider,
) : ScreenAccessRepository {

    private val tag = "ScreenAccessRepo"

    override fun observeUserAccess(): Flow<UserAccess> {
        val uid = currentUserIdProvider.get()
            ?: return flowOf(UserAccess.leastPrivilege())

        return callbackFlow {
            // 1) Fuente de verdad para la UI: la cache local.
            val cacheJob = launch {
                dao.observeByUid(uid).collect { entity ->
                    trySend(entity?.toUserAccess() ?: UserAccess.leastPrivilege())
                }
            }
            // 2) Sincronización en tiempo real Firestore → Room (best-effort).
            val syncJob = launch {
                remote.observe(uid)
                    .catch { Log.w(tag, "listener remoto falló: ${it.message}") }
                    .collect { remoteEntity -> dao.upsert(remoteEntity) }
            }
            awaitClose {
                cacheJob.cancel()
                syncJob.cancel()
            }
        }
    }

    override suspend fun current(): UserAccess {
        val uid = currentUserIdProvider.get() ?: return UserAccess.leastPrivilege()
        return dao.getByUid(uid)?.toUserAccess() ?: UserAccess.leastPrivilege()
    }

    override suspend fun refreshFromRemote(): Boolean {
        val uid = currentUserIdProvider.get() ?: return false
        return try {
            dao.upsert(remote.fetchOnce(uid))
            true
        } catch (e: Exception) {
            Log.w(tag, "refreshFromRemote falló (se usa cache): ${e.message}")
            false
        }
    }

    override suspend fun clearCache(uid: String?) {
        if (uid.isNullOrBlank()) dao.deleteAll() else dao.deleteByUid(uid)
    }
}

/** Convierte la fila cacheada en el modelo de dominio (claves nulas = ausentes). */
internal fun ScreenAccessEntity.toUserAccess(): UserAccess {
    val overrides = buildMap {
        inicio?.let { put(AppScreen.INICIO, it) }
        inventario?.let { put(AppScreen.INVENTARIO, it) }
        pedidos?.let { put(AppScreen.PEDIDOS, it) }
        clientes?.let { put(AppScreen.CLIENTES, it) }
        reportes?.let { put(AppScreen.REPORTES, it) }
        cuentasPendientes?.let { put(AppScreen.CUENTAS_PENDIENTES, it) }
    }
    return UserAccess(role = Role.fromKey(role), screenOverrides = overrides)
}
