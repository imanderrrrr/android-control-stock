package com.are.distribuidora.pedido.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountByAmountUseCase
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.domain.pedido.usecase.RoundToQuarterQuetzalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * DTO de presentación del carrito — sin lógica de negocio.
 *
 * El cálculo del descuento es responsabilidad de [ApplyItemDiscountUseCase] /
 * [ApplyItemDiscountByAmountUseCase] (dominio).
 * Este data class solo transporta los valores ya calculados hacia la UI.
 *
 * @param discountAmount  monto absoluto de descuento (calculado por el UseCase). Default 0.
 * @param discountPercent porcentaje informativo para mostrar en el badge de la UI. Default 0.
 * @param discountType    tipo de descuento aplicado (PERCENTAGE o AMOUNT). Default PERCENTAGE.
 */
data class CartItem(
    val productId: String,
    val name: String,
    val priceAmount: Double,
    val category: String?,
    val quantity: Int,
    val imageUrl: String? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val discountType: DiscountType = DiscountType.PERCENTAGE,
) {
    /** Subtotal sin descuento: precio × cantidad. */
    val subtotalBase: Double get() = priceAmount * quantity

    /** Subtotal final ya con el monto de descuento aplicado. */
    val subtotal: Double get() = (subtotalBase - discountAmount).coerceAtLeast(0.0)

    /** true si hay descuento activo. */
    val hasDiscount: Boolean get() = discountAmount > 0.0
}

