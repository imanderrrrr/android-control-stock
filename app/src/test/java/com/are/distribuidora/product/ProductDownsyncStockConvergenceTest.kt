package com.are.distribuidora.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.model.RemoteProduct
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import com.are.distribuidora.stockmovement.data.local.dao.StockMovementDao
import com.are.distribuidora.stockmovement.data.local.entity.StockMovementEntity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

/**
 * PENDIENTE 3 de la integración de release/4.1 — "el teléfono B recibe `Updated=1` en el downsync
 * de productos pero no aplica el stock nuevo y su watermark no avanza".
 *
 * Reproduce el escenario real SIN dispositivos: `ProductSyncRepositoryImpl` con un remoto falso
 * que imita a Firestore (`updatedAt` = timestamp de servidor, `stock` movido solo por
 * `FieldValue.increment` desde el libro de movimientos) y Room en memoria.
 *
 * Datos elegidos según [[produccion-stock-realidad]]: en producción 396 de 450 productos ya están
 * en stock 0, así que el caso principal usa un producto en 0 (donde un downsync roto pasa
 * desapercibido) y se replica con uno de stock positivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductDownsyncStockConvergenceTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
    private lateinit var movementDao: StockMovementDao
    private lateinit var remote: FakeFirestore
    private lateinit var sync: ProductSyncRepositoryImpl

    @Before
    fun setup() {
        ShadowLog.stream = null
        ShadowLog.clear()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
        movementDao = db.stockMovementDao()
        remote = FakeFirestore()
        sync = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
            movementDao = movementDao,
            cursorStore = com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore(),
        )
    }

    @After
    fun tearDown() = db.close()

    // ───────────────── el caso que se vio en los dos teléfonos ─────────────────

    /**
     * Producto con stock remoto 0 (el caso del 88% del catálogo real). B tiene un pedido offline
     * sin subir (movimientos SALIDA pendientes) y A sube un vale de entrada que deja el remoto en
     * 40 con un `updatedAt` de servidor nuevo. B debe quedar en 40 + su delta pendiente, y su
     * watermark debe avanzar al `updatedAt` remoto.
     */
    @Test
    fun `B aplica el vale de A sobre un producto en stock 0 y avanza su watermark`() = runTest {
        seedSyncedOnB(id = "p-cero", stock = 0, updatedAt = T1)
        remote.docs["p-cero"] = doc(id = "p-cero", stock = 0, updatedAt = T1)

        // B confirma un pedido offline de 12 unidades: movimiento pendiente + stock local -12.
        pendingSalidaOnB(id = "mov-b1", productId = "p-cero", qty = 12)
        assertEquals(-12, dao.getById("p-cero")!!.stock)

        // A sube un vale ENTRADA +40: increment remoto + serverTimestamp nuevo.
        remote.applyMovementIncrement("p-cero", delta = +40, at = T2)

        sync.syncDownstream()

        val row = dao.getById("p-cero")!!
        assertEquals("stock local = remoto (40) + pendientes de B (-12)", 28, row.stock)
        assertEquals("el watermark del producto avanza al updatedAt del servidor", T2, row.updatedAt)
        assertEquals(T2, dao.getLastSyncedProduct()!!.updatedAt)
    }

    /** Lo mismo sobre uno de los 54 productos con stock positivo. */
    @Test
    fun `B aplica el vale de A sobre un producto con stock positivo`() = runTest {
        seedSyncedOnB(id = "p-positivo", stock = 30, updatedAt = T1)
        remote.docs["p-positivo"] = doc(id = "p-positivo", stock = 30, updatedAt = T1)

        pendingSalidaOnB(id = "mov-b2", productId = "p-positivo", qty = 5)
        assertEquals(25, dao.getById("p-positivo")!!.stock)

        remote.applyMovementIncrement("p-positivo", delta = +40, at = T2)

        sync.syncDownstream()

        val row = dao.getById("p-positivo")!!
        assertEquals(70 - 5, row.stock)
        assertEquals(T2, row.updatedAt)
    }

    // ───────────────── (a) qué updatedAt tiene B si nunca subió el producto ─────────────────

    /**
     * B nunca sube productos (desde 4.1 ni siquiera sube `stock`), así que su `updatedAt` local
     * SIEMPRE viene del servidor por el downsync: no puede adelantarse al remoto y la rama
     * "Ignored stale remote update" no se dispara por un producto que B solo bajó.
     *
     * Mover el contador con `applyMovement` tampoco ensucia `updatedAt` (esa es la razón de que
     * el pedido offline de B no bloquee la bajada).
     */
    @Test
    fun `el updatedAt local de B siempre viene del servidor y nunca se adelanta`() = runTest {
        remote.docs["p1"] = doc(id = "p1", stock = 7, updatedAt = T1)
        sync.syncDownstream()
        assertEquals("primera bajada: adopta el timestamp del servidor", T1, dao.getById("p1")!!.updatedAt)

        pendingSalidaOnB(id = "mov-b3", productId = "p1", qty = 3)
        assertEquals("applyMovement no toca updatedAt", T1, dao.getById("p1")!!.updatedAt)
        assertEquals(SyncStatus.SYNCED, dao.getById("p1")!!.syncStatus)

        remote.applyMovementIncrement("p1", delta = +40, at = T2)
        sync.syncDownstream()

        assertEquals(44, dao.getById("p1")!!.stock)
        assertEquals(T2, dao.getById("p1")!!.updatedAt)
    }

    /**
     * LA CAUSA REAL del pendiente 3 — el **salto del watermark** que provoca la propia subida.
     *
     * El cursor de bajada era `MAX(updatedAt)` de las filas SYNCED, y la subida de un producto
     * adopta el `updatedAt` que el servidor le asignó (T3). Si entre la última bajada de B y esa
     * subida OTRO teléfono escribió un documento con timestamp intermedio (T2 — el vale de A),
     * el cursor salta de T1 a T3 sin haber bajado nunca T2 y la consulta
     * `updatedAt >= T3` deja ese documento **invisible para siempre**.
     *
     * El síntoma coincide punto por punto con lo observado en los dos teléfonos: llega 1 documento
     * (el producto que B acaba de subir, reincluido por el `>=`), se escribe — de ahí el
     * `Updated=1` —, el stock del OTRO producto no cambia y su watermark no avanza.
     *
     * `SyncProductsUseCase` hace `uploadPendingProducts()` y justo después `syncDownstream()`, así
     * que el salto y la bajada ocurren en el MISMO ciclo del worker.
     */
    @Test
    fun `el vale de A no se pierde cuando la subida de B adelanta el cursor`() = runTest {
        // B: catálogo bajado hasta T1. Un producto en stock 0 y una edición local pendiente.
        seedSyncedOnB(id = "p-cero", stock = 0, updatedAt = T1)
        remote.docs["p-cero"] = doc(id = "p-cero", stock = 0, updatedAt = T1)
        seedDirtyOnB(id = "q-editado", stock = 5, updatedAt = T1, status = SyncStatus.PENDING_UPDATE)
        remote.docs["q-editado"] = doc(id = "q-editado", stock = 5, updatedAt = T1)

        // B confirmó un pedido offline de 12 unidades de p-cero.
        pendingSalidaOnB(id = "mov-b7", productId = "p-cero", qty = 12)
        assertEquals(-12, dao.getById("p-cero")!!.stock)

        // A sube su vale ENTRADA +40 sobre p-cero: el servidor lo sella en T2.
        remote.applyMovementIncrement("p-cero", delta = +40, at = T2)

        // Ciclo del worker en B: primero sube q-editado (el servidor lo sella en T3 > T2) y
        // después baja el catálogo.
        remote.assignServerTimestampOnUpload = T3
        sync.uploadPendingProducts()
        sync.syncDownstream()

        val p = dao.getById("p-cero")!!
        assertEquals("el vale de A debe llegar a B: 40 remoto + (-12) pendiente", 28, p.stock)
        assertEquals("y su watermark debe avanzar al updatedAt del servidor", T2, p.updatedAt)
    }

    /**
     * Misma trampa sin pedidos de por medio y con un producto de stock positivo: cualquier
     * documento con timestamp intermedio se pierde cuando la subida adelanta el cursor.
     */
    @Test
    fun `ningun documento con timestamp intermedio se pierde tras una subida`() = runTest {
        seedSyncedOnB(id = "p-positivo", stock = 30, updatedAt = T1)
        remote.docs["p-positivo"] = doc(id = "p-positivo", stock = 30, updatedAt = T1)
        seedDirtyOnB(id = "q-editado", stock = 5, updatedAt = T1, status = SyncStatus.PENDING_UPDATE)
        remote.docs["q-editado"] = doc(id = "q-editado", stock = 5, updatedAt = T1)

        remote.applyMovementIncrement("p-positivo", delta = -7, at = T2)

        remote.assignServerTimestampOnUpload = T3
        sync.uploadPendingProducts()
        sync.syncDownstream()

        assertEquals(23, dao.getById("p-positivo")!!.stock)
        assertEquals(T2, dao.getById("p-positivo")!!.updatedAt)
    }

    /**
     * La rama "Ignored stale remote update" NO es alcanzable desde `syncDownstream`: la consulta
     * filtra por `updatedAt >= watermark` y el watermark es el máximo local, así que un documento
     * más viejo que su fila local nunca llega. Por eso `StaleIgnored` es 0 en operación normal y
     * el `Updated=N` viejo contaba, de hecho, escrituras reales. Sigue mereciendo la pena contarlo
     * bien: el contador era la única "evidencia" del diagnóstico anterior y no distinguía ambos
     * casos. La rama sí se ejercita desde `saveLocalProducts`, que recibe la lista ya construida.
     */
    @Test
    fun `saveLocalProducts descarta un remoto mas viejo que el local`() = runTest {
        seedSyncedOnB(id = "p1", stock = 10, updatedAt = T2)

        sync.saveLocalProducts(listOf(domainProduct(id = "p1", stock = 40, updatedAt = T1)))

        val row = dao.getById("p1")!!
        assertEquals("write descartado por LWW", 10, row.stock)
        assertEquals(T2, row.updatedAt)
    }

    /** Un write que SÍ ocurre sigue contándose. */
    @Test
    fun `una escritura real si se cuenta como Updated`() = runTest {
        seedSyncedOnB(id = "p1", stock = 10, updatedAt = T1)
        remote.docs["p1"] = doc(id = "p1", stock = 40, updatedAt = T2)

        sync.syncDownstream()

        assertEquals(40, dao.getById("p1")!!.stock)
        assertEquals(1, lastUpdatedCount())
        assertEquals(0, lastStaleCount())
    }

    /**
     * Régimen estacionario: sin cambios remotos, el filtro `updatedAt >= watermark` vuelve a traer
     * el producto-frontera (a propósito: ver el fix del cursor móvil en FirestoreProductDataSource).
     * Ese `Updated=1` repetido es NORMAL — es una reescritura idempotente, no un cambio perdido.
     */
    @Test
    fun `sin cambios remotos el producto frontera se reprocesa de forma idempotente`() = runTest {
        seedSyncedOnB(id = "p1", stock = 10, updatedAt = T1)
        remote.docs["p1"] = doc(id = "p1", stock = 10, updatedAt = T1)

        repeat(3) { sync.syncDownstream() }

        val row = dao.getById("p1")!!
        assertEquals(10, row.stock)
        assertEquals(T1, row.updatedAt)
        assertEquals("el frontera se reescribe con los mismos datos", 1, lastUpdatedCount())
    }

    // ───────────────── (b) el remoto omite `stock` ─────────────────

    /**
     * `StockMovementFirestoreOps.writeMissing` escribe con `SetOptions.merge()` SOLO
     * `stock` + `updatedAt`. El doc resultante conserva el resto de campos, así que `stock` nunca
     * falta por culpa de ese merge. Cuando de verdad falta (doc legado sin el campo), el contrato
     * es conservar el local — nunca ponerlo a 0.
     */
    @Test
    fun `si el remoto omite stock se conserva el local y el resto de campos si se aplican`() = runTest {
        seedSyncedOnB(id = "p1", stock = 25, updatedAt = T1)
        pendingSalidaOnB(id = "mov-b4", productId = "p1", qty = 5)
        assertEquals(20, dao.getById("p1")!!.stock)

        remote.docs["p1"] = doc(id = "p1", stock = null, updatedAt = T2).copy(name = "Renombrado")
        sync.syncDownstream()

        val row = dao.getById("p1")!!
        assertEquals("un remoto sin `stock` NO puede poner el contador a 0", 20, row.stock)
        assertEquals("Renombrado", row.name)
        assertEquals(T2, row.updatedAt)
    }

    /**
     * El merge de `writeMissing` sobre un producto que YA existe remotamente deja el doc completo:
     * el downsync de B recibe nombre + stock y converge.
     */
    @Test
    fun `el merge de writeMissing deja el doc completo y B converge`() = runTest {
        seedSyncedOnB(id = "p1", stock = 0, updatedAt = T1)
        remote.docs["p1"] = doc(id = "p1", stock = 0, updatedAt = T1)

        remote.applyMovementIncrement("p1", delta = +40, at = T2)

        assertEquals("el merge conserva los campos descriptivos", "P-p1", remote.docs["p1"]!!.name)
        assertEquals(40, remote.docs["p1"]!!.stock)

        sync.syncDownstream()
        assertEquals(40, dao.getById("p1")!!.stock)
    }

    // ───────────────── (c) orden entre sumPendingDelta y marcar los movimientos subidos ─────────────────

    /**
     * Cuando el movimiento de B ya subió (el servidor aplicó su increment) y B lo marcó SYNCED,
     * `sumPendingDelta` vuelve a 0 y el downsync converge al valor del servidor SIN descontar dos
     * veces, por muchas veces que corra.
     */
    @Test
    fun `tras subir el movimiento el downsync converge sin doble descuento`() = runTest {
        seedSyncedOnB(id = "p1", stock = 0, updatedAt = T1)
        remote.docs["p1"] = doc(id = "p1", stock = 0, updatedAt = T1)
        pendingSalidaOnB(id = "mov-b5", productId = "p1", qty = 12)

        // A repone 40 y luego sube el movimiento de B: el servidor queda en 28.
        remote.applyMovementIncrement("p1", delta = +40, at = T2)
        remote.applyMovementIncrement("p1", delta = -12, at = T3)
        movementDao.markSynced(listOf("mov-b5"), at = T3)

        repeat(3) { sync.syncDownstream() }

        val row = dao.getById("p1")!!
        assertEquals(28, row.stock)
        assertEquals(T3, row.updatedAt)
        assertEquals(0, movementDao.sumPendingDelta("p1"))
    }

    /**
     * VENTANA CONOCIDA: `uploadAndMarkSynced` commitea la transacción REMOTA y solo después marca
     * los movimientos SYNCED en Room. Un downsync que caiga justo en medio cuenta el delta dos
     * veces (el servidor ya lo aplicó y `sumPendingDelta` todavía lo ve pendiente).
     *
     * Es TRANSITORIO y se cura solo: en cuanto los movimientos quedan SYNCED, el siguiente
     * downsync reescribe el contador con el valor del servidor. Este test fija ese contrato para
     * que la auto-curación no se rompa.
     */
    @Test
    fun `la ventana entre el commit remoto y markSynced es transitoria y se cura sola`() = runTest {
        seedSyncedOnB(id = "p1", stock = 40, updatedAt = T1)
        remote.docs["p1"] = doc(id = "p1", stock = 40, updatedAt = T1)
        pendingSalidaOnB(id = "mov-b6", productId = "p1", qty = 12)
        assertEquals(28, dao.getById("p1")!!.stock)

        // El servidor ya aplicó el increment del movimiento, pero Room aún no lo marcó SYNCED.
        remote.applyMovementIncrement("p1", delta = -12, at = T2)
        movementDao.markSyncing(listOf("mov-b6"))
        sync.syncDownstream()
        assertEquals("transitorio: 28 remoto + (-12) todavía contado como pendiente", 16, dao.getById("p1")!!.stock)

        // Al cerrarse la subida (markSynced) el siguiente downsync recupera el valor correcto.
        movementDao.markSynced(listOf("mov-b6"), at = T2)
        sync.syncDownstream()
        assertEquals(28, dao.getById("p1")!!.stock)
    }

    // ───────────────── helpers ─────────────────

    private suspend fun seedSyncedOnB(id: String, stock: Int, updatedAt: Long) {
        dao.insert(
            ProductEntity(
                id = id, name = "P-$id", description = null, category = null, price = 10.0,
                imageUrl = null, imageLocalUri = null, barcode = null,
                stock = stock, isActive = true, isDeleted = false,
                syncStatus = SyncStatus.SYNCED,
                createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
            )
        )
    }

    private suspend fun seedDirtyOnB(id: String, stock: Int, updatedAt: Long, status: SyncStatus) {
        dao.insert(
            ProductEntity(
                id = id, name = "P-$id", description = null, category = null, price = 10.0,
                imageUrl = null, imageLocalUri = null, barcode = null,
                stock = stock, isActive = true, isDeleted = false,
                syncStatus = status,
                createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
            )
        )
    }

    private fun domainProduct(id: String, stock: Int, updatedAt: Long) = Product(
        id = ProductId.of(id), name = "P-$id", description = null, category = null,
        price = Money.of(java.math.BigDecimal.valueOf(10.0)), imageUrl = null, imageLocalUri = null,
        barcode = null, stock = Quantity.of(stock), isActive = true, isDeleted = false,
        createdAt = T1, updatedAt = updatedAt,
    )

    /** Lo que hace `createPedido`: movimiento SALIDA pendiente + efecto inmediato en el contador. */
    private suspend fun pendingSalidaOnB(id: String, productId: String, qty: Int) {
        movementDao.insert(
            StockMovementEntity(
                id = id, productId = productId, productName = "P-$productId",
                type = "SALIDA", quantity = qty, reason = "PEDIDO", orderId = "ped-b",
                note = null, createdBy = "uid-b", createdByName = "B", createdAt = 1L,
                syncStatus = SyncStatus.PENDING_CREATE,
            )
        )
        dao.applyMovement(productId, -qty)
    }

    private fun doc(id: String, stock: Int?, updatedAt: Long) = RemoteProduct(
        id = id, name = "P-$id", description = null, category = null, price = 10.0,
        imageUrl = null, barcode = null, stock = stock,
        isActive = true, isDeleted = false, createdRemoteAt = T1, updatedRemoteAt = updatedAt,
    )

    private fun lastUpdatedCount(): Int = lastCounter("Updated=")

    private fun lastStaleCount(): Int = lastCounter("StaleIgnored=")

    private fun lastCounter(key: String): Int {
        val line = ShadowLog.getLogsForTag("SYNC_PRODUCT")
            .lastOrNull { it.msg.startsWith("syncDownstream: Batch committed.") }
            ?: return 0
        assertTrue("línea de log sin $key: ${line.msg}", line.msg.contains(key))
        return line.msg.substringAfter(key).takeWhile { it.isDigit() }.toInt()
    }

    /**
     * Imita Firestore para el catálogo: los documentos solo cambian de `stock` por
     * `FieldValue.increment` (libro de movimientos) y su `updatedAt` lo pone el servidor.
     * La query del downsync replica `whereGreaterThanOrEqualTo("updatedAt", watermark)` con el
     * orden compuesto (updatedAt, id).
     */
    private class FakeFirestore : ProductRemoteDataSource {
        val docs = linkedMapOf<String, RemoteProduct>()

        /** `updatedAt: FieldValue.serverTimestamp()` — el servidor sella cada subida. */
        var assignServerTimestampOnUpload: Long? = null

        /** `StockMovementFirestoreOps.writeMissing`: set + merge de SOLO `stock` y `updatedAt`. */
        fun applyMovementIncrement(productId: String, delta: Int, at: Long) {
            val current = docs[productId]
            docs[productId] = current
                ?.copy(stock = (current.stock ?: 0) + delta, updatedRemoteAt = at)
                ?: RemoteProduct(
                    // Doc creado por el merge: SOLO stock y updatedAt, sin campos descriptivos.
                    id = productId, name = "", description = null, category = null, price = null,
                    imageUrl = null, barcode = null, stock = delta,
                    isActive = null, isDeleted = null, createdRemoteAt = null, updatedRemoteAt = at,
                )
        }

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val page = docs.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (page.isNotEmpty()) emit(page)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            val existing = docs[product.id]
            docs[product.id] = product.copy(
                // set + merge SIN stock: el servidor conserva su contador.
                stock = existing?.stock ?: product.stock,
                updatedRemoteAt = assignServerTimestampOnUpload ?: product.updatedRemoteAt,
            )
        }

        override suspend fun fetchProductById(id: String): RemoteProduct? = docs[id]

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            docs[id]?.let { docs[id] = it.copy(isDeleted = true, updatedRemoteAt = timestamp) }
        }
    }

    private companion object {
        const val T1 = 1_757_000_000_000L
        const val T2 = 1_757_000_060_000L
        const val T3 = 1_757_000_120_000L
    }
}
