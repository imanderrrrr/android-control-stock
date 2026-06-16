package com.are.distribuidora.pendingaccount.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.R
import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.are.distribuidora.data.local.entity.PendingUploadEntity
import com.are.distribuidora.pendingaccount.domain.model.formatQuetzales
import com.are.distribuidora.pendingaccount.presentation.model.AccountActivityUiModel
import com.are.distribuidora.pendingaccount.presentation.model.DueState
import com.are.distribuidora.pendingaccount.presentation.model.PendingAccountUiModel
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import com.are.distribuidora.workers.ImageUploadSyncScheduler
import com.are.distribuidora.workers.PendingAccountSyncScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel del módulo "Cuentas por cobrar" (rediseño Bosque Pro).
 *
 * Fuente única de verdad: la tabla `pending_accounts` (Room). Todas las pantallas
 * (lista, formulario, detalle, actividad) usan una instancia de este VM; como todo
 * deriva de flujos de Room, los cambios se reflejan en cualquier pantalla viva.
 *
 * Las fechas que llegan desde el formulario YA vienen normalizadas a medianoche
 * local (ver PendingAccountFormFragment.normalizeToLocalMidnight), por lo que aquí
 * se almacenan tal cual y "vencida" se calcula contra la medianoche local de hoy.
 */
