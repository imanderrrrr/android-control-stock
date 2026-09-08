package com.are.distribuidora.stockmovement.data.remote.firestore

import android.util.Log
import com.are.distribuidora.stockmovement.data.remote.StockMovementRemoteDataSource
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Subida de vales sueltos. Una transacción por lote pequeño (lecturas + escrituras < 500 ops).
 */
class FirestoreStockMovementDataSource(
    private val firestore: FirebaseFirestore,
) : StockMovementRemoteDataSource {

    private val tag = "SYNC_MOVEMENTS"

    override suspend fun uploadMovements(movements: List<StockMovement>): List<String> {
        if (movements.isEmpty()) return emptyList()
        val persisted = mutableListOf<String>()
        movements.chunked(CHUNK).forEach { chunk ->
            val written = firestore.runTransaction { tx ->
                val existing = StockMovementFirestoreOps.readExisting(tx, firestore, chunk)
                StockMovementFirestoreOps.writeMissing(tx, firestore, chunk, existing)
            }.await()
            val skipped = chunk.size - written.size
            Log.i(tag, "uploadMovements: written=${written.size} alreadyExisted=$skipped")
            persisted += chunk.map { it.id }
        }
        return persisted
    }

    private companion object {
        /** N lecturas + N escrituras + ≤N incrementos por transacción. */
        const val CHUNK = 100
    }
}
