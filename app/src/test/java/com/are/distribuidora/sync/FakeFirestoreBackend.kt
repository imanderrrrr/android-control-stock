package com.are.distribuidora.sync

import com.are.distribuidora.data.remote.model.RemoteProduct
import com.are.distribuidora.data.remote.pedido.PedidoRemoteDataSource
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Un único "Firestore" compartido por varios dispositivos, con las reglas que importan para la
 * convergencia del stock en 4.1:
 *
 *  - `productos/{id}.stock` SOLO se mueve con incrementos atómicos (`FieldValue.increment`)
 *    desde el libro de movimientos; nadie escribe el contador como valor absoluto.
 *  - `stock_movements/{id}` es CREATE-ONLY con id determinístico: reescribir un movimiento que ya
 *    existe es un no-op, así que un reintento del worker nunca descuenta dos veces
 *    (`StockMovementFirestoreOps.readExisting` + `writeMissing`).
 *  - cada escritura sella `updatedAt` con un serverTimestamp monótono.
 *
 * Permite reproducir en JVM, de forma determinista, lo que la sesión anterior no pudo cerrar en
 * los AVDs porque el SDK de Firestore del vendedor respondía "client is offline" a
 * `runTransaction`.
 */
class FakeFirestoreBackend(startClock: Long = 1_757_000_000_000L) {

    val products = linkedMapOf<String, RemoteProduct>()

    /** Ids de movimientos ya persistidos (la colección es create-only). */
    val movementIds = linkedSetOf<String>()

    /** Cabeceras de pedidos: id → borrado o no. */
    val deletedPedidos = linkedSetOf<String>()
    val uploadedPedidos = linkedSetOf<String>()

    private var clock = startClock

    fun nextTimestamp(): Long {
        clock += 1_000L
        return clock
    }

    fun seedProduct(id: String, stock: Int) {
        products[id] = RemoteProduct(
            id = id, name = "P-$id", description = null, category = null, price = 10.0,
            imageUrl = null, barcode = null, stock = stock,
            isActive = true, isDeleted = false, createdRemoteAt = clock, updatedRemoteAt = clock,
        )
    }

    fun stockOf(id: String): Int = products.getValue(id).stock ?: 0

    /**
     * La transacción de `StockMovementFirestoreOps`: escribe solo los movimientos que no existen y
     * aplica UN incremento por producto, sellando `updatedAt` con serverTimestamp.
     */
    fun applyMovements(movements: List<StockMovement>) {
        val toWrite = movements.filterNot { it.id in movementIds }
        if (toWrite.isEmpty()) return
        toWrite.forEach { movementIds += it.id }
        val at = nextTimestamp()
        toWrite.groupBy { it.productId }
            .mapValues { (_, ms) -> ms.sumOf { it.signedQuantity } }
            .forEach { (productId, delta) ->
                val current = products[productId]
                products[productId] = current
                    ?.copy(stock = (current.stock ?: 0) + delta, updatedRemoteAt = at)
                    ?: RemoteProduct(
                        // set + merge sobre un producto que aún no existe: SOLO stock y updatedAt.
                        id = productId, name = "", description = null, category = null, price = null,
                        imageUrl = null, barcode = null, stock = delta,
                        isActive = null, isDeleted = null, createdRemoteAt = null, updatedRemoteAt = at,
                    )
            }
    }

    /** Vista de `PedidoRemoteDataSource` para un dispositivo. */
    fun pedidoRemote(): PedidoRemoteDataSource = object : PedidoRemoteDataSource {
        override suspend fun uploadPedido(
            pedidoId: String,
            payload: PedidoRemoteDataSource.PedidoPayload,
            items: List<PedidoRemoteDataSource.PedidoItemPayload>,
            movements: List<StockMovement>,
        ) {
            uploadedPedidos += pedidoId
            applyMovements(movements)
        }

        override suspend fun softDeletePedido(
            routeId: String,
            pedidoId: String,
            orderKey: String?,
            deletedByUid: String?,
            movements: List<StockMovement>,
        ) {
            deletedPedidos += pedidoId
            applyMovements(movements)
        }

        override suspend fun markPedidoExpiredInCloud(routeId: String, pedidoId: String) {}
        override suspend fun hardDeletePedidoFromCloud(routeId: String, pedidoId: String) {}
    }

    /** Vista de `ProductRemoteDataSource` (bajada del catálogo) para un dispositivo. */
    fun catalogRemote(): ProductRemoteDataSource = object : ProductRemoteDataSource {
        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val page = products.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (page.isNotEmpty()) emit(page)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            val existing = products[product.id]
            products[product.id] = product.copy(
                stock = existing?.stock ?: product.stock,
                updatedRemoteAt = nextTimestamp(),
            )
        }

        override suspend fun fetchProductById(id: String): RemoteProduct? = products[id]

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            products[id]?.let { products[id] = it.copy(isDeleted = true, updatedRemoteAt = nextTimestamp()) }
        }
    }
}
