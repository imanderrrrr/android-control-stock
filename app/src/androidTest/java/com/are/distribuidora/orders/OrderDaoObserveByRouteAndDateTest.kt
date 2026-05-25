package com.are.distribuidora.orders

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.orders.data.local.dao.OrderDao
import com.are.distribuidora.orders.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Test DAO — observeByRouteAndDate
 *
 * Verifica que insertar 2 orders de la misma ruta pero con distintas fechas
 * hace que observeByRouteAndDate devuelva solo los de la fecha consultada.
 */
class OrderDaoObserveByRouteAndDateTest {

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: OrderDao
    private val executor = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .allowMainThreadQueries()
            .build()
        dao = db.orderDao()
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdown()
    }

    @Test
    fun observeByRouteAndDate_returnsOnlyOrdersMatchingDate() = runBlocking {
        val routeId = "ROUTE-TEST"
        val dateA   = "2026-02-24"
        val dateB   = "2026-02-25"
        val now     = System.currentTimeMillis()

        // Insertar order en dateA
        dao.upsert(buildOrder(orderId = "ORDER-A", routeId = routeId, deliveryDate = dateA, now = now))
        // Insertar order en dateB
        dao.upsert(buildOrder(orderId = "ORDER-B", routeId = routeId, deliveryDate = dateB, now = now))
        // Insertar order eliminado en dateA (no debe aparecer)
        dao.upsert(buildOrder(orderId = "ORDER-DEL", routeId = routeId, deliveryDate = dateA, now = now, isDeleted = true))

        // Consultar dateA
        val resultA = dao.observeByRouteAndDate(routeId = routeId, deliveryDate = dateA).first()
        assertEquals("Debe haber solo 1 order activo para $dateA", 1, resultA.size)
        assertEquals("ORDER-A", resultA[0].orderId)

        // Consultar dateB
        val resultB = dao.observeByRouteAndDate(routeId = routeId, deliveryDate = dateB).first()
        assertEquals("Debe haber solo 1 order activo para $dateB", 1, resultB.size)
        assertEquals("ORDER-B", resultB[0].orderId)

        // Consultar fecha inexistente
        val resultC = dao.observeByRouteAndDate(routeId = routeId, deliveryDate = "2026-01-01").first()
        assertEquals("No debe haber orders para una fecha inexistente", 0, resultC.size)
    }

    @Test
    fun observeByRouteAndDate_differentRoutesSameDate_returnsOnlyMatchingRoute() = runBlocking {
        val dateA  = "2026-02-24"
        val now    = System.currentTimeMillis()

        dao.upsert(buildOrder(orderId = "ORDER-R1", routeId = "ROUTE-1", deliveryDate = dateA, now = now))
        dao.upsert(buildOrder(orderId = "ORDER-R2", routeId = "ROUTE-2", deliveryDate = dateA, now = now))

        val resultR1 = dao.observeByRouteAndDate(routeId = "ROUTE-1", deliveryDate = dateA).first()
        assertEquals(1, resultR1.size)
        assertEquals("ORDER-R1", resultR1[0].orderId)

        val resultR2 = dao.observeByRouteAndDate(routeId = "ROUTE-2", deliveryDate = dateA).first()
        assertEquals(1, resultR2.size)
        assertEquals("ORDER-R2", resultR2[0].orderId)
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private fun buildOrder(
        orderId: String,
        routeId: String,
        deliveryDate: String,
        now: Long,
        isDeleted: Boolean = false,
    ) = OrderEntity(
        orderId              = orderId,
        routeId              = routeId,
        deliveryDate         = deliveryDate,
        clientName           = "Cliente Test",
        clientAddress        = null,
        sellerName           = "Vendedor Test",
        itemsCount           = 2,
        itemsDownloaded      = 0,
        totalAmount          = null,
        downloadStatus       = "ITEMS_PENDING",
        failedReasonCode     = null,
        failedReasonMessage  = null,
        failedAttempts       = 0,
        lastAttemptAt        = null,
        createdAt            = now,
        updatedAt            = now,
        vendedorId           = "uid-other",
        isDeleted            = isDeleted,
    )
}

