package com.are.distribuidora.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.are.distribuidora.client.data.local.dao.ClientDao
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.data.local.dao.PendingUploadDao
import com.are.distribuidora.data.local.entity.PendingAccountEntity
import com.are.distribuidora.data.local.entity.PendingUploadEntity
import com.are.distribuidora.presentation.home.model.AccountActivityUiModel
import com.are.distribuidora.presentation.home.model.PendingAccountUiModel
import com.are.distribuidora.route.domain.model.Route
import com.are.distribuidora.route.domain.usecase.CreateRouteUseCase
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import com.are.distribuidora.workers.ImageUploadSyncScheduler
import com.are.distribuidora.workers.PendingAccountSyncScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ClientsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val createRouteUseCase: CreateRouteUseCase,
    private val getRoutesUseCase: GetRoutesUseCase,
    private val pendingAccountDao: PendingAccountDao,
    private val pendingUploadDao: PendingUploadDao,
    private val clientDao: ClientDao,
    private val imageUploadSyncScheduler: ImageUploadSyncScheduler,
    private val pendingAccountSyncScheduler: PendingAccountSyncScheduler,
    private val firebaseStorage: FirebaseStorage,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    sealed interface Event {
        data object RouteCreated : Event
        data object AccountCreated : Event
        data object AccountMarkedPaid : Event
        data object AccountDeleted : Event
        data class Error(val message: String) : Event
    }

    private val _events = Channel<Event>()
    val events = _events.receiveAsFlow()

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    private val _pendingAccounts = MutableStateFlow<List<PendingAccountUiModel>>(emptyList())
    val pendingAccounts: StateFlow<List<PendingAccountUiModel>> = _pendingAccounts.asStateFlow()

    private val _recentActivity = MutableStateFlow<List<AccountActivityUiModel>>(emptyList())
    val recentActivity: StateFlow<List<AccountActivityUiModel>> = _recentActivity.asStateFlow()

    /** Clientes de una ruta (para el diálogo de selección). */
    private val _clientsForRoute = MutableStateFlow<List<ClientPickerItem>>(emptyList())
    val clientsForRoute: StateFlow<List<ClientPickerItem>> = _clientsForRoute.asStateFlow()

    @Suppress("DEPRECATION")
    private val currencyFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }
    @Suppress("DEPRECATION")
    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("es", "GT"))

    init {
        loadRoutes()
        purgeOldActivityLogs()
        observePendingAccounts()
        observeRecentActivity()
        // Trigger sync on init
        pendingAccountSyncScheduler.enqueueSync()
    }

    fun loadRoutes() {
        viewModelScope.launch {
            getRoutesUseCase().onSuccess { fetchedRoutes ->
                _routes.value = fetchedRoutes
            }
        }
    }

    fun createRoute(name: String, deliveryDay: Int) {
        viewModelScope.launch {
            val newRoute = Route(
                id = UUID.randomUUID().toString(),
                name = name,
                deliveryDay = deliveryDay,
                clientsCount = 0,
                synced = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            createRouteUseCase(newRoute)
                .onSuccess {
                    _events.send(Event.RouteCreated)
                    loadRoutes()
                }
                .onError { _events.send(Event.Error("Error al crear ruta")) }
        }
    }

    // ── Cuentas Pendientes ────────────────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePendingAccounts() {
        viewModelScope.launch {
            // Ticker que emite cada 60s para re-evaluar cuentas vencidas sin reiniciar la app
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(60_000L)
                }
            }.flatMapLatest { nowMillis ->
                pendingAccountDao.observeAllActive(nowMillis)
            }.collect { entities ->
                val todayStart = todayStartMillis()
                _pendingAccounts.value = entities.map { e ->
                    PendingAccountUiModel(
                        id = e.id,
                        routeId = e.routeId,
                        clientId = e.clientId,
                        clientName = e.clientName,
                        routeName = e.routeName,
                        amountFormatted = currencyFormat.format(e.amountCents / 100.0),
                        amountCents = e.amountCents,
                        invoicePhotoUri = e.invoicePhotoUri,
                        invoiceRemoteUrl = e.invoiceRemoteUrl,
                        dueDateFormatted = dateFormat.format(e.dueDateMillis),
                        dueDateMillis = e.dueDateMillis,
                        isOverdue = e.dueDateMillis < todayStart,
                        isPaid = e.isPaid,
                        notes = e.notes,
                    )
                }
            }
        }
    }

    private fun observeRecentActivity() {
        viewModelScope.launch {
            // Mostrar actividad de los últimos 3 días
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            pendingAccountDao.observeRecentActivity(threeDaysAgo).collect { entities ->
                _recentActivity.value = entities.map { e ->
                    val actionText = when (e.resolvedAction) {
                        "PAID" -> "marcó como pagada"
                        "DELETED" -> "eliminó"
                        else -> "actualizó"
                    }
                    AccountActivityUiModel(
                        id = e.id,
                        message = "${e.resolvedBy ?: "Alguien"} $actionText la cuenta de ${e.clientName} de la ruta ${e.routeName}",
                        actionType = e.resolvedAction ?: "",
                        amountFormatted = currencyFormat.format(e.amountCents / 100.0),
                        resolvedAt = e.resolvedAt ?: e.updatedAt,
                    )
                }
            }
        }
    }

    /**
     * Elimina físicamente los registros de actividad (pagados/eliminados)
     * con más de 3 días de antigüedad. Se llama una vez al iniciar el ViewModel.
     */
    private fun purgeOldActivityLogs() {
        viewModelScope.launch {
            val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
            pendingAccountDao.deleteOldResolvedBefore(threeDaysAgo)
        }
    }

    fun loadClientsForRoute(routeId: String) {
        viewModelScope.launch {
            val clients = clientDao.getInitialClientsByRoute(routeId)
            _clientsForRoute.value = clients.map {
                ClientPickerItem(id = it.id, name = it.name)
            }
        }
    }

    fun createPendingAccount(
        routeId: String,
        routeName: String,
        clientId: String,
        clientName: String,
        amountQ: Double,
        /** Path absoluto al archivo de imagen ya copiado en filesDir por el Fragment. Null si no hay imagen. */
        invoiceLocalPath: String?,
        dueDateMillis: Long,
        notes: String?,
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val accountId = UUID.randomUUID().toString()

            // La imagen ya fue copiada al filesDir por el Fragment antes de llamar aquí.
            // Verificamos que el archivo exista; si no, descartamos la ruta para evitar referencias rotas.
            val localFilePath: String? = invoiceLocalPath?.takeIf { path ->
                val f = java.io.File(path)
                if (!f.exists()) {
                    Log.w(TAG, "Invoice local file not found, ignoring: $path")
                    false
                } else true
            }

            val entity = PendingAccountEntity(
                id = accountId,
                routeId = routeId,
                clientId = clientId,
                clientName = clientName,
                routeName = routeName,
                amountCents = (amountQ * 100).toLong(),
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

            // Encolar subida de foto a Firebase Storage si hay foto
            if (localFilePath != null) {
                val storagePath = "pending_accounts/$accountId/invoice.jpg"
                val pendingUpload = PendingUploadEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = ENTITY_TYPE_PENDING_ACCOUNT,
                    entityId = accountId,
                    localUri = localFilePath,
                    storagePath = storagePath,
                    state = "PENDING",
                    attemptCount = 0,
                    lastError = null,
                    createdAt = now,
                )
                pendingUploadDao.insert(pendingUpload)
                imageUploadSyncScheduler.enqueueUploadWorker()
                Log.d(TAG, "Invoice upload enqueued: accountId=$accountId storagePath=$storagePath")
            }

            // Encolar sync con Firestore
            pendingAccountSyncScheduler.enqueueSync()

            _events.send(Event.AccountCreated)
        }
    }

    fun markAccountPaid(accountId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val userEmail = firebaseAuth.currentUser?.email ?: "desconocido"

            // Limpiar imágenes
            cleanupAccountImages(accountId)

            // Marcar como pagada con quién lo hizo
            pendingAccountDao.markPaid(accountId, now, userEmail)

            // Encolar sync
            pendingAccountSyncScheduler.enqueueSync()

            _events.send(Event.AccountMarkedPaid)
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val userEmail = firebaseAuth.currentUser?.email ?: "desconocido"

            // Limpiar imágenes
            cleanupAccountImages(accountId)

            // Soft-delete con quién lo hizo
            pendingAccountDao.markDeleted(accountId, now, userEmail)

            // Encolar sync
            pendingAccountSyncScheduler.enqueueSync()

            _events.send(Event.AccountDeleted)
        }
    }

    /**
     * Elimina la imagen de factura asociada a una cuenta pendiente de:
     * 1. Firebase Storage (pending_accounts/{id}/invoice.jpg)
     * 2. Archivo local (filesDir/invoices/{id}.jpg)
     * 3. Registro en pending_uploads (si existe)
     */
    private suspend fun cleanupAccountImages(accountId: String) {
        try {
            val entity = pendingAccountDao.getById(accountId)

            // 1. Eliminar de Firebase Storage
            val storagePath = "pending_accounts/$accountId/invoice.jpg"
            try {
                firebaseStorage.reference.child(storagePath).delete().await()
                Log.d(TAG, "Storage image deleted: $storagePath")
            } catch (e: Exception) {
                Log.d(TAG, "Storage image not found or already deleted: $storagePath (${e.message})")
            }

            // 2. Eliminar archivo local
            val localPath = entity?.invoicePhotoUri
            if (localPath != null) {
                val localFile = java.io.File(localPath)
                if (localFile.exists()) {
                    localFile.delete()
                    Log.d(TAG, "Local invoice file deleted: $localPath")
                }
            }
            val conventionFile = java.io.File(appContext.filesDir, "invoices/$accountId.jpg")
            if (conventionFile.exists()) {
                conventionFile.delete()
                Log.d(TAG, "Local invoice file (convention) deleted: ${conventionFile.absolutePath}")
            }

            // 3. Eliminar registro de pending_uploads
            val pendingUpload = pendingUploadDao.findByEntityIdAndType(accountId, ENTITY_TYPE_PENDING_ACCOUNT)
            if (pendingUpload != null) {
                pendingUploadDao.markDone(pendingUpload.id)
                Log.d(TAG, "Pending upload record cleaned: ${pendingUpload.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up images for account $accountId", e)
        }
    }

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val TAG = "PendingAccounts"
        const val ENTITY_TYPE_PENDING_ACCOUNT = "PENDING_ACCOUNT"
    }
}

data class ClientPickerItem(
    val id: String,
    val name: String,
)
