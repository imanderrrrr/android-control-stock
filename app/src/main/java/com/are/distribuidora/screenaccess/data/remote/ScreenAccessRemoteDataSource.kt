package com.are.distribuidora.screenaccess.data.remote

import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del origen remoto de `userScreenAccess/{uid}` (Firestore en producción,
 * fake en tests).
 */
interface ScreenAccessRemoteDataSource {
    /** Listener en tiempo real. Cierra con error si Firestore reporta uno. */
    fun observe(uid: String): Flow<ScreenAccessEntity>

    /** Lectura puntual (server, con fallback a la cache de Firestore). Lanza si falla. */
    suspend fun fetchOnce(uid: String): ScreenAccessEntity
}
