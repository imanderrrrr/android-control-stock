package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.model.ReportParams
import com.are.distribuidora.domain.pedido.model.ReportResult
import javax.inject.Inject

/**
 * Obtiene los datos de reporte de ventas para los últimos 7 y 14 días.
 *
 * Encapsula el cálculo de los rangos de fecha y delega la consulta
 * al repositorio, manteniendo la capa de Presentación libre de
 * cualquier dependencia hacia Room o DAOs.
 */
class GetReportDataUseCase @Inject constructor(
    private val repository: PedidoRepository,
) {
    /**
     * @param sinceEpoch14 Timestamp en millis del inicio del período de 14 días.
     * @param sinceEpoch7  Timestamp en millis del inicio del período de 7 días.
     * @param topLimit     Cantidad máxima de items en listas top/bottom (default 5).
     */
    suspend operator fun invoke(
        sinceEpoch14: Long,
        sinceEpoch7: Long,
        topLimit: Int = 5,
    ): ReportResult = repository.getReportData(
        ReportParams(
            sinceEpoch14 = sinceEpoch14,
            sinceEpoch7 = sinceEpoch7,
            topLimit = topLimit,
        )
    )
}


