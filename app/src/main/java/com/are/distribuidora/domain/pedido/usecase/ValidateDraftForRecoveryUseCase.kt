package com.are.distribuidora.domain.pedido.usecase

import com.are.distribuidora.domain.pedido.PedidoRepository
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.model.PedidoDraftItem
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import javax.inject.Inject

/**
 * Revalida un borrador contra el estado ACTUAL del catálogo y de los pedidos antes
 * de ofrecer al vendedor retomarlo.
 *
 * El borrador puede llevar horas guardado: entre medias el catálogo pudo cambiar de
 * precios, algún producto pudo borrarse o desactivarse, o el propio pedido pudo
 * llegar a crearse desde otro sitio. La regla de "precio congelado" de la app aplica
 * al ítem de un pedido YA creado, no a un borrador, así que aquí manda el precio
 * vigente del catálogo.
 *
 * Dominio puro: sin dependencias de Android. `now` se inyecta para poder testearlo.
 */
class ValidateDraftForRecoveryUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val pedidoRepository: PedidoRepository,
) {

    sealed interface Outcome {
        /**
         * El borrador no debe ofrecerse. Ya fue (o debe ser) descartado.
         */
        data class Discard(val reason: DiscardReason) : Outcome

        /**
         * El borrador se puede retomar, posiblemente ajustado.
         * @param draft   borrador ya saneado (sin productos muertos, con precios vigentes).
         * @param notices cambios que hay que contarle al vendedor.
         */
        data class Restore(
            val draft: PedidoDraft,
            val notices: List<Notice>,
        ) : Outcome
    }

    enum class DiscardReason {
        /** Más viejo que [MAX_AGE_MS]: ya no representa una intención vigente. */
        TOO_OLD,

        /** Ya existe un pedido activo para ese cliente hoy: retomarlo sería duplicarlo. */
        ORDER_ALREADY_EXISTS,

        /** No quedó ningún ítem vivo tras la revalidación. */
        EMPTY_AFTER_VALIDATION,
    }

    sealed interface Notice {
        /** Productos que ya no existen o quedaron inactivos; se quitaron del carrito. */
        data class ItemsRemoved(val names: List<String>) : Notice

        /** Productos cuyo precio cambió; se adoptó el precio vigente. */
        data class PricesChanged(val changes: List<PriceChange>) : Notice

        /** La fecha de entrega guardada ya pasó; se reemplazó por [newDate]. */
        data class DeliveryDateReset(val previousDate: String, val newDate: String) : Notice
    }

    data class PriceChange(
        val name: String,
        val oldPrice: Double,
        val newPrice: Double,
    )

    /**
     * @param today fecha de hoy en formato "YYYY-MM-DD" (la misma que usa el flujo).
     * @param now   instante actual en epoch ms.
     */
    suspend operator fun invoke(
        draft: PedidoDraft,
        today: String,
        now: Long = System.currentTimeMillis(),
    ): Outcome {
        // 1) Demasiado viejo. Un borrador de hace más de una semana ya no refleja lo
        //    que el vendedor quería; ofrecerlo confunde más de lo que ayuda.
        if (now - draft.updatedAt > MAX_AGE_MS) {
            return Outcome.Discard(DiscardReason.TOO_OLD)
        }

        // 2) Anti duplicados: la app solo permite un pedido activo por cliente y día.
        //    Si ya existe, retomar el borrador terminaría en un error al confirmar.
        val clienteId = (draft.cliente as? ClienteSelection.Existente)?.clienteId
        if (clienteId != null) {
            val existing = pedidoRepository.findActivePedidoByClienteAndDay(clienteId, now)
            if (existing != null) {
                return Outcome.Discard(DiscardReason.ORDER_ALREADY_EXISTS)
            }
        }

        // 3) Revalidar ítems contra el catálogo.
        val notices = mutableListOf<Notice>()
        val removed = mutableListOf<String>()
        val priceChanges = mutableListOf<PriceChange>()
        val survivors = mutableListOf<PedidoDraftItem>()

        for (item in draft.items) {
            if (item.isCustom) {
                // Ítem personalizado: no vive en el catálogo, no hay nada que revalidar.
                survivors += item
                continue
            }
            // `ProductId.of` rechaza el id vacío; un borrador con un id corrupto se
            // trata igual que un producto que ya no existe.
            val productId = runCatching { ProductId.of(item.productoId) }.getOrNull()
            val product = productId?.let { productRepository.getById(it) }
            if (product == null || !product.isActive || product.isDeleted) {
                removed += item.nombre
                continue
            }
            val currentPrice = product.price.amount.toDouble()
            if (currentPrice != item.precioUnitario) {
                priceChanges += PriceChange(
                    name = product.name,
                    oldPrice = item.precioUnitario,
                    newPrice = currentPrice,
                )
                // Se adopta el precio vigente y se recalcula el descuento por PORCENTAJE,
                // que está expresado en relación al precio. Un descuento por MONTO fijo es
                // una cantidad en quetzales que el vendedor decidió: se respeta tal cual,
                // acotada al nuevo subtotal para que nunca deje el ítem en negativo.
                val newSubtotal = currentPrice * item.cantidad
                val newDiscount = if (item.descuentoPercent > 0.0) {
                    newSubtotal * (item.descuentoPercent / 100.0)
                } else {
                    item.descuentoAmount.coerceAtMost(newSubtotal)
                }
                survivors += item.copy(
                    precioUnitario = currentPrice,
                    descuentoAmount = newDiscount,
                    nombre = product.name,
                )
            } else {
                survivors += item
            }
        }

        if (survivors.isEmpty()) {
            return Outcome.Discard(DiscardReason.EMPTY_AFTER_VALIDATION)
        }

        if (removed.isNotEmpty()) notices += Notice.ItemsRemoved(removed)
        if (priceChanges.isNotEmpty()) notices += Notice.PricesChanged(priceChanges)

        // 4) Fecha de entrega vencida → se reemplaza por hoy y se avisa. Se prefiere
        //    esto a descartar: la fecha es lo más barato de corregir y descartar
        //    destruiría justo el trabajo que esta función existe para salvar.
        var deliveryDate = draft.deliveryDate
        if (deliveryDate.isNotBlank() && deliveryDate < today) {
            notices += Notice.DeliveryDateReset(previousDate = deliveryDate, newDate = today)
            deliveryDate = today
        }

        return Outcome.Restore(
            draft = draft.copy(items = survivors, deliveryDate = deliveryDate),
            notices = notices,
        )
    }

    companion object {
        /**
         * Antigüedad máxima de un borrador recuperable: 7 días.
         *
         * El enunciado permitía descartar a las 24 h. Se eligió un umbral más
         * generoso y, en su lugar, corregir la fecha de entrega vencida (ver paso 4):
         * un vendedor que pierde el pedido un viernes por la tarde y vuelve el lunes
         * sigue queriendo su carrito, y a las 24 h se le habría tirado. Más allá de
         * una semana sí se asume que el pedido ya no interesa.
         */
        const val MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