@HiltViewModel
class CreatePedidoFlowViewModel @Inject constructor(
    private val applyItemDiscountUseCase: ApplyItemDiscountUseCase,
    private val applyItemDiscountByAmountUseCase: ApplyItemDiscountByAmountUseCase,
    private val clientRepository: ClientRepository,
) : ViewModel() {

    private val _routeId = MutableStateFlow<String?>(null)
    val routeId: StateFlow<String?> = _routeId.asStateFlow()

    private val _deliveryDate = MutableStateFlow<String>("")
    /** Fecha de entrega en formato "YYYY-MM-DD". Se guarda junto con la selección de ruta. */
    val deliveryDate: StateFlow<String> = _deliveryDate.asStateFlow()

    private val _clienteSelection = MutableStateFlow<ClienteSelection?>(null)
    val clienteSelection: StateFlow<ClienteSelection?> = _clienteSelection.asStateFlow()

    // ── Carrito ──────────────────────────────────────────────────────────────
    private val _cartItems = MutableStateFlow<Map<String, CartItem>>(emptyMap())
    val cartItems: StateFlow<Map<String, CartItem>> = _cartItems.asStateFlow()

    /** Total del carrito redondeado al múltiplo de Q 0.25 más cercano (GT). */
    val cartTotal: StateFlow<Double> = _cartItems
        .map { map -> RoundToQuarterQuetzalUseCase(map.values.sumOf { it.subtotal }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Límite de compra del cliente seleccionado (centavos). null = sin límite. */
    private val _maxOrderAmountInCents = MutableStateFlow<Long?>(null)
    val maxOrderAmountInCents: StateFlow<Long?> = _maxOrderAmountInCents.asStateFlow()

    /** Nombre/dirección del cliente seleccionado, para los encabezados del flujo. */
    private val _clienteNombre = MutableStateFlow<String?>(null)
    val clienteNombre: StateFlow<String?> = _clienteNombre.asStateFlow()

    private val _clienteDireccion = MutableStateFlow<String?>(null)
    val clienteDireccion: StateFlow<String?> = _clienteDireccion.asStateFlow()

    /**
     * true cuando el total del carrito excede el límite del cliente.
     * La UI usa este flag para mostrar una advertencia visual en tiempo real.
     */
    val isOverLimit: StateFlow<Boolean> = combine(
        cartTotal,
        _maxOrderAmountInCents,
    ) { total, limit ->
        if (limit == null) false
        else (total * 100).toLong() > limit
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSelection(routeId: String, selection: ClienteSelection, deliveryDate: String = "") {
        _routeId.value = routeId
        _deliveryDate.value = deliveryDate
        _clienteSelection.value = selection
        loadClientOrderLimit(selection)
    }

    /**
     * Carga el límite de compra del cliente desde Room (offline-first).
     * Solo aplica a clientes existentes; temporales no tienen límite.
     */
    private fun loadClientOrderLimit(selection: ClienteSelection) {
        when (selection) {
            is ClienteSelection.Existente -> {
                viewModelScope.launch {
                    val result = clientRepository.getClientById(selection.clienteId)
                    if (result is Result.Success) {
                        _maxOrderAmountInCents.value = result.value?.maxOrderAmountInCents
                        _clienteNombre.value = result.value?.name
                        _clienteDireccion.value = result.value?.address
                    } else {
                        _maxOrderAmountInCents.value = null
                    }
                }
            }
            is ClienteSelection.Temporal -> {
                _maxOrderAmountInCents.value = null
                _clienteNombre.value = selection.snapshot.nombre
                _clienteDireccion.value = selection.snapshot.direccion
            }
        }
    }

    fun clear() {
        _routeId.value = null
        _deliveryDate.value = ""
        _clienteSelection.value = null
        _maxOrderAmountInCents.value = null
        _clienteNombre.value = null
        _clienteDireccion.value = null
        _cartItems.value = emptyMap()
    }

    /** Agrega un producto al carrito (qty=1) o incrementa si ya existe. */
    fun add(product: Product) {
        val id = product.id.value
        val current = _cartItems.value.toMutableMap()
        val existing = current[id]
        current[id] = if (existing != null) {
            existing.copy(quantity = existing.quantity + 1)
        } else {
            CartItem(
                productId   = id,
                name        = product.name,
                priceAmount = product.price.amount.toDouble(),
                category    = product.category,
                quantity    = 1,
                imageUrl    = product.imageUrl,
                barcode     = product.barcode,
            )
        }
        _cartItems.value = current
    }

    /** Incrementa la cantidad de un producto en el carrito. */
    fun increment(productId: String) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return
        current[productId] = item.copy(quantity = item.quantity + 1)
        _cartItems.value = current
    }

    /** Decrementa la cantidad. Si llega a 0, elimina el item. */
    fun decrement(productId: String) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return
        if (item.quantity <= 1) {
            current.remove(productId)
        } else {
            current[productId] = item.copy(quantity = item.quantity - 1)
        }
        _cartItems.value = current
    }

    /** Establece una cantidad directa (0 = elimina).
     *  Si el ítem tiene descuento activo, recalcula el monto absoluto para la nueva cantidad. */
    fun setQuantity(productId: String, qty: Int) {
        if (qty <= 0) {
            _cartItems.value = _cartItems.value.toMutableMap().also { it.remove(productId) }
        } else {
            val current = _cartItems.value.toMutableMap()
            val item = current[productId] ?: return

            // Recalcular monto de descuento si hay descuento activo.
            // FIX BUG #3: los UseCases tienen require() que lanzan IllegalArgumentException
            // si el precio es negativo (dato corrupto en BD). Capturamos y usamos 0.0 de fallback.
            val newDiscountAmount = try {
                when {
                    item.discountType == DiscountType.PERCENTAGE && item.discountPercent > 0.0 ->
                        applyItemDiscountUseCase(
                            precioUnitario = item.priceAmount,
                            cantidad       = qty,
                            porcentaje     = item.discountPercent,
                        )
                    item.discountType == DiscountType.AMOUNT && item.discountAmount > 0.0 ->
                        applyItemDiscountByAmountUseCase(
                            precioUnitario = item.priceAmount,
                            cantidad       = qty,
                            montoDescuento = item.discountAmount,
                        )
                    else -> 0.0
                }
            } catch (_: IllegalArgumentException) {
                0.0 // fallback seguro: sin descuento si los valores son inválidos
            }

            current[productId] = item.copy(
                quantity       = qty,
                discountAmount = newDiscountAmount,
            )
            _cartItems.value = current
        }
    }

    /** Actualiza las notas de un ítem del carrito. null/blank = sin notas. */
    fun setNotes(productId: String, notes: String?) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return
        current[productId] = item.copy(notes = notes?.trim()?.takeIf { it.isNotBlank() })
        _cartItems.value = current
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
    }

    /** Elimina un producto del carrito por ID. */
    fun removeFromCart(productId: String) {
        _cartItems.value = _cartItems.value.toMutableMap().also { it.remove(productId) }
    }

    /**
     * Aplica (o elimina) un descuento por PORCENTAJE a un ítem del carrito.
     *
     * El cálculo porcentaje → monto absoluto se delega a [ApplyItemDiscountUseCase] (dominio).
     *
     * @param percent valor entre 0.0 y 100.0; 0 = sin descuento.
     */
    fun setDiscount(productId: String, percent: Double) {
        val clamped = percent.coerceIn(0.0, 100.0)
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return

        val discountMonto = try {
            applyItemDiscountUseCase(
                precioUnitario = item.priceAmount,
                cantidad       = item.quantity,
                porcentaje     = clamped,
            )
        } catch (_: IllegalArgumentException) {
            0.0
        }

        current[productId] = item.copy(
            discountAmount  = discountMonto,
            discountPercent = clamped,
            discountType    = DiscountType.PERCENTAGE,
        )
        _cartItems.value = current
    }

    /**
     * Aplica (o elimina) un descuento por MONTO FIJO en Quetzales a un ítem del carrito.
     *
     * El clamp y redondeo se delega a [ApplyItemDiscountByAmountUseCase] (dominio).
     *
     * @param amount monto en Q ≥ 0; 0 = sin descuento.
     */
    fun setDiscountByAmount(productId: String, amount: Double) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return

        val discountMonto = try {
            applyItemDiscountByAmountUseCase(
                precioUnitario = item.priceAmount,
                cantidad       = item.quantity,
                montoDescuento = amount.coerceAtLeast(0.0),
            )
        } catch (_: IllegalArgumentException) {
            0.0
        }

        current[productId] = item.copy(
            discountAmount  = discountMonto,
            discountPercent = 0.0,
            discountType    = DiscountType.AMOUNT,
        )
        _cartItems.value = current
    }

    /**
     * Agrega un producto al carrito con cantidad y notas específicas (desde detalle).
     * Si ya existe el item, SUMA la cantidad y actualiza notas.
     */
    fun addToCart(product: Product, quantity: Int, notes: String?) {
        if (quantity <= 0) return
        val id = product.id.value
        val current = _cartItems.value.toMutableMap()
        val existing = current[id]
        current[id] = if (existing != null) {
            existing.copy(
                quantity = existing.quantity + quantity,
                notes    = notes?.takeIf { it.isNotBlank() } ?: existing.notes,
            )
        } else {
            CartItem(
                productId   = id,
                name        = product.name,
                priceAmount = product.price.amount.toDouble(),
                category    = product.category,
                quantity    = quantity,
                imageUrl    = product.imageUrl,
                barcode     = product.barcode,
                notes       = notes?.takeIf { it.isNotBlank() },
            )
        }
        _cartItems.value = current
    }

    /**
     * Agrega un ítem personalizado al carrito (sin stock, sin Product en BD).
     * Cada llamada crea un ítem independiente identificado por un UUID prefijado
     * con "custom_", de modo que nunca colisiona con IDs de productos reales.
     *
     * @param name     Nombre libre del ítem.
     * @param quantity Cantidad (> 0).
     * @param price    Precio unitario (≥ 0).
     * @param notes    Detalles opcionales.
     */
    fun addCustomItem(name: String, quantity: Int, price: Double, notes: String?) {
        if (quantity <= 0 || price < 0.0) return
        val customId = "custom_${UUID.randomUUID()}"
        val current  = _cartItems.value.toMutableMap()
        current[customId] = CartItem(
            productId   = customId,
            name        = name.trim(),
            priceAmount = price,
            category    = null,
            quantity    = quantity,
            imageUrl    = null,
            notes       = notes?.takeIf { it.isNotBlank() },
        )
        _cartItems.value = current
    }
}
