package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Sale
import kotlinx.coroutines.flow.Flow

class GetSalesFromRemoteUseCase(
    private val repo: SaleRepository,
) {
    operator fun invoke(): Flow<List<Sale>> = repo.getSales()
}
