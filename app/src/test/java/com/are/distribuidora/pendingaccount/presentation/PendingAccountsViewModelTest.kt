package com.are.distribuidora.pendingaccount.presentation

import android.content.Context
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.data.local.dao.PendingAccountDao
import com.are.distribuidora.route.domain.usecase.GetRoutesUseCase
import com.are.distribuidora.screenaccess.domain.model.UserAccess
import com.are.distribuidora.screenaccess.domain.repository.UserAccessProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gates de Cuentas por cobrar (4.1.4): el ViewModel es la segunda barrera después de la UI.
 * - Crear → CREATE_RECEIVABLE: el vendedor SÍ (registra la deuda en la calle).
 * - Editar el monto y borrar → MANAGE_RECEIVABLES: solo admin (equivale a mover dinero).
 * - Cobrar → COLLECT_RECEIVABLE: el vendedor sí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingAccountsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: PendingAccountDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(access: UserAccess): PendingAccountsViewModel {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "Sin permiso"
        val routes = mockk<GetRoutesUseCase>()
        coEvery { routes(any()) } returns Result.Success(emptyList())
        return PendingAccountsViewModel(
            appContext = context,
            getRoutesUseCase = routes,
            pendingAccountDao = dao,
            pendingUploadDao = mockk(relaxed = true),
            clientDao = mockk(relaxed = true),
            imageUploadSyncScheduler = mockk(relaxed = true),
            pendingAccountSyncScheduler = mockk(relaxed = true),
            firebaseStorage = mockk(relaxed = true),
            firebaseAuth = mockk(relaxed = true),
            userAccessProvider = UserAccessProvider { access },
        )
    }

    private fun vendedor() = UserAccess.leastPrivilege()
    private fun admin() = UserAccess.admin()

    private fun PendingAccountsViewModel.create() = createPendingAccount(
        routeId = "r1", routeName = "Ruta 1", clientId = "c1", clientName = "Cliente",
        amountQ = 150.0, invoiceLocalPath = null, dueDateMillis = 1_800_000_000_000L, notes = null,
    )

    private fun PendingAccountsViewModel.update() = updatePendingAccount(
        id = "a1", routeId = "r1", routeName = "Ruta 1", clientId = "c1", clientName = "Cliente",
        amountQ = 1.0, dueDateMillis = 1_800_000_000_000L, notes = null,
        invoicePhotoUri = null, invoiceRemoteUrl = null, isNewPhoto = false,
    )

    // ── crear ───────────────────────────────────────────────────────────────

    @Test
    fun `vendedor crea una cuenta por cobrar`() = runTest {
        val vm = viewModel(vendedor())
        vm.create()
        advanceUntilIdle()

        assertEquals(PendingAccountsViewModel.Event.Created, vm.events.first())
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `admin crea una cuenta por cobrar`() = runTest {
        val vm = viewModel(admin())
        vm.create()
        advanceUntilIdle()

        assertEquals(PendingAccountsViewModel.Event.Created, vm.events.first())
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    // ── editar el monto ─────────────────────────────────────────────────────

    @Test
    fun `vendedor NO edita el monto de una cuenta`() = runTest {
        val vm = viewModel(vendedor())
        vm.update()
        advanceUntilIdle()

        assertTrue(vm.events.first() is PendingAccountsViewModel.Event.Error)
        coVerify(exactly = 0) {
            dao.updateDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `admin edita el monto de una cuenta`() = runTest {
        val vm = viewModel(admin())
        vm.update()
        advanceUntilIdle()

        assertEquals(PendingAccountsViewModel.Event.Updated, vm.events.first())
        coVerify(exactly = 1) {
            dao.updateDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ── borrar ──────────────────────────────────────────────────────────────

    @Test
    fun `vendedor NO borra una cuenta`() = runTest {
        val vm = viewModel(vendedor())
        vm.deleteAccount("a1")
        advanceUntilIdle()

        assertTrue(vm.events.first() is PendingAccountsViewModel.Event.Error)
        coVerify(exactly = 0) { dao.markDeleted(any(), any(), any()) }
    }

    @Test
    fun `admin borra una cuenta`() = runTest {
        val vm = viewModel(admin())
        vm.deleteAccount("a1")
        advanceUntilIdle()

        assertEquals(PendingAccountsViewModel.Event.Deleted, vm.events.first())
        coVerify(exactly = 1) { dao.markDeleted("a1", any(), any()) }
    }

    // ── cobrar ──────────────────────────────────────────────────────────────

    @Test
    fun `vendedor cobra una cuenta`() = runTest {
        val vm = viewModel(vendedor())
        vm.markAccountPaid("a1")
        advanceUntilIdle()

        assertEquals(PendingAccountsViewModel.Event.Paid, vm.events.first())
        coVerify(exactly = 1) { dao.markPaid("a1", any(), any()) }
    }
}
