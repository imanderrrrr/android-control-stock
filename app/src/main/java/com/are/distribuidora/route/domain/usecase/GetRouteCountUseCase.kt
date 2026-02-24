package com.are.distribuidora.route.domain.usecase

import com.are.distribuidora.route.domain.repository.RouteRepository
import javax.inject.Inject

/**
 * Caso de uso: retorna el número de rutas almacenadas localmente.
 *
 * Útil para diagnóstico y para que la UI verifique si el catálogo está disponible
 * sin acceder directamente a la capa de datos.
 */
class GetRouteCountUseCase @Inject constructor(
    private val repository: RouteRepository,
) {
    suspend operator fun invoke(): Int = repository.countRoutes()
}

