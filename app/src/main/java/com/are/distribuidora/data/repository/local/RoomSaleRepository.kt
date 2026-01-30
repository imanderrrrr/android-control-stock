package com.are.distribuidora.data.repository.local

import com.are.distribuidora.data.local.dao.SaleDao
import com.are.distribuidora.data.local.dao.SaleItemDao
import com.are.distribuidora.data.local.mapper.toDomain
import com.are.distribuidora.domain.model.Sale
import com.are.distribuidora.domain.sale.SaleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RoomSaleRepository(
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
) : SaleRepository {

    override fun getSales(): Flow<List<Sale>> = flow {
        val sales = saleDao.getAll()
        val domain = sales.map { saleEntity ->
            val items = saleItemDao.getBySaleId(saleEntity.id)
            saleEntity.toDomain(items)
        }
        emit(domain)
    }
}
