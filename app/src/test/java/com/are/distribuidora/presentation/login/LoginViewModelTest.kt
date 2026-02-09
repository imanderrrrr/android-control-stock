package com.are.distribuidora.presentation.login

import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.auth.domain.repository.AuthRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.route.domain.repository.RouteSyncRepository
import com.are.distribuidora.route.domain.usecase.DownloadRoutesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitLogin_whenEmptyCredentials_setsError_andDoesNotNavigate() = runTest {
        val vm = LoginViewModel(
            authRepository = FakeAuthRepository(),
            downloadRoutesUseCase = DownloadRoutesUseCase(FakeRouteSyncRepository()),
        )

        vm.onEmailChange("")
        vm.onPasswordChange("")
        vm.submitLogin()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Email y contraseña son obligatorios", state.errorMessage)
        assertFalse(state.navigateToHome)
    }

    @Test
    fun submitLogin_whenSuccess_setsNavigateToHome() = runTest {
        val vm = LoginViewModel(
            authRepository = FakeAuthRepository(loginResult = Result.Success(sampleSession())),
            downloadRoutesUseCase = DownloadRoutesUseCase(FakeRouteSyncRepository()),
        )

        vm.onEmailChange("user@example.com")
        vm.onPasswordChange("pass")
        vm.submitLogin()

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.navigateToHome)

        vm.consumeNavigation()
        assertFalse(vm.uiState.value.navigateToHome)
    }

    @Test
    fun submitLogin_whenError_setsMessage_andDoesNotNavigate() = runTest {
        val vm = LoginViewModel(
            authRepository = FakeAuthRepository(loginResult = Result.Error(Failure.ValidationError("bad credentials"))),
            downloadRoutesUseCase = DownloadRoutesUseCase(FakeRouteSyncRepository()),
        )

        vm.onEmailChange("user@example.com")
        vm.onPasswordChange("wrong")
        vm.submitLogin()

        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("bad credentials", state.errorMessage)
        assertFalse(state.navigateToHome)
    }

    private fun sampleSession() = Session(
        userId = "u1",
        email = "user@example.com",
        authToken = "token",
        lastLoginAt = 1705459200000L,
    )

    private class FakeAuthRepository(
        private val loginResult: Result<Session> = Result.Error(Failure.UnknownError),
    ) : AuthRepository {

        override suspend fun login(email: String, password: String): Result<Session> = loginResult

        override suspend fun logout(): Result<Unit> = Result.Success(Unit)

        override suspend fun getCurrentSession(): Session? = null

        override suspend fun isSessionActive(): Boolean = false
    }

    private class FakeRouteSyncRepository : RouteSyncRepository {
        override suspend fun syncPending(limit: Int) = Unit
        override suspend fun pullRemote() = Unit
    }
}
