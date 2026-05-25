package com.are.distribuidora.pedido.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.pedido.PedidoWithItems
import com.are.distribuidora.domain.pedido.SyncStatusLabel
import com.are.distribuidora.domain.pedido.usecase.DeletePedidoUseCase
import com.are.distribuidora.domain.pedido.usecase.ObserveAllPedidosUseCase
import com.are.distribuidora.orders.domain.model.Order
import com.are.distribuidora.orders.domain.model.OrderDownloadStatus
import com.are.distribuidora.domain.product.GetProductImageUseCase
import com.are.distribuidora.orders.domain.usecase.DownloadOrderItemsUseCase
import com.are.distribuidora.orders.domain.usecase.FetchAllOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.FetchOrdersHeaderUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteAndDateUseCase
import com.are.distribuidora.orders.domain.usecase.ObserveOtherOrdersByRouteUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel compartido por los niveles de la pantalla Pedidos.
 *
 * - "Mis Pedidos": reactivo vía [ObserveAllPedidosUseCase].
 * - "Otros Pedidos": reactivo vía [ObserveOtherOrdersByRouteUseCase] por ruta.
 *   Permite descargar items con [DownloadOrderItemsUseCase].
 */
@HiltViewModel
class PedidosViewModel @Inject constructor(
    private val observeAllPedidosUseCase: ObserveAllPedidosUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val deletePedidoUseCase: DeletePedidoUseCase,
    private val authRepository: AuthRepository,
    private val observeOtherOrdersByRouteUseCase: ObserveOtherOrdersByRouteUseCase,
    private val observeOtherOrdersByRouteAndDateUseCase: ObserveOtherOrdersByRouteAndDateUseCase,
    private val downloadOrderItemsUseCase: DownloadOrderItemsUseCase,
    private val fetchOrdersHeaderUseCase: FetchOrdersHeaderUseCase,
    private val fetchAllOrdersHeaderUseCase: FetchAllOrdersHeaderUseCase,
    private val getProductImageUseCase: GetProductImageUseCase,
) : ViewModel() {

    // ── Mis Pedidos ──────────────────────────────────────────────────────────

    sealed class UiState {
        object Loading : UiState()
        data class Success(val routes: List<RouteOrderSummaryUiModel>) : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class DeleteEvent {
        object Success : DeleteEvent()
        data class Error(val message: String) : DeleteEvent()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deleteEvent = MutableSharedFlow<DeleteEvent>()
    val deleteEvent: SharedFlow<DeleteEvent> = _deleteEvent.asSharedFlow()

    private var allPedidos: List<PedidoWithItems> = emptyList()

    // ── Otros Pedidos ────────────────────────────────────────────────────────

    sealed class OtrosUiState {
        object Loading : OtrosUiState()
        data class Success(val routes: List<OtrosRouteUiModel>) : OtrosUiState()
        data class Error(val message: String) : OtrosUiState()
    }

    sealed class DownloadEvent {
        data class Success(val orderId: String) : DownloadEvent()
        data class Error(val orderId: String, val message: String) : DownloadEvent()
    }

    private val _otrosUiState = MutableStateFlow<OtrosUiState>(OtrosUiState.Loading)
    val otrosUiState: StateFlow<OtrosUiState> = _otrosUiState.asStateFlow()

    private val _downloadEvent = MutableSharedFlow<DownloadEvent>()
    val downloadEvent: SharedFlow<DownloadEvent> = _downloadEvent.asSharedFlow()

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val otrosOrdersByRoute = mutableMapOf<String, List<Order>>()
    private val routeObserveJobs   = mutableMapOf<String, Job>()

    // ── Compartido ───────────────────────────────────────────────────────────

    private var routesMap: Map<String, String> = emptyMap()

    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }
    private val dateFormat    = SimpleDateFormat("d MMM yyyy", Locale("es", "GT"))
    private val dbDateFormat  = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Filtro de fecha — Mis Pedidos ─────────────────────────────────────────

    /**
     * Fecha seleccionada en formato "YYYY-MM-DD" para filtrar pedidos.
     * Por defecto es el día de hoy al abrir la pantalla.
     * El Fragment puede actualizar este valor con [setSelectedDate].
     */
    private val _selectedDate = MutableStateFlow(todayAsDbString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /** Cambia la fecha activa y provoca que el Flow de pedidos se re-suscriba. */
    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    // ── Filtro de fecha — Otros Pedidos (estado INDEPENDIENTE) ────────────────

    /**
     * Fecha seleccionada en formato "YYYY-MM-DD" exclusivamente para "Otros Pedidos".
     * Independiente de [selectedDate] de "Mis Pedidos".
     * Por defecto es hoy.
     */
    private val _otrosSelectedDate = MutableStateFlow(todayAsDbString())
    val otrosSelectedDate: StateFlow<String> = _otrosSelectedDate.asStateFlow()

    /**
     * Cambia la fecha activa de "Otros Pedidos", limpia el mapa en memoria
     * y re-lanza la observación reactiva para la nueva fecha.
     */
    fun setOtrosSelectedDate(dbDate: String) {
        _otrosSelectedDate.value = dbDate
    }

    /** Devuelve la fecha de hoy en formato "YYYY-MM-DD". */
    fun todayAsDbString(): String = dbDateFormat.format(Date())

    /** Formatea un string "YYYY-MM-DD" como "d MMM yyyy" para mostrarlo en la UI. */
    fun formatDateForDisplay(dbDate: String): String = try {
        val parsed = dbDateFormat.parse(dbDate)
        if (parsed != null) dateFormat.format(parsed) else dbDate
    } catch (e: Exception) { dbDate }

    init {
        viewModelScope.launch {
            // 1. Cargar rutas primero para que resolveRouteName funcione correctamente
            // desde el primer emit de pedidos
            routesMap = when (val r = getRoutesUseCase()) {
                is Result.Success -> r.value.associate { it.id to it.name }
                else              -> emptyMap()
            }

            // 2. Solo después arrancar la observación reactiva de pedidos
            _selectedDate
                .flatMapLatest { date -> observeAllPedidosUseCase(date) }
                .collect { pedidos ->
                    allPedidos = pedidos
                    _uiState.value = UiState.Success(buildRouteSummaries(pedidos))
                }
        }
    }

    // ── Mis Pedidos — API pública ─────────────────────────────────────────────

    fun getPedidosByRoute(routeId: String): List<PedidoClienteUiModel> =
        allPedidos
            .filter { it.pedido.routeId == routeId }
            .sortedByDescending { it.pedido.creadoEn }
            .map { mapToPedidoClienteUiModel(it) }

    fun getPedidoForPrint(pedidoId: String): PedidoWithItems? =
        allPedidos.firstOrNull { it.pedido.id == pedidoId }

    suspend fun getVendorEmail(): String =
        authRepository.getCurrentSession()?.email ?: "Vendedor"

    suspend fun getItemsByPedido(pedidoId: String): List<PedidoItemUiModel> =
        allPedidos
            .firstOrNull { it.pedido.id == pedidoId }
            ?.items
            ?.map { item ->
                PedidoItemUiModel(
                    itemId             = item.id,
                    nombre             = item.nombre,
                    precioUnitario     = currencyFormat.format(item.precioUnitario),
                    cantidad           = item.cantidad,
                    descuentoFormatted = if (item.descuentoItem > 0)
                        "-${currencyFormat.format(item.descuentoItem)}" else null,
                    totalItem          = currencyFormat.format(item.totalItem),
                    notes              = item.notes?.takeIf { it.isNotBlank() },
                    imageUrl           = getProductImageUseCase(item.productoId),
                )
            } ?: emptyList()

    fun getClienteNombre(pedidoId: String): String =
        allPedidos.firstOrNull { it.pedido.id == pedidoId }
            ?.pedido?.clienteSnapshot?.nombre ?: ""

    fun getTotalPedido(pedidoId: String): String =
        allPedidos.firstOrNull { it.pedido.id == pedidoId }
            ?.pedido?.total?.let { currencyFormat.format(it) } ?: ""

    fun deletePedido(pedidoId: String) {
        viewModelScope.launch {
            when (deletePedidoUseCase(pedidoId)) {
                is Result.Success -> _deleteEvent.emit(DeleteEvent.Success)
                is Result.Error   -> _deleteEvent.emit(DeleteEvent.Error("Error al eliminar el pedido"))
            }
        }
    }

    // ── Otros Pedidos — API pública ───────────────────────────────────────────

    /**
     * Inicia la observación reactiva de todas las rutas conocidas para "Otros Pedidos".
     * Debe llamarse una vez al mostrarse el tab "Otros Pedidos".
     *
     * Cada vez que [otrosSelectedDate] cambia:
     *  1. Se cancela la observación anterior por ruta.
     *  2. Se limpia el mapa en memoria (evita mezcla visual de fechas distintas).
     *  3. Se emite Loading mientras se re-suscriben las rutas.
     *  4. Se lanza fetch desde Firestore para la nueva fecha+ruta.
     *  5. Room notifica al Flow con los resultados persistidos.
     */
    fun startObservingAllOtherOrders() {
        viewModelScope.launch {
            val routes = when (val r = getRoutesUseCase()) {
                is Result.Success -> r.value
                else              -> return@launch
            }
            if (routes.isEmpty()) {
                _otrosUiState.value = OtrosUiState.Success(emptyList())
                return@launch
            }

            // Reactivo a cambios de fecha: al cambiar otrosSelectedDate se cancela todo
            // lo anterior y se re-suscribe con la nueva fecha.
            _otrosSelectedDate.collect { date ->
                // 1) Cancelar observaciones anteriores por ruta
                routeObserveJobs.values.forEach { it.cancel() }
                routeObserveJobs.clear()

                // 2) Limpiar resultados anteriores y emitir Loading para evitar mezcla visual
                otrosOrdersByRoute.clear()
                _otrosUiState.value = OtrosUiState.Loading

                // 3) Suscribir el Flow de Room para cada ruta filtrado por fecha
                routes.forEach { route -> observeOtherOrdersByDate(route.id, date) }

                // 4) Disparar fetch desde Firestore para esa ruta+fecha
                routes.forEach { route ->
                    launch { fetchOrdersHeaderUseCase.execute(routeId = route.id, deliveryDate = date) }
                }
            }
        }
    }

    /**
     * Observa reactivamente los pedidos de una ruta para la fecha indicada.
     * Es idempotente por clave ruta+fecha (se gestiona externamente).
     */
    fun observeOtherOrdersByDate(routeId: String, deliveryDate: String) {
        if (routeObserveJobs[routeId]?.isActive == true) return
        routeObserveJobs[routeId] = viewModelScope.launch {
            observeOtherOrdersByRouteAndDateUseCase(routeId, deliveryDate).collect { orders ->
                otrosOrdersByRoute[routeId] = orders
                _otrosUiState.value = OtrosUiState.Success(
                    buildOtrosRouteSummaries(otrosOrdersByRoute)
                )
            }
        }
    }

    /**
     * @deprecated Usa [observeOtherOrdersByDate]. Mantenido para compatibilidad interna.
     * Inicia la observación reactiva para una ruta específica (sin filtro de fecha).
     */
    @Suppress("unused")
    fun observeOtherOrders(routeId: String) {
        if (routeObserveJobs[routeId]?.isActive == true) return
        routeObserveJobs[routeId] = viewModelScope.launch {
            observeOtherOrdersByRouteUseCase(routeId).collect { orders ->
                otrosOrdersByRoute[routeId] = orders
                _otrosUiState.value = OtrosUiState.Success(
                    buildOtrosRouteSummaries(otrosOrdersByRoute)
                )
            }
        }
    }

    /**
     * Descarga headers de Firestore para una ruta+fecha.
     * Room notifica automáticamente al Flow tras el upsert.
     */
    @Suppress("unused")
    fun fetchOtherOrdersHeaders(routeId: String, deliveryDate: String) {
        viewModelScope.launch {
            fetchOrdersHeaderUseCase.execute(routeId = routeId, deliveryDate = deliveryDate)
        }
    }

    /**
     * Descarga items de un pedido de otro vendedor.
     * Muestra spinner individual en la tarjeta mientras está en curso.
     */
    fun downloadOrderItems(orderId: String) {
        if (_downloadingIds.value.contains(orderId)) return
        viewModelScope.launch {
            _downloadingIds.value = _downloadingIds.value + orderId
            when (downloadOrderItemsUseCase.execute(orderId)) {
                is Result.Success -> _downloadEvent.emit(DownloadEvent.Success(orderId))
                is Result.Error   -> _downloadEvent.emit(
                    DownloadEvent.Error(orderId, "Error al descargar pedido")
                )
            }
            _downloadingIds.value = _downloadingIds.value - orderId
        }
    }

    // ── Mappers privados ──────────────────────────────────────────────────────

    private fun buildRouteSummaries(all: List<PedidoWithItems>): List<RouteOrderSummaryUiModel> =
        all
            .groupBy { it.pedido.routeId }
            .map { (routeId, pedidos) ->
                RouteOrderSummaryUiModel(
                    routeId        = routeId,
                    routeName      = resolveRouteName(routeId),
                    pedidoCount    = pedidos.size,
                    totalFormatted = currencyFormat.format(pedidos.sumOf { it.pedido.total }),
                )
            }
            .sortedBy { it.routeName }

    private fun buildOtrosRouteSummaries(
        byRoute: Map<String, List<Order>>,
    ): List<OtrosRouteUiModel> =
        byRoute
            .filter { it.value.isNotEmpty() }
            .map { (routeId, orders) ->
                // Sumar solo los pedidos que ya tienen total (descargados)
                val completedTotals = orders
                    .filter { it.totalAmount != null && it.downloadStatus == OrderDownloadStatus.COMPLETED }
                    .mapNotNull { it.totalAmount }
                val routeTotal = if (completedTotals.isNotEmpty()) {
                    currencyFormat.format(completedTotals.sum())
                } else null

                OtrosRouteUiModel(
                    routeId             = routeId,
                    routeName           = resolveRouteName(routeId),
                    orderCount          = orders.size,
                    orders              = orders.map { mapToOtrosHeaderUiModel(it) },
                    routeTotalFormatted = routeTotal,
                )
            }
            .sortedBy { it.routeName }

    private fun resolveRouteName(routeId: String): String = when {
        routeId.isBlank()              -> "Sin ruta"
        routesMap.containsKey(routeId) -> routesMap[routeId]!!
        else                           -> routeId
    }

    private fun mapToOtrosHeaderUiModel(order: Order): OtrosOrderHeaderUiModel {
        val (statusLabel, uiStatus) = when (order.downloadStatus) {
            OrderDownloadStatus.NONE,
            OrderDownloadStatus.ITEMS_PENDING -> "Pendiente"      to OtrosDownloadUiStatus.PENDING
            OrderDownloadStatus.IN_PROGRESS   -> "Descargando…"   to OtrosDownloadUiStatus.IN_PROGRESS
            OrderDownloadStatus.COMPLETED     -> "Descargado"     to OtrosDownloadUiStatus.COMPLETED
            OrderDownloadStatus.FAILED        -> "Error — reintentar" to OtrosDownloadUiStatus.FAILED
        }
        return OtrosOrderHeaderUiModel(
            orderId             = order.orderId,
            routeId             = order.routeId,
            clientName          = order.clientName,
            clientAddress       = order.clientAddress,
            sellerName          = order.sellerName,
            itemsCount          = order.itemsCount,
            downloadStatusLabel = statusLabel,
            downloadStatus      = uiStatus,
            totalFormatted      = order.totalAmount?.let { currencyFormat.format(it) },
            deliveryDate        = order.deliveryDate,
            createdAtFormatted  = dateFormat.format(Date(order.createdAt)),
        )
    }

    private fun mapToPedidoClienteUiModel(pw: PedidoWithItems) = PedidoClienteUiModel(
        pedidoId           = pw.pedido.id,
        clienteNombre      = pw.pedido.clienteSnapshot.nombre,
        totalFormatted     = currencyFormat.format(pw.pedido.total),
        itemCount          = pw.items.size,
        syncStatus         = when (pw.syncStatusLabel) {
            SyncStatusLabel.SYNCED  -> "✓ Sincronizado"
            SyncStatusLabel.PENDING -> "⏳ Pendiente"
            SyncStatusLabel.FAILED  -> "✗ Error"
            SyncStatusLabel.UNKNOWN -> ""
        },
        creadoEnFormatted  = dateFormat.format(Date(pw.pedido.creadoEn)),
        // isEditable = true para todos los estados visibles.
        // La query de Room ya excluye PENDING_DELETE / isDeleted, así que todos los
        // pedidos que llegan aquí son editables por defecto.
        isEditable         = true,
    )
}
