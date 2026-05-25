package com.are.distribuidora.orders

import com.are.distribuidora.orders.data.local.entity.OrderEntity
import com.are.distribuidora.orders.data.mapper.toDomain
import com.are.distribuidora.orders.domain.model.OrderDownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios del mapper OrderEntity → Order (cabecera).
 *
 * Verifica que:
 * - Todos los campos se mapean correctamente.
 * - downloadStatus se convierte a enum correctamente.
 * - Valores desconocidos de downloadStatus se convierten a NONE.
 */
class OrderHeaderMapperTest {

    private fun buildEntity(
        orderId: String = "ORDER-1",
        routeId: String = "ROUTE-A",
        deliveryDate: String = "2026-01-17",
        clientName: String = "Cliente Test",
        clientAddress: String? = "Calle 1",
        sellerName: String? = "Vendedor A",
        itemsCount: Int = 5,
        itemsDownloaded: Int = 0,
        totalAmount: Double? = null,
        downloadStatus: String = "ITEMS_PENDING",
        failedReasonCode: String? = null,
        failedReasonMessage: String? = null,
        failedAttempts: Int = 0,
        lastAttemptAt: Long? = null,
        createdAt: Long = 1000L,
        updatedAt: Long = 2000L,
    ) = OrderEntity(
        orderId = orderId,
        routeId = routeId,
        deliveryDate = deliveryDate,
        clientName = clientName,
        clientAddress = clientAddress,
        sellerName = sellerName,
        itemsCount = itemsCount,
        itemsDownloaded = itemsDownloaded,
        totalAmount = totalAmount,
        downloadStatus = downloadStatus,
        failedReasonCode = failedReasonCode,
        failedReasonMessage = failedReasonMessage,
        failedAttempts = failedAttempts,
        lastAttemptAt = lastAttemptAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `toDomain maps all basic fields correctly`() {
        val entity = buildEntity()
        val domain = entity.toDomain()

        assertEquals("ORDER-1", domain.orderId)
        assertEquals("ROUTE-A", domain.routeId)
        assertEquals("2026-01-17", domain.deliveryDate)
        assertEquals("Cliente Test", domain.clientName)
        assertEquals("Calle 1", domain.clientAddress)
        assertEquals("Vendedor A", domain.sellerName)
        assertEquals(5, domain.itemsCount)
        assertEquals(0, domain.itemsDownloaded)
        assertNull(domain.totalAmount)
        assertEquals(OrderDownloadStatus.ITEMS_PENDING, domain.downloadStatus)
        assertNull(domain.failedReasonCode)
        assertNull(domain.failedReasonMessage)
        assertEquals(0, domain.failedAttempts)
        assertNull(domain.lastAttemptAt)
        assertEquals(1000L, domain.createdAt)
        assertEquals(2000L, domain.updatedAt)
    }

    @Test
    fun `toDomain maps COMPLETED status`() {
        val entity = buildEntity(
            downloadStatus = "COMPLETED",
            itemsDownloaded = 5,
            totalAmount = 250.0,
        )
        val domain = entity.toDomain()
        assertEquals(OrderDownloadStatus.COMPLETED, domain.downloadStatus)
        assertEquals(5, domain.itemsDownloaded)
        assertEquals(250.0, domain.totalAmount)
    }

    @Test
    fun `toDomain maps FAILED status with reason`() {
        val entity = buildEntity(
            downloadStatus = "FAILED",
            failedReasonCode = "COUNT_MISMATCH",
            failedReasonMessage = "staging=3 expected=5",
            failedAttempts = 2,
            lastAttemptAt = 9999L,
        )
        val domain = entity.toDomain()
        assertEquals(OrderDownloadStatus.FAILED, domain.downloadStatus)
        assertEquals("COUNT_MISMATCH", domain.failedReasonCode)
        assertEquals("staging=3 expected=5", domain.failedReasonMessage)
        assertEquals(2, domain.failedAttempts)
        assertEquals(9999L, domain.lastAttemptAt)
    }

    @Test
    fun `toDomain maps unknown status to NONE`() {
        val entity = buildEntity(downloadStatus = "UNKNOWN_STATUS_XYZ")
        val domain = entity.toDomain()
        assertEquals(OrderDownloadStatus.NONE, domain.downloadStatus)
    }

    @Test
    fun `toDomain handles null optional fields`() {
        val entity = buildEntity(
            clientAddress = null,
            sellerName = null,
            totalAmount = null,
            failedReasonCode = null,
            failedReasonMessage = null,
            lastAttemptAt = null,
        )
        val domain = entity.toDomain()
        assertNull(domain.clientAddress)
        assertNull(domain.sellerName)
        assertNull(domain.totalAmount)
        assertNull(domain.failedReasonCode)
        assertNull(domain.failedReasonMessage)
        assertNull(domain.lastAttemptAt)
    }

    @Test
    fun `toDomain maps IN_PROGRESS status`() {
        val entity = buildEntity(downloadStatus = "IN_PROGRESS", failedAttempts = 1)
        val domain = entity.toDomain()
        assertEquals(OrderDownloadStatus.IN_PROGRESS, domain.downloadStatus)
        assertEquals(1, domain.failedAttempts)
    }

    @Test
    fun `toDomain isDeleted defaults to false`() {
        // OrderEntity.isDeleted tiene default false → el dominio debe recibirlo como false
        val entity = buildEntity()
        val domain = entity.toDomain()
        assertFalse(domain.isDeleted)
    }

    @Test
    fun `toDomain propagates isDeleted true`() {
        val entity = buildEntity().copy(isDeleted = true)
        val domain = entity.toDomain()
        assertTrue(domain.isDeleted)
    }
}

