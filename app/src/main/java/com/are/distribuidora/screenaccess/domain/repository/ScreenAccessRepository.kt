package com.are.distribuidora.screenaccess.domain.repository

import com.are.distribuidora.screenaccess.domain.model.UserAccess
import kotlinx.coroutines.flow.Flow

/**
 * Fuente del rol y permisos de pantalla del usuario autenticado.
 *
 * Offline-first: emite siempre desde la cache local (Room) y, en paralelo y de
 * forma best-effort, mantiene esa cache sincronizada en tiempo real con
 * Firestore (`userScreenAccess/{uid}`). Sin red, sigue funcionando con la última
 * configuración conocida. Sin fila en cache ⇒ [UserAccess.leastPrivilege].
 */
interface ScreenAccessRepository : UserAccessProvider {
    /** Emite el acceso del usuario actual (rol + restricciones por pantalla). */
    fun observeUserAccess(): Flow<UserAccess>

    /**
     * Trae UNA vez el documento remoto y lo cachea. Devuelve true si la cache quedó
     * actualizada desde Firestore. Pensado para el arranque: evita que un admin vea
     * la app un instante como vendedor en un teléfono sin cache.
     */
    suspend fun refreshFromRemote(): Boolean

    /** Borra la cache de acceso del usuario indicado (al cerrar sesión). */
    suspend fun clearCache(uid: String?)
}
