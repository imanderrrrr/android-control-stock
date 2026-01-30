package com.are.distribuidora.data.remote.firestore

import com.are.distribuidora.data.remote.sale.SaleRemoteDataSource

/**
 * Data source remoto que siempre falla al subir ventas.
 * Implementa la interfaz para pruebas offline.
 */
class FailingFirestoreSaleDataSource : SaleRemoteDataSource {

    override suspend fun uploadSale(
        saleId: String,
        sellerId: String,
        createdBy: String,
        finalizedBy: String?,
        items: List<SaleRemoteDataSource.ItemPayload>
    ) {
        throw RuntimeException("Simulated Firestore failure")
    }

    override suspend fun fetchSales(): List<SaleRemoteDataSource.SalePayload> {
        throw RuntimeException("Simulated Firestore failure")
    }
}
