package com.are.distribuidora.data.repository

import com.are.distribuidora.data.local.dao.PedidoDraftDao
import com.are.distribuidora.data.local.entity.PedidoDraftEntity
import com.are.distribuidora.data.local.entity.PedidoDraftItemEntity
import com.are.distribuidora.data.local.entity.PedidoDraftWithItemsEntity
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.model.PedidoDraftItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardado, lectura y borrado del borrador, incluido el filtrado por vendedorId.
 *
 * Usa un DAO en memoria (no un mock) porque lo que importa aquí es el
 * comportamiento de ida y vuelta: que lo guardado se lea idéntico y que un
 * vendedor no vea el borrador de otro.
 */
class PedidoDraftRepositoryImplTest {

    private val dao = InMemoryPedidoDraftDao()
    private val repository = PedidoDraftRepositoryImpl(dao)

    @Test
    fun `guardar y leer devuelve el borrador identico`() = runTest {
        val draft = draft(vendedorId = "vend-1")

        repository.save(draft)
        val loaded = repository.get("vend-1")

        assertNotNull(loaded)
        assertEquals(draft.routeId, loaded!!.routeId)
        assertEquals(draft.deliveryDate, loaded.deliveryDate)
        assertEquals(draft.ivaEnabled, loaded.ivaEnabled)
        assertEquals(draft.clienteNombre, loaded.clienteNombre)
        assertEquals(ClienteSelection.Existente("cli-1"), loaded.cliente)
        assertEquals(2, loaded.items.size)
        val coca = loaded.items.first { it.productoId == "p1" }
        assertEquals("Coca 600", coca.nombre)
        assertEquals(10.0, coca.precioUnitario, 0.001)
        assertEquals(3, coca.cantidad)
        assertEquals(2.5, coca.descuentoAmount, 0.001)
        assertEquals(DiscountType.AMOUNT, coca.descuentoType)
        assertEquals("sin hielo", coca.notes)
    }

    @Test
    fun `guardar dos veces reemplaza el borrador anterior`() = runTest {
        // Invariante del esquema: un solo borrador por vendedor.
        repository.save(draft(vendedorId = "vend-1"))
        repository.save(
            draft(vendedorId = "vend-1", routeId = "ruta-2").copy(
                items = listOf(item("p9", "Otro", 1.0, 1))
            )
        )

        val loaded = repository.get("vend-1")!!
        assertEquals("ruta-2", loaded.routeId)
        assertEquals(listOf("p9"), loaded.items.map { it.productoId })
    }

    @Test
    fun `un vendedor nunca ve el borrador de otro`() = runTest {
        repository.save(draft(vendedorId = "vend-1"))

        assertNull(repository.get("vend-2"))
        assertNotNull(repository.get("vend-1"))
    }

    @Test
    fun `borrar elimina cabecera e items`() = runTest {
        repository.save(draft(vendedorId = "vend-1"))

        repository.delete("vend-1")

        assertNull(repository.get("vend-1"))
        assertTrue(dao.items.isEmpty())
    }

    @Test
    fun `borrar el borrador de un vendedor no toca el de otro`() = runTest {
        repository.save(draft(vendedorId = "vend-1"))
        repository.save(draft(vendedorId = "vend-2"))

        repository.delete("vend-1")

        assertNull(repository.get("vend-1"))
        assertEquals(2, repository.get("vend-2")!!.items.size)
    }

    @Test
    fun `borrar un borrador inexistente no falla`() = runTest {
        repository.delete("nadie")
        assertNull(repository.get("nadie"))
    }

    @Test
    fun `cliente temporal se guarda y se reconstruye con su snapshot`() = runTest {
        val temporal = draft(vendedorId = "vend-1").copy(
            cliente = ClienteSelection.Temporal(
                ClienteSnapshot(nombre = "Tienda Nueva", telefono = "5555", direccion = "Zona 1")
            ),
            clienteNombre = "Tienda Nueva",
        )

        repository.save(temporal)
        val loaded = repository.get("vend-1")!!

        val cliente = loaded.cliente as ClienteSelection.Temporal
        assertEquals("Tienda Nueva", cliente.snapshot.nombre)
        assertEquals("5555", cliente.snapshot.telefono)
        assertEquals("Zona 1", cliente.snapshot.direccion)
    }

    @Test
    fun `borrador de cliente existente sin clienteId no se ofrece`() = runTest {
        // Fila corrupta: mejor no ofrecer nada que ofrecer un pedido sin cliente.
        dao.upsertDraft(
            PedidoDraftEntity(
                vendedorId = "vend-1",
                routeId = "ruta-1",
                clienteId = null,
                clienteNombre = "Tienda Ana",
                clienteTelefono = null,
                clienteDireccion = null,
                clienteEsTemporal = false,
                deliveryDate = "2026-09-13",
                ivaEnabled = false,
                updatedAt = 1L,
            )
        )

        assertNull(repository.get("vend-1"))
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun draft(vendedorId: String, routeId: String = "ruta-1") = PedidoDraft(
        vendedorId = vendedorId,
        routeId = routeId,
        cliente = ClienteSelection.Existente("cli-1"),
        clienteNombre = "Tienda Ana",
        deliveryDate = "2026-09-13",
        ivaEnabled = true,
        updatedAt = 1_789_000_000_000L,
        items = listOf(
            item("p1", "Coca 600", 10.0, 3, 2.5, DiscountType.AMOUNT, "sin hielo"),
            item("p2", "Sazón", 5.0, 1),
        ),
    )

    private fun item(
        id: String,
        nombre: String,
        precio: Double,
        cantidad: Int,
        descuentoAmount: Double = 0.0,
        descuentoType: DiscountType = DiscountType.PERCENTAGE,
        notes: String? = null,
    ) = PedidoDraftItem(
        productoId = id,
        nombre = nombre,
        precioUnitario = precio,
        cantidad = cantidad,
        descuentoAmount = descuentoAmount,
        descuentoPercent = 0.0,
        descuentoType = descuentoType,
        notes = notes,
        category = null,
        imageUrl = null,
        barcode = null,
    )
}

/**
 * DAO en memoria que imita la semántica de Room que importa aquí:
 * REPLACE por clave primaria y borrado por vendedorId.
 */
private class InMemoryPedidoDraftDao : PedidoDraftDao {

    private val drafts = mutableMapOf<String, PedidoDraftEntity>()
    val items = mutableListOf<PedidoDraftItemEntity>()

    override suspend fun upsertDraft(draft: PedidoDraftEntity) {
        drafts[draft.vendedorId] = draft
    }

    override suspend fun insertItems(items: List<PedidoDraftItemEntity>) {
        items.forEach { new ->
            this.items.removeAll { it.vendedorId == new.vendedorId && it.productoId == new.productoId }
            this.items += new
        }
    }

    override suspend fun deleteItems(vendedorId: String) {
        items.removeAll { it.vendedorId == vendedorId }
    }

    override suspend fun deleteDraft(vendedorId: String) {
        drafts.remove(vendedorId)
    }

    override suspend fun getDraft(vendedorId: String): PedidoDraftWithItemsEntity? {
        val header = drafts[vendedorId] ?: return null
        return PedidoDraftWithItemsEntity(
            draft = header,
            items = items.filter { it.vendedorId == vendedorId },
        )
    }

    override suspend fun countFor(vendedorId: String): Int = if (drafts.containsKey(vendedorId)) 1 else 0
}
