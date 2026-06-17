package com.are.distribuidora.screenaccess.domain.repository

import com.are.distribuidora.screenaccess.domain.model.ScreenAccess
import kotlinx.coroutines.flow.Flow

/**
 * Fuente de permisos de pantalla del usuario autenticado.
 *
 * Offline-first: emite siempre desde la cache local (Room) y, en paralelo y de
 * forma best-effort, mantiene esa cache sincronizada en tiempo real con
 * Firestore (`userScreenAccess/{uid}`). Sin red, sigue funcionando con la última
 * configuración conocida.
 */
interface ScreenAccessRepository {
    /** Emite los permisos del usuario actual; aplica la regla default-allow. */
    fun observeScreenAccess(): Flow<ScreenAccess>
}
