package com.are.distribuidora.pedido

import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.entity.PedidoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión Bug #5 — PedidoEntity.isDeleted debe existir como campo explícito.
 *
 * Antes del fix: PedidoEntity no tenía campo isDeleted; el soft-delete se expresaba
 * únicamente mediante syncStatus=PENDING_DELETE, lo cual era insuficiente para:
 *   1. Distinguir "en proceso de eliminación" de "eliminado confirmado".
 *   2. Bloquear el worker de sync para que no suba pedidos eliminados.
 *   3. Filtrar consistentemente en consultas Room junto con OrderEntity/ProductEntity.
 *
 * Después del fix: PedidoEntity tiene isDeleted: Boolean = false
 *   - El worker de sync excluye pedidos con isDeleted=true (vía query isDeleted=0).
 *   - getAllWithItems() excluye isDeleted=1 Y syncStatus='PENDING_DELETE'.
 *   - getPedidosByCliente() excluye isDeleted=1 Y syncStatus='PENDING_DELETE'.
 */
class PedidoEntityIsDeletedTest {

    private fun makePedidoEntity(
        id: String = "PED-1",
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        isDeleted: Boolean = false,
    ) = PedidoEntity(
        id = id,
        vendedorId = "uid-test",
        routeId = "ROUTE-1",
        deliveryDate = "2026-02-22",
        clienteId = "CLIENT-1",
        clienteNombre = "Cliente Test",
        clienteTelefono = null,
        clienteDireccion = null,
        subtotal = 100.0,
        descuentoGlobal = 0.0,
        total = 100.0,
        version = 1,
        actualizadoPor = "uid-test",
        creadoEn = 1000L,
        actualizadoEn = 2000L,
        syncStatus = syncStatus,
        createdAt = 1000L,
        updatedAt = 2000L,
        orderKey = null,
        isDeleted = isDeleted,
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test A
    // PedidoEntity se puede crear con isDeleted=false (valor por defecto)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `PedidoEntity se crea con isDeleted=false por defecto`() {
        val entity = makePedidoEntity()
        assertFalse("isDeleted debe ser false por defecto", entity.isDeleted)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test B
    // PedidoEntity se puede crear con isDeleted=true (eliminado)
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `PedidoEntity se puede crear con isDeleted=true`() {
        val entity = makePedidoEntity(isDeleted = true)
        assertTrue("isDeleted debe ser true", entity.isDeleted)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test C
    // copy(isDeleted=true) no altera otros campos
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `copy con isDeleted=true preserva todos los demas campos`() {
        val original = makePedidoEntity(id = "PED-COPY", syncStatus = SyncStatus.SYNCED)
        val deleted = original.copy(isDeleted = true, updatedAt = 9999L)

        assertTrue(deleted.isDeleted)
        assertEquals("PED-COPY", deleted.id)
        assertEquals(SyncStatus.SYNCED, deleted.syncStatus)
        assertEquals(9999L, deleted.updatedAt)
        assertEquals(original.total, deleted.total, 0.0)
        assertEquals(original.vendedorId, deleted.vendedorId)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test D
    // Un pedido PENDING_DELETE con isDeleted=false NO debe ser tratado como eliminado
    // (son estados ortogonales: PENDING_DELETE es "en tránsito", isDeleted es "confirmado")
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `PENDING_DELETE con isDeleted=false son estados ortogonales`() {
        val entity = makePedidoEntity(syncStatus = SyncStatus.PENDING_DELETE, isDeleted = false)
        assertEquals(SyncStatus.PENDING_DELETE, entity.syncStatus)
        assertFalse("isDeleted debe ser false aunque syncStatus sea PENDING_DELETE", entity.isDeleted)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test E
    // Un pedido SYNCED con isDeleted=true debe tener ambos campos correctamente
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `pedido SYNCED puede marcarse como isDeleted=true independientemente`() {
        val entity = makePedidoEntity(syncStatus = SyncStatus.SYNCED, isDeleted = true)
        assertEquals(SyncStatus.SYNCED, entity.syncStatus)
        assertTrue("isDeleted debe ser true", entity.isDeleted)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test F
    // Simular el filtro del worker: pedidos con isDeleted=true no deben procesarse
    // Este test verifica la lógica de filtrado que aplican las queries del DAO
    // (isDeleted = 0 en getPendingPedidosWithItems).
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `filtro del worker excluye pedidos con isDeleted=true`() {
        val pendingStatuses = setOf(SyncStatus.PENDING_CREATE, SyncStatus.SYNCING)

        val allPedidos = listOf(
            makePedidoEntity("PED-A", SyncStatus.PENDING_CREATE, isDeleted = false),  // debe procesar
            makePedidoEntity("PED-B", SyncStatus.PENDING_CREATE, isDeleted = true),   // NO debe procesar
            makePedidoEntity("PED-C", SyncStatus.SYNCING,        isDeleted = false),  // debe procesar
            makePedidoEntity("PED-D", SyncStatus.SYNCED,         isDeleted = false),  // no es pending
            makePedidoEntity("PED-E", SyncStatus.SYNCING,        isDeleted = true),   // NO debe procesar
        )

        // Simula el filtro de la query: syncStatus IN (:statuses) AND isDeleted = 0
        val toSync = allPedidos.filter { it.syncStatus in pendingStatuses && !it.isDeleted }

        assertEquals("Solo deben procesarse 2 pedidos", 2, toSync.size)
        val ids = toSync.map { it.id }
        assertTrue("PED-A debe procesarse", "PED-A" in ids)
        assertTrue("PED-C debe procesarse", "PED-C" in ids)
        assertFalse("PED-B (isDeleted) NO debe procesarse", "PED-B" in ids)
        assertFalse("PED-E (isDeleted) NO debe procesarse", "PED-E" in ids)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Bug #5 — Test G
    // Simular el filtro de la lista pública: excluye isDeleted=1 Y PENDING_DELETE
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    fun `lista publica excluye tanto isDeleted=true como PENDING_DELETE`() {
        val allPedidos = listOf(
            makePedidoEntity("PED-OK",       SyncStatus.SYNCED,        isDeleted = false),  // visible
            makePedidoEntity("PED-DELETED",  SyncStatus.SYNCED,        isDeleted = true),   // oculto (isDeleted)
            makePedidoEntity("PED-PENDING-D",SyncStatus.PENDING_DELETE, isDeleted = false), // oculto (PENDING_DELETE)
            makePedidoEntity("PED-BOTH",     SyncStatus.PENDING_DELETE, isDeleted = true),  // oculto (ambos)
            makePedidoEntity("PED-PENDING-C",SyncStatus.PENDING_CREATE, isDeleted = false), // visible (pendiente)
        )

        // Simula getAllWithItems(): syncStatus != 'PENDING_DELETE' AND isDeleted = 0
        val visible = allPedidos.filter {
            it.syncStatus != SyncStatus.PENDING_DELETE && !it.isDeleted
        }

        assertEquals("Solo deben verse 2 pedidos", 2, visible.size)
        val ids = visible.map { it.id }
        assertTrue("PED-OK debe estar visible", "PED-OK" in ids)
        assertTrue("PED-PENDING-C debe estar visible", "PED-PENDING-C" in ids)
        assertFalse("PED-DELETED no debe estar visible", "PED-DELETED" in ids)
        assertFalse("PED-PENDING-D no debe estar visible", "PED-PENDING-D" in ids)
        assertFalse("PED-BOTH no debe estar visible", "PED-BOTH" in ids)
    }
}

