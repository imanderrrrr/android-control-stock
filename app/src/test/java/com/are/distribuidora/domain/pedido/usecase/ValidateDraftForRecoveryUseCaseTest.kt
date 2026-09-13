package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.model.PedidoDraftItem
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.Quantity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Revalidación del borrador antes de ofrecer "retomar el pedido".
 *
 * Todo lo que se prueba aquí es determinista: no hay Android, ni Room, ni reloj real
 * (el `now` se inyecta).
 */
class ValidateDraftForRecoveryUseCaseTest {

    private val hoy = "2026-09-13"
    private val ahora = 1_789_000_000_000L

    // ─── Casos que SÍ se pueden retomar ──────────────────────────────────────

    @Test
    fun `borrador intacto se ofrece sin avisos`() = runTest {
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val outcome = useCase(draft(items = listOf(item("p1", "Coca 600", 10.0, 3))), hoy, ahora)

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertTrue(restore.notices.isEmpty())
        assertEquals(1, restore.draft.items.size)
        assertEquals(3, restore.draft.items[0].cantidad)
    }

    @Test
    fun `producto borrado del catalogo se quita y se avisa`() = runTest {
        // "p2" no existe en el repositorio → producto borrado.
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val outcome = useCase(
            draft(items = listOf(item("p1", "Coca 600", 10.0, 1), item("p2", "Sazón", 5.0, 2))),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(listOf("p1"), restore.draft.items.map { it.productoId })
        val removed = restore.notices
            .filterIsInstance<ValidateDraftForRecoveryUseCase.Notice.ItemsRemoved>()
            .single()
        assertEquals(listOf("Sazón"), removed.names)
    }

    @Test
    fun `producto inactivo se quita y se avisa`() = runTest {
        val useCase = build(
            products = listOf(
                product("p1", "Coca 600", 10.0),
                product("p2", "Sazón", 5.0, isActive = false),
            )
        )

        val outcome = useCase(
            draft(items = listOf(item("p1", "Coca 600", 10.0, 1), item("p2", "Sazón", 5.0, 2))),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(listOf("p1"), restore.draft.items.map { it.productoId })
        assertTrue(
            restore.notices.any { it is ValidateDraftForRecoveryUseCase.Notice.ItemsRemoved }
        )
    }

    @Test
    fun `precio cambiado adopta el precio vigente y avisa`() = runTest {
        // El borrador lo guardó a 10.0; el catálogo ya va por 12.5.
        val useCase = build(products = listOf(product("p1", "Coca 600", 12.5)))

        val outcome = useCase(draft(items = listOf(item("p1", "Coca 600", 10.0, 2))), hoy, ahora)

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(12.5, restore.draft.items[0].precioUnitario, 0.001)
        val change = restore.notices
            .filterIsInstance<ValidateDraftForRecoveryUseCase.Notice.PricesChanged>()
            .single().changes.single()
        assertEquals("Coca 600", change.name)
        assertEquals(10.0, change.oldPrice, 0.001)
        assertEquals(12.5, change.newPrice, 0.001)
    }

    @Test
    fun `descuento por porcentaje se recalcula sobre el precio nuevo`() = runTest {
        val useCase = build(products = listOf(product("p1", "Coca 600", 20.0)))

        // 10% sobre 10.0 x 2 = 2.0 guardado; con precio 20.0 x 2 debe pasar a 4.0.
        val outcome = useCase(
            draft(
                items = listOf(
                    item("p1", "Coca 600", 10.0, 2, descuentoAmount = 2.0, descuentoPercent = 10.0)
                )
            ),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(4.0, restore.draft.items[0].descuentoAmount, 0.001)
    }

    @Test
    fun `descuento por monto fijo se respeta y se acota al subtotal nuevo`() = runTest {
        // Precio baja de 10.0 a 3.0: el descuento fijo de 8.0 excedería el subtotal (6.0).
        val useCase = build(products = listOf(product("p1", "Coca 600", 3.0)))

        val outcome = useCase(
            draft(
                items = listOf(
                    item(
                        "p1", "Coca 600", 10.0, 2,
                        descuentoAmount = 8.0,
                        descuentoPercent = 0.0,
                        descuentoType = DiscountType.AMOUNT,
                    )
                )
            ),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(6.0, restore.draft.items[0].descuentoAmount, 0.001)
    }

    @Test
    fun `item personalizado sobrevive sin consultar el catalogo`() = runTest {
        // Un "custom_" no existe en el catálogo; no debe tratarse como producto borrado.
        val useCase = build(products = emptyList())

        val outcome = useCase(
            draft(items = listOf(item("custom_abc", "Flete", 25.0, 1))),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(listOf("custom_abc"), restore.draft.items.map { it.productoId })
        assertTrue(restore.notices.isEmpty())
    }

    // ─── Fecha de entrega ────────────────────────────────────────────────────

    @Test
    fun `fecha de entrega vencida se cambia a hoy y se avisa`() = runTest {
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val outcome = useCase(
            draft(items = listOf(item("p1", "Coca 600", 10.0, 1)), deliveryDate = "2026-09-10"),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(hoy, restore.draft.deliveryDate)
        val notice = restore.notices
            .filterIsInstance<ValidateDraftForRecoveryUseCase.Notice.DeliveryDateReset>()
            .single()
        assertEquals("2026-09-10", notice.previousDate)
        assertEquals(hoy, notice.newDate)
    }

    @Test
    fun `fecha de entrega de hoy no genera aviso`() = runTest {
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val outcome = useCase(
            draft(items = listOf(item("p1", "Coca 600", 10.0, 1)), deliveryDate = hoy),
            hoy, ahora,
        )

        val restore = outcome as ValidateDraftForRecoveryUseCase.Outcome.Restore
        assertEquals(hoy, restore.draft.deliveryDate)
        assertTrue(restore.notices.isEmpty())
    }

    // ─── Casos que se DESCARTAN ──────────────────────────────────────────────

    @Test
    fun `borrador mas viejo que el maximo se descarta`() = runTest {
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val viejo = draft(
            items = listOf(item("p1", "Coca 600", 10.0, 1)),
            updatedAt = ahora - ValidateDraftForRecoveryUseCase.MAX_AGE_MS - 1,
        )
        val outcome = useCase(viejo, hoy, ahora)

        assertEquals(
            ValidateDraftForRecoveryUseCase.DiscardReason.TOO_OLD,
            (outcome as ValidateDraftForRecoveryUseCase.Outcome.Discard).reason,
        )
    }

    @Test
    fun `borrador de hace dos dias todavia se ofrece`() = runTest {
        // Decisión documentada: el umbral es 7 días, no 24 h; a las 48 h el pedido
        // sigue valiendo y lo único que se corrige es la fecha de entrega.
        val useCase = build(products = listOf(product("p1", "Coca 600", 10.0)))

        val outcome = useCase(
            draft(
                items = listOf(item("p1", "Coca 600", 10.0, 1)),
                updatedAt = ahora - 2 * 24 * 60 * 60 * 1000L,
            ),
            hoy, ahora,
        )

        assertTrue(outcome is ValidateDraftForRecoveryUseCase.Outcome.Restore)
    }

    @Test
    fun `si ya existe pedido activo del cliente se descarta`() = runTest {
        val useCase = build(
            products = listOf(product("p1", "Coca 600", 10.0)),
            existingPedidoId = "pedido-ya-creado",
        )

        val outcome = useCase(draft(items = listOf(item("p1", "Coca 600", 10.0, 1))), hoy, ahora)

        assertEquals(
            ValidateDraftForRecoveryUseCase.DiscardReason.ORDER_ALREADY_EXISTS,
            (outcome as ValidateDraftForRecoveryUseCase.Outcome.Discard).reason,
        )
    }

    @Test
    fun `cliente temporal no consulta duplicados`() = runTest {
        // Un cliente temporal no tiene id estable: la regla anti duplicados no aplica
        // y el borrador debe poder retomarse aunque el repo devolviera algo.
        val useCase = build(
            products = listOf(product("p1", "Coca 600", 10.0)),
            existingPedidoId = "no-deberia-consultarse",
        )

        val outcome = useCase(
            draft(
                items = listOf(item("p1", "Coca 600", 10.0, 1)),
                cliente = ClienteSelection.Temporal(
                    com.are.distribuidora.domain.pedido.model.ClienteSnapshot("Tienda Ana", null, null)
                ),
            ),
            hoy, ahora,
        )

        assertTrue(outcome is ValidateDraftForRecoveryUseCase.Outcome.Restore)
    }

    @Test
    fun `si no sobrevive ningun item se descarta`() = runTest {
        val useCase = build(products = emptyList())

        val outcome = useCase(draft(items = listOf(item("p1", "Coca 600", 10.0, 1))), hoy, ahora)

        assertEquals(
            ValidateDraftForRecoveryUseCase.DiscardReason.EMPTY_AFTER_VALIDATION,
            (outcome as ValidateDraftForRecoveryUseCase.Outcome.Discard).reason,
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Se usan mocks relajados en vez de fakes a mano: de estas interfaces (que tienen
     * decenas de métodos) el caso de uso solo toca dos, y escribir el resto solo
     * añadiría ruido que hay que mantener cada vez que cambie la interfaz.
     */
    private fun build(
        products: List<Product>,
        existingPedidoId: String? = null,
    ): ValidateDraftForRecoveryUseCase {
        val productRepository = mockk<ProductRepository>(relaxed = true)
        coEvery { productRepository.getById(any()) } answers {
            // ProductId es un `value class`: en runtime el argumento llega ya
            // desenvuelto como String, no como ProductId.
            val id = firstArg<String>()
            products.firstOrNull { it.id.value == id }
        }
        val pedidoRepository = mockk<PedidoRepository>(relaxed = true)
        coEvery {
            pedidoRepository.findActivePedidoByClienteAndDay(any(), any())
        } returns existingPedidoId

        return ValidateDraftForRecoveryUseCase(productRepository, pedidoRepository)
    }

    private fun draft(
        items: List<PedidoDraftItem>,
        deliveryDate: String = hoy,
        updatedAt: Long = ahora,
        cliente: ClienteSelection = ClienteSelection.Existente("cli-1"),
    ) = PedidoDraft(
        vendedorId = "vend-1",
        routeId = "ruta-1",
        cliente = cliente,
        clienteNombre = "Tienda Ana",
        deliveryDate = deliveryDate,
        ivaEnabled = false,
        items = items,
        updatedAt = updatedAt,
    )

    private fun item(
        id: String,
        nombre: String,
        precio: Double,
        cantidad: Int,
        descuentoAmount: Double = 0.0,
        descuentoPercent: Double = 0.0,
        descuentoType: DiscountType = DiscountType.PERCENTAGE,
    ) = PedidoDraftItem(
        productoId = id,
        nombre = nombre,
        precioUnitario = precio,
        cantidad = cantidad,
        descuentoAmount = descuentoAmount,
        descuentoPercent = descuentoPercent,
        descuentoType = descuentoType,
        notes = null,
        category = null,
        imageUrl = null,
        barcode = null,
    )

    private fun product(
        id: String,
        nombre: String,
        precio: Double,
        isActive: Boolean = true,
        isDeleted: Boolean = false,
    ) = Product(
        id = ProductId.of(id),
        name = nombre,
        price = Money.of(BigDecimal.valueOf(precio)),
        stock = Quantity.of(10),
        isActive = isActive,
        isDeleted = isDeleted,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
