package com.are.distribuidora.data.repository

import com.are.distribuidora.data.local.dao.PedidoDraftDao
import com.are.distribuidora.data.local.entity.PedidoDraftEntity
import com.are.distribuidora.data.local.entity.PedidoDraftItemEntity
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.PedidoDraftRepository
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.ClienteSnapshot
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.model.PedidoDraftItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación sobre Room. Sin capa remota: el borrador es local por diseño.
 */
@Singleton
class PedidoDraftRepositoryImpl @Inject constructor(
    private val dao: PedidoDraftDao,
) : PedidoDraftRepository {

    override suspend fun save(draft: PedidoDraft) {
        val clienteId = (draft.cliente as? ClienteSelection.Existente)?.clienteId
        val temporal = draft.cliente as? ClienteSelection.Temporal

        val header = PedidoDraftEntity(
            vendedorId        = draft.vendedorId,
            routeId           = draft.routeId,
            clienteId         = clienteId,
            clienteNombre     = draft.clienteNombre,
            clienteTelefono   = temporal?.snapshot?.telefono,
            clienteDireccion  = temporal?.snapshot?.direccion,
            clienteEsTemporal = temporal != null,
            deliveryDate      = draft.deliveryDate,
            ivaEnabled        = draft.ivaEnabled,
            updatedAt         = draft.updatedAt,
        )
        val items = draft.items.map { item ->
            PedidoDraftItemEntity(
                vendedorId       = draft.vendedorId,
                productoId       = item.productoId,
                nombre           = item.nombre,
                precioUnitario   = item.precioUnitario,
                cantidad         = item.cantidad,
                descuentoAmount  = item.descuentoAmount,
                descuentoPercent = item.descuentoPercent,
                descuentoType    = item.descuentoType.name,
                notes            = item.notes,
                category         = item.category,
                imageUrl         = item.imageUrl,
                barcode          = item.barcode,
            )
        }
        dao.replaceDraft(header, items)
    }

    override suspend fun get(vendedorId: String): PedidoDraft? {
        val row = dao.getDraft(vendedorId) ?: return null
        val h = row.draft

        val cliente: ClienteSelection = if (h.clienteEsTemporal) {
            ClienteSelection.Temporal(
                ClienteSnapshot(
                    nombre    = h.clienteNombre,
                    telefono  = h.clienteTelefono,
                    direccion = h.clienteDireccion,
                )
            )
        } else {
            // Sin clienteId no se puede reconstruir un cliente existente: el borrador
            // está corrupto y es preferible no ofrecerlo a ofrecer un pedido sin cliente.
            val id = h.clienteId ?: return null
            ClienteSelection.Existente(id)
        }

        return PedidoDraft(
            vendedorId    = h.vendedorId,
            routeId       = h.routeId,
            cliente       = cliente,
            clienteNombre = h.clienteNombre,
            deliveryDate  = h.deliveryDate,
            ivaEnabled    = h.ivaEnabled,
            updatedAt     = h.updatedAt,
            items = row.items.map { e ->
                PedidoDraftItem(
                    productoId       = e.productoId,
                    nombre           = e.nombre,
                    precioUnitario   = e.precioUnitario,
                    cantidad         = e.cantidad,
                    descuentoAmount  = e.descuentoAmount,
                    descuentoPercent = e.descuentoPercent,
                    descuentoType    = runCatching { DiscountType.valueOf(e.descuentoType) }
                        .getOrDefault(DiscountType.PERCENTAGE),
                    notes            = e.notes,
                    category         = e.category,
                    imageUrl         = e.imageUrl,
                    barcode          = e.barcode,
                )
            },
        )
    }

    override suspend fun delete(vendedorId: String) {
        // Los ítems caen por ON DELETE CASCADE, pero los borramos explícitamente
        // porque las foreign keys de Room solo se aplican si están habilitadas en
        // la conexión, y no queremos depender de eso para no dejar huérfanos.
        dao.deleteItems(vendedorId)
        dao.deleteDraft(vendedorId)
    }
}
