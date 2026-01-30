package com.are.distribuidora.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.auth.data.local.DataStoreAuthLocalDataSource
import com.are.distribuidora.auth.data.remote.AuthRemoteDataSource
import com.are.distribuidora.auth.data.repository.OfflineFirstAuthRepository
import com.are.distribuidora.auth.domain.model.Session
import com.are.distribuidora.core.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Integration tests para el contrato offline-first de sesión.
 */
@Ignore(
    "TEST LEGACY: estaba acoplado a SessionManager (removido). " +
        "Migrar a SessionRepository/UseCases antes de re-habilitar."
)
class SessionIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var local: DataStoreAuthLocalDataSource

    @Before
    fun setUp() = runBlocking {
        if (!::dataStore.isInitialized) {
            dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            dataStore = PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = {
                    context.preferencesDataStoreFile("auth_test_session_integration")
                }
            )
        }

        local = DataStoreAuthLocalDataSource(dataStore)
        local.clearSession()
    }

    @After
    fun tearDown() {
        if (::dataStoreScope.isInitialized) {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun S1_sessionPersistsOffline_getCurrentSessionReturnsSession() = runBlocking {
        val session = Session(
            userId = "user-1",
            email = "user1@example.com",
            authToken = null,
            lastLoginAt = 1705459200000L,
        )
        local.saveSession(session)

        val repository = OfflineFirstAuthRepository(
            local = local,
            remote = object : AuthRemoteDataSource {
                override suspend fun login(email: String, password: String) =
                    throw AssertionError("Remote no debe llamarse en S1")
            }
        )

        val current = repository.getCurrentSession()
        assertNotNull(current)
        assertEquals("user-1", current!!.userId)
    }

    @Test
    fun S2_logoutClearsSessionExplicitly_sessionBecomesNull() = runBlocking {
        local.saveSession(
            Session(
                userId = "user-2",
                email = "user2@example.com",
                authToken = "token",
                lastLoginAt = 1705459200000L,
            )
        )

        val repository = OfflineFirstAuthRepository(
            local = local,
            remote = object : AuthRemoteDataSource {
                override suspend fun login(email: String, password: String) =
                    throw AssertionError("Remote no debe llamarse en S2")
            }
        )

        val result = repository.logout()

        assertTrue(result is Result.Success)
        assertNull(repository.getCurrentSession())
    }

    @Test
    fun S3_sessionNotInvalidatedWhenRemoteErrors_existingSessionRemains() = runBlocking {
        local.saveSession(
            Session(
                userId = "user-4",
                email = "user4@example.com",
                authToken = "token-valid",
                lastLoginAt = 1705459200000L,
            )
        )

        val repository = OfflineFirstAuthRepository(
            local = local,
            remote = object : AuthRemoteDataSource {
                override suspend fun login(email: String, password: String) =
                    throw RuntimeException("Simulated remote failure")
            }
        )

        val result = repository.login("user4@example.com", "wrong")

        assertTrue(result is Result.Error)
        assertNotNull(repository.getCurrentSession())
    }
}
