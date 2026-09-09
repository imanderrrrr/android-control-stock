package com.are.distribuidora.stockmovement.domain.usecase

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.screenaccess.domain.repository.UserAccessProvider
import com.are.distribuidora.stockmovement.domain.model.MovementReason
import com.are.distribuidora.stockmovement.domain.model.MovementType
import com.are.distribuidora.stockmovement.domain.model.StockMovement
import com.are.distribuidora.stockmovement.domain.repository.StockMovementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Registra un vale manual de entrada o salida.
 *
 * Reglas:
 * - cantidad > 0; el motivo debe ser manual (COMPRA, DEVOLUCION, AJUSTE, MERMA, OTRO).
 * - El producto debe existir en el catálogo local.
 * - Se permite dejar el stock en negativo: es información, no un error.
 * - Autorización (integración 4.0+4.1): exige [Permission.CREATE_VOUCHER] vía [UserAccessProvider],
 *   la misma fuente única (RolePolicy) que usan los gates de pedidos. La UI oculta las entradas,
 *   pero el permiso se vuelve a comprobar aquí: ocultar un botón no es control de acceso.
 */
class CreateStockVoucherUseCase(
    private val movements: StockMovementRepository,
    private val products: ProductRepository,
    private val currentUser: CurrentUserIdProvider,
    private val userAccessProvider: UserAccessProvider,
) {
    suspend operator fun invoke(
        productId: String,
        type: MovementType,
        quantity: Int,
        reason: MovementReason,
        note: String?,
    ): Result<StockMovement> {
        if (!userAccessProvider.current().can(Permission.CREATE_VOUCHER)) {
            return Result.Error(Failure.Forbidden)
        }
        if (quantity <= 0) return Result.Error(Failure.ValidationError("La cantidad debe ser mayor a 0"))
        if (reason.isAutomatic) return Result.Error(Failure.ValidationError("Motivo no válido para un vale"))
        val uid = currentUser.get()
            ?: return Result.Error(Failure.ValidationError("No hay sesión activa"))
        val product = products.getById(ProductId.of(productId))
            ?: return Result.Error(Failure.NotFound)

        val movement = StockMovement(
            id = UUID.randomUUID().toString(),
            productId = product.id.value,
            productName = product.name,
            type = type,
            quantity = quantity,
            reason = reason,
            orderId = null,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = uid,
            createdByName = currentUser.getDisplayName() ?: uid,
            createdAt = System.currentTimeMillis(),
        )
        return try {
            movements.recordVoucher(movement)
            Result.Success(movement)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(Failure.DatabaseError)
        }
    }
}

/** Historial de movimientos de un producto (más reciente primero). */
class ObserveProductMovementsUseCase(
    private val repository: StockMovementRepository,
) {
    operator fun invoke(productId: String, limit: Int = 100): Flow<List<StockMovement>> =
        repository.observeByProduct(productId, limit)
}

/**
 * Sube los movimientos pendientes que no viajan con un pedido. Lo invoca el worker de
 * productos ANTES del downsync, para que el contador remoto ya incluya los incrementos.
 */
class SyncStockMovementsUseCase(
    private val repository: StockMovementRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val logger: Logger,
) {
    suspend operator fun invoke(): kotlin.Result<Unit> {
        if (!connectivityChecker.isOnline()) {
            logger.d(TAG, "Sin red: se omite la subida de movimientos")
            return kotlin.Result.success(Unit)
        }
        return try {
            repository.uploadPending()
            kotlin.Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Falló la subida de movimientos", e)
            kotlin.Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "SYNC_MOVEMENTS"
    }
}
