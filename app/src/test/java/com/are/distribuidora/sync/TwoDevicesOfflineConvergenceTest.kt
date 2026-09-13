package com.are.distribuidora.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore
import com.are.distribuidora.data.repository.PedidoRepositoryImpl
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.CreatePedidoItemInput
import com.are.distribuidora.domain.pedido.model.CreatePedidoParams
import com.are.distribuidora.stockmovement.FakeCurrentUser
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PENDIENTE 1 de la integración de release/4.1 — **convergencia de dos pedidos offline**.
 *
 * La sesión anterior no pudo cerrarlo en vivo: el SDK de Firestore del AVD del vendedor se quedó
 * respondiendo "client is offline" a `runTransaction`, así que su pedido nunca subió. Aquí los dos
 * dispositivos son dos `PedidoRepositoryImpl` con su propia base Room contra UN SOLO
 * [FakeFirestoreBackend], y la prueba es determinista y sin red.
 *
 * Contrato que se verifica: el contador remoto queda en `remoto inicial − Σ de los dos pedidos`,
 * independientemente del ORDEN de subida, y ambos teléfonos convergen a ese valor tras bajar el
 * catálogo. Nunca hay escritura absoluta del contador: solo incrementos del libro de movimientos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TwoDevicesOfflineConvergenceTest {

    private lateinit var backend: FakeFirestoreBackend
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device

    @Before
    fun setup() = runTest {
        backend = FakeFirestoreBackend()
        backend.seedProduct("A", stock = 100)
        // Un producto ya colapsado a 0, como el 88% del catálogo real en producción.
        backend.seedProduct("Z", stock = 0)

        deviceA = Device("admin-1", "Admin", backend)
        deviceB = Device("vend-1", "Vendedor", backend)
        listOf(deviceA, deviceB).forEach { it.seedCatalogFromBackend() }
    }

    @After
    fun tearDown() {
        deviceA.close()
        deviceB.close()
    }

    @Test
    fun `dos pedidos offline convergen cuando sube primero A`() = runTest {
        convergen(uploadOrder = listOf("A", "B"))
    }

    @Test
    fun `dos pedidos offline convergen cuando sube primero B`() = runTest {
        convergen(uploadOrder = listOf("B", "A"))
    }

    private suspend fun convergen(uploadOrder: List<String>) {
        // Los dos teléfonos, sin red, descuentan de su copia local.
        val pedidoA = deviceA.createPedido("A" to 5, "Z" to 2)
        val pedidoB = deviceB.createPedido("A" to 7)

        assertEquals(95, deviceA.stock("A"))
        assertEquals(-2, deviceA.stock("Z"))
        assertEquals(93, deviceB.stock("A"))
        assertEquals("cada teléfono solo ve SU pendiente", 100, backend.stockOf("A"))

        // Vuelve la red: suben en el orden indicado.
        uploadOrder.forEach { who ->
            when (who) {
                "A" -> deviceA.uploadPedido(pedidoA)
                else -> deviceB.uploadPedido(pedidoB)
            }
        }

        assertEquals("remoto = 100 − 5 − 7", 88, backend.stockOf("A"))
        assertEquals(-2, backend.stockOf("Z"))

        // Ambos bajan el catálogo y convergen al contador del servidor.
        deviceA.syncDownstream()
        deviceB.syncDownstream()

        assertEquals(88, deviceA.stock("A"))
        assertEquals(88, deviceB.stock("A"))
        assertEquals(-2, deviceA.stock("Z"))
        assertEquals(-2, deviceB.stock("Z"))
        assertEquals(0, deviceA.pendingDelta("A"))
        assertEquals(0, deviceB.pendingDelta("A"))
    }

    /**
     * Un reintento del worker (el caso que provocó el "client is offline": la primera subida pudo
     * haber llegado al servidor y el cliente no verlo) NO vuelve a descontar: los ids de los
     * movimientos son determinísticos y la colección es create-only.
     */
    @Test
    fun `un reintento de la subida no descuenta dos veces`() = runTest {
        val pedidoB = deviceB.createPedido("A" to 7)

        deviceB.uploadPedido(pedidoB)
        assertEquals(93, backend.stockOf("A"))

        // El worker reintenta con los mismos movimientos (mismos ids determinísticos).
        backend.applyMovements(deviceB.movementsOf(pedidoB))
        assertEquals("segunda escritura = no-op", 93, backend.stockOf("A"))

        deviceB.syncDownstream()
        deviceA.syncDownstream()
        assertEquals(93, deviceB.stock("A"))
        assertEquals(93, deviceA.stock("A"))
    }

    /**
     * Mientras el pedido de B sigue sin subir, B NO debe perder su descuento al bajar el catálogo
     * con el pedido de A ya aplicado: stock local = remoto + pendientes propios.
     */
    @Test
    fun `el pedido aun sin subir sobrevive a la bajada del pedido ajeno`() = runTest {
        val pedidoA = deviceA.createPedido("A" to 5)
        deviceB.createPedido("A" to 7)

        deviceA.uploadPedido(pedidoA)
        assertEquals(95, backend.stockOf("A"))

        deviceB.syncDownstream()
        assertEquals("95 del servidor − 7 aún pendientes en B", 88, deviceB.stock("A"))
        assertEquals(-7, deviceB.pendingDelta("A"))
    }

    // ───────────────────────────── un teléfono ─────────────────────────────

    private class Device(uid: String, name: String, backend: FakeFirestoreBackend) {
        val db: DistribuidoraDatabase = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DistribuidoraDatabase::class.java,
        ).allowMainThreadQueries().build()

        private val backendRef = backend

        val pedidoRepo = PedidoRepositoryImpl(
            database = db,
            pedidoDao = db.pedidoDao(),
            pedidoItemDao = db.pedidoItemDao(),
            productDao = db.productDao(),
            remoteDataSource = backend.pedidoRemote(),
            currentUserIdProvider = FakeCurrentUser(uid = uid, name = name),
            movementDao = db.stockMovementDao(),
        )

        val syncRepo = ProductSyncRepositoryImpl(
            remote = backend.catalogRemote(),
            local = db.productDao(),
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
            movementDao = db.stockMovementDao(),
            cursorStore = InMemoryProductSyncCursorStore(),
        )

        private val vendedorId = uid

        suspend fun seedCatalogFromBackend() {
            backendRef.products.values.forEach { r ->
                db.productDao().insert(
                    ProductEntity(
                        id = r.id, name = r.name, description = null, category = null, price = 10.0,
                        imageUrl = null, imageLocalUri = null, barcode = null,
                        stock = r.stock ?: 0, isActive = true, isDeleted = false,
                        syncStatus = SyncStatus.SYNCED,
                        createdAt = r.updatedRemoteAt ?: 0L,
                        updatedAt = r.updatedRemoteAt ?: 0L,
                        lastSyncedAt = r.updatedRemoteAt ?: 0L,
                    )
                )
            }
        }

        suspend fun createPedido(vararg items: Pair<String, Int>): String {
            val result = pedidoRepo.createPedido(
                CreatePedidoParams(
                    vendedorId = vendedorId,
                    routeId = "route-1",
                    deliveryDate = "2026-09-13",
                    clienteId = "cli-$vendedorId",
                    clienteSnapshot = ClienteSnapshot(nombre = "Tienda", telefono = null, direccion = null),
                    items = items.map { (pid, qty) -> CreatePedidoItemInput(pid, "P-$pid", 10.0, qty, 0.0) },
                    descuentoGlobal = 0.0,
                    totalRedondeado = items.sumOf { it.second * 10.0 },
                )
            )
            return (result as Result.Success).value
        }

        suspend fun uploadPedido(pedidoId: String) {
            val pending = pedidoRepo.getPendingPedidosForSync(20).first { it.pedido.id == pedidoId }
            pedidoRepo.uploadAndMarkSynced(pending)
        }

        suspend fun syncDownstream() = syncRepo.syncDownstream()

        suspend fun stock(id: String): Int = db.productDao().getById(id)!!.stock

        suspend fun pendingDelta(productId: String): Int = db.stockMovementDao().sumPendingDelta(productId)

        suspend fun movementsOf(pedidoId: String) =
            db.stockMovementDao().getByOrderId(pedidoId).map { it.toDomain() }

        fun close() = db.close()
    }
}
