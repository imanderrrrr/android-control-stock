package com.are.distribuidora.client.domain.usecase

import com.are.distribuidora.client.domain.model.Client
import com.are.distribuidora.client.domain.repository.ClientRepository
import com.are.distribuidora.core.result.Failure
import com.are.distribuidora.core.result.Result
import com.are.distribuidora.domain.core.SyncState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateClientUseCaseTest {

    private val repository = mockk<ClientRepository>()
    private val useCase = UpdateClientUseCase(repository)

    private val validClient = Client(
        id = "client-123",
        name = "Juan Perez",
        phone = null,
        address = null,
        latitude = null,
        longitude = null,
        maxOrderAmountInCents = null,
        isActive = true,
        isDeleted = false,
        routeId = "route-1",
        syncState = SyncState.SYNCED,
        createdAt = 1000L,
        updatedAt = 1000L,
        createdBy = "user-1",
        lastModifiedBy = "user-1"
    )

    @Test
    fun `invoke returns Validation error when id is blank`() = runBlocking {
        val client = validClient.copy(id = "")

        val result = useCase(client)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).failure is Failure.ValidationError)
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `invoke returns Validation error when name is blank`() = runBlocking {
        val client = validClient.copy(name = "  ")

        val result = useCase(client)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).failure is Failure.ValidationError)
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `invoke delegates to repository when input is valid`() = runBlocking {
        coEvery { repository.update(validClient) } returns Result.Success(Unit)

        val result = useCase(validClient)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) { repository.update(validClient) }
    }
}
