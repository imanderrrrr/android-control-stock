package com.are.distribuidora.domain.sale

import com.are.distribuidora.domain.model.Sale
import kotlinx.coroutines.flow.Flow

interface SaleRepository {
    fun getSales(): Flow<List<Sale>>
}
