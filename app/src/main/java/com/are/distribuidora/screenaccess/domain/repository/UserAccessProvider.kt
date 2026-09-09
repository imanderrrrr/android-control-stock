package com.are.distribuidora.screenaccess.domain.repository

import com.are.distribuidora.screenaccess.domain.model.UserAccess

/**
 * Punto de consulta puntual (no reactivo) del acceso del usuario actual, para que los
 * casos de uso rechacen acciones prohibidas con `Failure.Forbidden`.
 *
 * Lee la cache local (Room): funciona offline y no espera a la red.
 */
fun interface UserAccessProvider {
    suspend fun current(): UserAccess
}
