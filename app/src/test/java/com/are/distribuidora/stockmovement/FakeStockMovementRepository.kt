package com.are.distribuidora.stockmovement

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.repository.StockMovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Libro de movimientos en memoria para tests JVM puros.
 * [onApply] recibe (productId, delta con signo) para que el test mueva su propio stock fake.
 */
class FakeStockMovementRepository(
    private val onApply: (productId: String, signedDelta: Int) -> Unit = { _, _ -> },
) : StockMovementRepository {
    private val store = MutableStateFlow<List<StockMovement>>(emptyList())
    val recorded: List<StockMovement> get() = store.value
    var uploadCalls = 0

    override suspend fun recordVoucher(movement: StockMovement) {
        if (store.value.any { it.id == movement.id }) return
        store.value = store.value + movement
        onApply(movement.productId, movement.signedQuantity)
    }

    override fun observeByProduct(productId: String, limit: Int): Flow<List<StockMovement>> =
        store.map { list -> list.filter { it.productId == productId }.sortedByDescending { it.createdAt }.take(limit) }

    override suspend fun uploadPending() { uploadCalls++ }
}

class FakeCurrentUser(private val uid: String? = "uid-test", private val name: String? = "Tester") : CurrentUserIdProvider {
    override fun get(): String? = uid
    override fun getDisplayName(): String? = name
}