@HiltViewModel
class PendingAccountsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val pendingAccountDao: PendingAccountDao,
    private val pendingUploadDao: PendingUploadDao,
    private val clientDao: ClientDao,
    private val imageUploadSyncScheduler: ImageUploadSyncScheduler,
    private val pendingAccountSyncScheduler: PendingAccountSyncScheduler,
    private val firebaseStorage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    enum class Filter { ALL, OVERDUE, UPCOMING }

    sealed interface Event {
        data object Created : Event
        data object Updated : Event
        data object Paid : Event
        data object Deleted : Event
        data class Error(val message: String) : Event
    }

    data class Summary(
        val totalFormatted: String = formatQuetzales(0),
        val count: Int = 0,
        val overdueCount: Int = 0,
    )

    data class ChipCounts(val all: Int = 0, val overdue: Int = 0, val upcoming: Int = 0)

    data class ClientOption(val id: String, val name: String)

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    private val _clientsForRoute = MutableStateFlow<List<ClientOption>>(emptyList())
    val clientsForRoute: StateFlow<List<ClientOption>> = _clientsForRoute.asStateFlow()

    private val dateShortFmt = SimpleDateFormat("d MMM", Locale("es", "GT"))
    private val dateLongFmt = SimpleDateFormat("d MMM yyyy", Locale("es", "GT"))

    /** Tick cada 60 s para refrescar el estado "vencida" sin reiniciar la app. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    /**
     * Lista activa cruda, ya mapeada y ordenada (vencidas primero, luego por fecha).
     * Es un StateFlow compartido para que lista/resumen/chips usen una sola suscripción.
     */
    private val rawActive: StateFlow<List<PendingAccountUiModel>> =
        pendingAccountDao.observeAllActive(System.currentTimeMillis())
            .combine(ticker) { list, _ -> list }
            .map { entities ->
                entities.map { toUiModel(it) }
                    .sortedWith(
                        compareByDescending<PendingAccountUiModel> { it.isOverdue }
                            .thenBy { it.dueDateMillis }
                    )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val accounts: StateFlow<List<PendingAccountUiModel>> =
        combine(rawActive, _filter) { list, f ->
            when (f) {
                Filter.ALL -> list
                Filter.OVERDUE -> list.filter { it.isOverdue }
                Filter.UPCOMING -> list.filter { !it.isOverdue }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val summary: StateFlow<Summary> =
        rawActive.map { list ->
            Summary(
                totalFormatted = formatQuetzales(list.sumOf { it.amountCents }),
                count = list.size,
                overdueCount = list.count { it.isOverdue },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), Summary())

    val chipCounts: StateFlow<ChipCounts> =
        rawActive.map { list ->
            ChipCounts(
                all = list.size,
                overdue = list.count { it.isOverdue },
                upcoming = list.count { !it.isOverdue },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ChipCounts())

    val activity: StateFlow<List<AccountActivityUiModel>> =
        pendingAccountDao.observeRecentActivity(System.currentTimeMillis() - THREE_DAYS_MILLIS)
            .map { list -> list.map { toActivity(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        loadRoutes()
        purgeOldActivityLogs()
        pendingAccountSyncScheduler.enqueueSync()
    }

    fun setFilter(filter: Filter) {
        _filter.value = filter
    }

    fun loadRoutes() {
        viewModelScope.launch {
            getRoutesUseCase().onSuccess { _routes.value = it }
        }
    }

    fun loadClientsForRoute(routeId: String) {
        viewModelScope.launch {
            _clientsForRoute.value = clientDao.getInitialClientsByRoute(routeId)
                .map { ClientOption(it.id, it.name) }
        }
    }

    suspend fun getAccount(id: String): PendingAccountEntity? = pendingAccountDao.getById(id)

    fun observeAccount(id: String): Flow<PendingAccountUiModel?> =
        pendingAccountDao.observeById(id).map { entity -> entity?.let { toUiModel(it) } }

    // ── Crear / editar ────────────────────────────────────────────────────────

    fun createPendingAccount(
        routeId: String,
        routeName: String,
        clientId: String,
        clientName: String,
        amountQ: Double,
        /** Path local ya copiado a filesDir (medianoche local ya aplicada a la fecha). */
        invoiceLocalPath: String?,
        dueDateMillis: Long,
        notes: String?,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val accountId = UUID.randomUUID().toString()
            val localFilePath = invoiceLocalPath?.takeIf { File(it).exists() }

            val entity = PendingAccountEntity(
                id = accountId,
                routeId = routeId,
                clientId = clientId,
                clientName = clientName,
                routeName = routeName,
                amountCents = toCents(amountQ),
                invoicePhotoUri = localFilePath,
                invoiceRemoteUrl = null,
                dueDateMillis = dueDateMillis,
                isPaid = false,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE,
            )
            pendingAccountDao.insert(entity)
            if (localFilePath != null) enqueuePhotoUpload(accountId, localFilePath, now)
            pendingAccountSyncScheduler.enqueueSync()
            _events.send(Event.Created)
        }
    }

    fun updatePendingAccount(
        id: String,
        routeId: String,
        routeName: String,
        clientId: String,
        clientName: String,
        amountQ: Double,
        dueDateMillis: Long,
        notes: String?,
        invoicePhotoUri: String?,
        invoiceRemoteUrl: String?,
        isNewPhoto: Boolean,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            pendingAccountDao.updateDetails(
                id = id,
                routeId = routeId,
                clientId = clientId,
                clientName = clientName,
                routeName = routeName,
                amountCents = toCents(amountQ),
                dueDateMillis = dueDateMillis,
                notes = notes?.takeIf { it.isNotBlank() },
                invoicePhotoUri = invoicePhotoUri,
                invoiceRemoteUrl = invoiceRemoteUrl,
                now = now,
            )
            if (isNewPhoto && invoicePhotoUri != null) enqueuePhotoUpload(id, invoicePhotoUri, now)
            pendingAccountSyncScheduler.enqueueSync()
            _events.send(Event.Updated)
        }
    }

    // ── Resolver (pagar / eliminar) ─────────────────────────────────────────────
    // FIX crítico: el cambio de estado local va PRIMERO (es inmediato y es lo que
    // retira la tarjeta). La limpieza de imágenes (llamada de red que puede colgarse
    // ~2 min sin conexión) va al final, en segundo plano, sin bloquear la UI.

    fun markAccountPaid(accountId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val userEmail = firebaseAuth.currentUser?.email ?: "desconocido"
            pendingAccountDao.markPaid(accountId, now, userEmail)
            _events.send(Event.Paid)
            pendingAccountSyncScheduler.enqueueSync()
            launch { cleanupAccountImages(accountId) }
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val userEmail = firebaseAuth.currentUser?.email ?: "desconocido"
            pendingAccountDao.markDeleted(accountId, now, userEmail)
            _events.send(Event.Deleted)
            pendingAccountSyncScheduler.enqueueSync()
            launch { cleanupAccountImages(accountId) }
        }
    }

    private suspend fun enqueuePhotoUpload(accountId: String, localPath: String, now: Long) {
        val pendingUpload = PendingUploadEntity(
            id = UUID.randomUUID().toString(),
            entityType = ENTITY_TYPE_PENDING_ACCOUNT,
            entityId = accountId,
            localUri = localPath,
            storagePath = "pending_accounts/$accountId/invoice.jpg",
            state = "PENDING",
            attemptCount = 0,
            lastError = null,
            createdAt = now,
        )
        pendingUploadDao.insert(pendingUpload)
        imageUploadSyncScheduler.enqueueUploadWorker()
    }

    private fun purgeOldActivityLogs() {
        viewModelScope.launch {
            pendingAccountDao.deleteOldResolvedBefore(System.currentTimeMillis() - THREE_DAYS_MILLIS)
        }
    }

    /**
     * Limpieza best-effort de la imagen de factura. Solo toca Storage si la cuenta
     * tuvo foto: si nunca hubo, evita la llamada de red que podría colgarse offline.
     */
    private suspend fun cleanupAccountImages(accountId: String) {
        try {
            val entity = pendingAccountDao.getById(accountId)
            val localPath = entity?.invoicePhotoUri
            val remoteUrl = entity?.invoiceRemoteUrl
            val hadPhoto = !localPath.isNullOrBlank() || !remoteUrl.isNullOrBlank()

            if (hadPhoto) {
                val storagePath = "pending_accounts/$accountId/invoice.jpg"
                try {
                    firebaseStorage.reference.child(storagePath).delete().await()
                } catch (e: Exception) {
                    Log.d(TAG, "Storage image not found/already deleted: $storagePath (${e.message})")
                }
            }

            if (!localPath.isNullOrBlank()) {
                val f = File(localPath)
                if (f.exists()) f.delete()
            }
            val conventionFile = File(appContext.filesDir, "invoices/$accountId.jpg")
            if (conventionFile.exists()) conventionFile.delete()

            val pendingUpload = pendingUploadDao.findByEntityIdAndType(accountId, ENTITY_TYPE_PENDING_ACCOUNT)
            if (pendingUpload != null) pendingUploadDao.markDone(pendingUpload.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up images for account $accountId", e)
        }
    }

    // ── Mapeo ───────────────────────────────────────────────────────────────────

    private fun toUiModel(e: PendingAccountEntity): PendingAccountUiModel {
        val today = todayStartMillis()
        val diffDays = Math.round((e.dueDateMillis - today).toDouble() / DAY_MILLIS).toInt()
        val dateShort = dateShortFmt.format(e.dueDateMillis)

        val state: DueState
        val label: String
        var banner: String? = null
        when {
            diffDays < 0 -> {
                state = DueState.OVERDUE
                label = appContext.getString(R.string.cxc_due_overdue, dateShort)
                val days = -diffDays
                val daysTxt = if (days == 1) appContext.getString(R.string.cxc_overdue_days_one)
                else appContext.getString(R.string.cxc_overdue_days, days)
                banner = appContext.getString(R.string.cxc_overdue_banner, daysTxt)
            }
            diffDays == 0 -> {
                state = DueState.SOON
                label = appContext.getString(R.string.cxc_due_today)
            }
            diffDays in 1..3 -> {
                state = DueState.SOON
                label = if (diffDays == 1) appContext.getString(R.string.cxc_due_soon_one, dateShort)
                else appContext.getString(R.string.cxc_due_soon, diffDays, dateShort)
            }
            else -> {
                state = DueState.NORMAL
                label = appContext.getString(R.string.cxc_due_normal, dateShort)
            }
        }

        return PendingAccountUiModel(
            id = e.id,
            routeId = e.routeId,
            clientId = e.clientId,
            clientName = e.clientName,
            routeName = e.routeName,
            amountCents = e.amountCents,
            amountFormatted = formatQuetzales(e.amountCents),
            invoicePhotoUri = e.invoicePhotoUri,
            invoiceRemoteUrl = e.invoiceRemoteUrl,
            dueDateMillis = e.dueDateMillis,
            dueLabel = label,
            dueState = state,
            overdueBannerText = banner,
            notes = e.notes,
            createdAtFormatted = dateLongFmt.format(e.createdAt),
        )
    }

    private fun toActivity(e: PendingAccountEntity): AccountActivityUiModel {
        val who = e.resolvedBy ?: appContext.getString(R.string.cxc_someone)
        val paid = e.resolvedAction == "PAID"
        val message = if (paid) appContext.getString(R.string.cxc_activity_paid, who, e.clientName)
        else appContext.getString(R.string.cxc_activity_deleted, who, e.clientName)
        return AccountActivityUiModel(
            id = e.id,
            isPaid = paid,
            message = message,
            amountFormatted = formatQuetzales(e.amountCents),
            resolvedAtMillis = e.resolvedAt ?: e.updatedAt,
        )
    }

    private fun todayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun toCents(amountQ: Double): Long = Math.round(amountQ * 100)

    companion object {
        private const val TAG = "PendingAccounts"
        const val ENTITY_TYPE_PENDING_ACCOUNT = "PENDING_ACCOUNT"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val THREE_DAYS_MILLIS = 3L * DAY_MILLIS
    }
}
