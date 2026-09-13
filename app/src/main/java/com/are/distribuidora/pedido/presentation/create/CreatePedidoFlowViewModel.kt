package com.are.distribuidora.pedido.presentation.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.pedido.DiscountType
import com.are.distribuidora.domain.pedido.PedidoDraftRepository
import com.are.distribuidora.domain.pedido.model.ClienteSelection
import com.are.distribuidora.domain.pedido.model.PedidoDraft
import com.are.distribuidora.domain.pedido.model.PedidoDraftItem
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountByAmountUseCase
import com.are.distribuidora.domain.pedido.usecase.ApplyItemDiscountUseCase
import com.are.distribuidora.domain.pedido.usecase.RoundToQuarterQuetzalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** Tasa de IVA (12%) aplicada cuando el vendedor activa el IVA en el carrito. */
private const val IVA_RATE = 0.12

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
    private val draftRepository: PedidoDraftRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    // ── Borrador persistente ─────────────────────────────────────────────────
    //
    // Requisito de Anderson: en cuanto cambie CUALQUIER cosa del pedido (agregar un
    // ítem, tocar una cantidad, aplicar un descuento, elegir cliente o fecha) tiene
    // que quedar guardado en local, para que un cierre inesperado no se lleve el
    // trabajo. Nada de debounce: una ventana de 300 ms es justo lo que se perdería
    // si la app muere justo después de un toque.
    //
    // El guardado es INMEDIATO pero SERIALIZADO: cada mutación pide un guardado con
    // [requestDraftSave] y un único consumidor los atiende de uno en uno. El canal
    // tiene capacidad 1 con DROP_OLDEST, así que si el vendedor machaca el "+" más
    // rápido de lo que Room escribe, las peticiones intermedias se descartan pero
    // SIEMPRE queda una pendiente que persiste el estado final — nunca se pierde el
    // último cambio y nunca hay dos escrituras pisándose. `tryEmit` no suspende, así
    // que la UI jamás se bloquea por esto.
    private val draftSaveRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** uid del vendedor dueño del borrador. Se cachea para no leer la sesión en cada guardado. */
    @Volatile
    private var vendedorId: String? = null

    init {
        viewModelScope.launch {
            // `collect` (no `collectLatest`): una escritura en curso nunca se cancela
            // a medias; la siguiente petición espera su turno y persiste el estado
            // más reciente, que es el que importa.
            draftSaveRequests.collect { persistDraft() }
        }
    }

    private fun requestDraftSave() {
        draftSaveRequests.tryEmit(Unit)
    }

    private suspend fun currentVendedorId(): String? =
        vendedorId ?: authRepository.getCurrentSession()?.userId?.also { vendedorId = it }

    /**
     * Escribe el estado actual del flujo en Room, o borra el borrador si ya no hay
     * nada que recuperar.
     *
     * Lee el estado en el momento de escribir (no un snapshot capturado al pedir el
     * guardado), que es lo que hace correcta la conflación descrita arriba.
     */
    private suspend fun persistDraft() {
        val uid = currentVendedorId() ?: return
        val route = _routeId.value
        val selection = _clienteSelection.value
        val items = _cartItems.value

        // Sin flujo activo o con el carrito vacío no hay nada que retomar: se borra
        // para que "existe borrador" siga significando "hay trabajo sin terminar".
        if (route.isNullOrBlank() || selection == null || items.isEmpty()) {
            runCatching { draftRepository.delete(uid) }
            return
        }

        val draft = PedidoDraft(
            vendedorId    = uid,
            routeId       = route,
            cliente       = selection,
            clienteNombre = _clienteNombre.value
                ?: (selection as? ClienteSelection.Temporal)?.snapshot?.nombre
                ?: "",
            deliveryDate  = _deliveryDate.value,
            ivaEnabled    = _ivaEnabled.value,
            updatedAt     = System.currentTimeMillis(),
            items = items.values.map { item ->
                PedidoDraftItem(
                    productoId       = item.productId,
                    nombre           = item.name,
                    precioUnitario   = item.priceAmount,
                    cantidad         = item.quantity,
                    descuentoAmount  = item.discountAmount,
                    descuentoPercent = item.discountPercent,
                    descuentoType    = item.discountType,
                    notes            = item.notes,
                    category         = item.category,
                    imageUrl         = item.imageUrl,
                    barcode          = item.barcode,
                )
            },
        )
        // El borrador es una comodidad: si Room falla, el pedido en memoria sigue
        // intacto y el vendedor puede confirmarlo igual. No se propaga el error.
        runCatching { draftRepository.save(draft) }
    }

    /**
     * Rehidrata el flujo desde un borrador ya revalidado y lo vuelve a guardar
     * (el borrador puede haber cambiado en la revalidación: precios nuevos, ítems
     * quitados o fecha de entrega corregida).
     */
    fun restoreFrom(draft: PedidoDraft) {
        vendedorId = draft.vendedorId
        _routeId.value = draft.routeId
        _deliveryDate.value = draft.deliveryDate
        _clienteSelection.value = draft.cliente
        _ivaEnabled.value = draft.ivaEnabled
        _cartItems.value = draft.items.associate { item ->
            item.productoId to CartItem(
                productId       = item.productoId,
                name            = item.nombre,
                priceAmount     = item.precioUnitario,
                category        = item.category,
                quantity        = item.cantidad,
                imageUrl        = item.imageUrl,
                barcode         = item.barcode,
                notes           = item.notes,
                discountAmount  = item.descuentoAmount,
                discountPercent = item.descuentoPercent,
                discountType    = item.descuentoType,
            )
        }
        loadClientOrderLimit(draft.cliente)
        requestDraftSave()
    }

    /**
     * Descarta el borrador del vendedor sin tocar el estado en memoria.
     * Lo usa la recuperación cuando el vendedor elige "Descartar".
     */
    fun discardDraft(vendedorIdToDiscard: String) {
        viewModelScope.launch { runCatching { draftRepository.delete(vendedorIdToDiscard) } }
    }

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

    // ── IVA (12%) opcional ─────────────────────────────────────────────────────
    private val _ivaEnabled = MutableStateFlow(false)
    /** Si el vendedor activó el IVA del 12% en el carrito. Por defecto desactivado. */
    val ivaEnabled: StateFlow<Boolean> = _ivaEnabled.asStateFlow()

    fun setIvaEnabled(enabled: Boolean) {
        _ivaEnabled.value = enabled
        requestDraftSave()
    }

    /** Subtotal neto del carrito (suma de subtotales de ítems, sin IVA). */
    val cartSubtotal: StateFlow<Double> = _cartItems
        .map { map -> map.values.sumOf { it.subtotal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Total del carrito redondeado al Q 0.25; suma el IVA 12% cuando está activo. */
    val cartTotal: StateFlow<Double> = combine(cartSubtotal, _ivaEnabled) { sub, on ->
        RoundToQuarterQuetzalUseCase(if (on) sub * (1.0 + IVA_RATE) else sub)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Monto de IVA a mostrar (total − subtotal cuando está activo; 0 si no). */
    val cartIva: StateFlow<Double> = combine(cartSubtotal, cartTotal, _ivaEnabled) { sub, total, on ->
        if (on) (total - sub).coerceAtLeast(0.0) else 0.0
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

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
        requestDraftSave()
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
                        requestDraftSave()
                    } else {
                        _maxOrderAmountInCents.value = null
                    }
                }
            }
            is ClienteSelection.Temporal -> {
                _maxOrderAmountInCents.value = null
                _clienteNombre.value = selection.snapshot.nombre
                _clienteDireccion.value = selection.snapshot.direccion
                requestDraftSave()
            }
        }
    }

    /**
     * Cierra el flujo LIMPIAMENTE: borra el estado en memoria y el borrador persistido.
     *
     * Este es el punto que le da sentido a toda la recuperación: se llama al confirmar
     * el pedido con éxito y al abandonar el flujo a propósito. Por eso, que al arrancar
     * exista un borrador significa exactamente que nadie cerró el flujo bien — sea por
     * un crash o porque Android mató el proceso por memoria.
     */
    fun clear() {
        val uid = vendedorId
        if (uid != null) {
            viewModelScope.launch { runCatching { draftRepository.delete(uid) } }
        }
        _routeId.value = null
        _deliveryDate.value = ""
        _clienteSelection.value = null
        _maxOrderAmountInCents.value = null
        _clienteNombre.value = null
        _clienteDireccion.value = null
        _ivaEnabled.value = false
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
        requestDraftSave()
    }

    /** Incrementa la cantidad de un producto en el carrito. */
    fun increment(productId: String) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return
        current[productId] = item.copy(quantity = item.quantity + 1)
        _cartItems.value = current
        requestDraftSave()
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
        requestDraftSave()
    }

    /** Establece una cantidad directa (0 = elimina).
     *  Si el ítem tiene descuento activo, recalcula el monto absoluto para la nueva cantidad. */
    fun setQuantity(productId: String, qty: Int) {
        if (qty <= 0) {
            _cartItems.value = _cartItems.value.toMutableMap().also { it.remove(productId) }
            requestDraftSave()
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
        requestDraftSave()
        }
    }

    /** Actualiza las notas de un ítem del carrito. null/blank = sin notas. */
    fun setNotes(productId: String, notes: String?) {
        val current = _cartItems.value.toMutableMap()
        val item = current[productId] ?: return
        current[productId] = item.copy(notes = notes?.trim()?.takeIf { it.isNotBlank() })
        _cartItems.value = current
        requestDraftSave()
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
        requestDraftSave()
    }

    /** Elimina un producto del carrito por ID. */
    fun removeFromCart(productId: String) {
        _cartItems.value = _cartItems.value.toMutableMap().also { it.remove(productId) }
        requestDraftSave()
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
        requestDraftSave()
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
        requestDraftSave()
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
        requestDraftSave()
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
        requestDraftSave()
    }
}
