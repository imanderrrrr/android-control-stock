package com.are.distribuidora.screenaccess.data

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.roles.domain.Permission
import com.are.distribuidora.roles.domain.Role
import com.are.distribuidora.screenaccess.data.local.dao.ScreenAccessDao
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import com.are.distribuidora.screenaccess.data.remote.ScreenAccessRemoteDataSource
import com.are.distribuidora.screenaccess.data.repository.ScreenAccessRepositoryImpl
import com.are.distribuidora.screenaccess.domain.model.AppScreen
import com.are.distribuidora.screenaccess.domain.model.UserAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenAccessRepositoryImplTest {

    private class FakeDao : ScreenAccessDao {
        val rows = MutableStateFlow<Map<String, ScreenAccessEntity>>(emptyMap())
        override fun observeByUid(uid: String): Flow<ScreenAccessEntity?> = rows.map { it[uid] }
        override suspend fun getByUid(uid: String): ScreenAccessEntity? = rows.value[uid]
        override suspend fun upsert(entity: ScreenAccessEntity) { rows.value = rows.value + (entity.uid to entity) }
        override suspend fun deleteByUid(uid: String) { rows.value = rows.value - uid }
        override suspend fun deleteAll() { rows.value = emptyMap() }
    }

    private class FakeRemote(
        private val entity: ScreenAccessEntity?,
        private val fails: Boolean = false,
    ) : ScreenAccessRemoteDataSource {
        override fun observe(uid: String): Flow<ScreenAccessEntity> = flow {
            if (fails) throw IllegalStateException("sin red")
            entity?.let { emit(it) }
        }
        override suspend fun fetchOnce(uid: String): ScreenAccessEntity {
            if (fails) throw IllegalStateException("sin red")
            return entity ?: error("sin doc")
        }
    }

    private fun uid(value: String?) = object : CurrentUserIdProvider { override fun get() = value }

    private fun entity(uid: String, role: String?, pedidos: Boolean? = null) = ScreenAccessEntity(
        uid = uid, inicio = null, inventario = null, pedidos = pedidos, clientes = null,
        reportes = null, cuentasPendientes = null, updatedAtMillis = null, updatedBy = null, role = role,
    )

    @Test
    fun `sin documento remoto ni cache es vendedor`() = runTest {
        val dao = FakeDao()
        // Documento ausente: el remoto emite una entidad "vacía" (todo null), como hace Firestore.
        val repo = ScreenAccessRepositoryImpl(dao, FakeRemote(entity("u1", role = null)), uid("u1"))

        val emissions = repo.observeUserAccess().take(2).toList()

        assertEquals(Role.VENDEDOR, emissions.first().role)
        assertEquals(Role.VENDEDOR, emissions.last().role)
        assertFalse(emissions.last().can(Permission.VIEW_REPORTS))
        assertEquals(UserAccess.leastPrivilege(), emissions.first())
    }

    @Test
    fun `sin sesion es vendedor`() = runTest {
        val repo = ScreenAccessRepositoryImpl(FakeDao(), FakeRemote(null), uid(null))
        assertEquals(UserAccess.leastPrivilege(), repo.observeUserAccess().first())
        assertEquals(UserAccess.leastPrivilege(), repo.current())
    }

    @Test
    fun `rol admin remoto llega a la cache y a la UI`() = runTest {
        val dao = FakeDao()
        val repo = ScreenAccessRepositoryImpl(dao, FakeRemote(entity("u1", role = "admin")), uid("u1"))

        val last = repo.observeUserAccess().take(2).toList().last()

        assertEquals(Role.ADMIN, last.role)
        assertTrue(last.can(Permission.VIEW_REPORTS))
        assertEquals("admin", dao.rows.value["u1"]?.role)
    }

    @Test
    fun `screens pedidos=false sobre admin bloquea la pestaña sin quitar permisos`() = runTest {
        val dao = FakeDao()
        val repo = ScreenAccessRepositoryImpl(
            dao, FakeRemote(entity("u1", role = "admin", pedidos = false)), uid("u1"),
        )

        val last = repo.observeUserAccess().take(2).toList().last()

        assertFalse(last.isAllowed(AppScreen.PEDIDOS))
        assertTrue(last.can(Permission.EDIT_ANY_ORDER))
        assertTrue(last.can(Permission.CREATE_ORDER))
        assertTrue(last.isAllowed(AppScreen.REPORTES))
    }

    @Test
    fun `sin red se sirve la cache y refresh devuelve false`() = runTest {
        val dao = FakeDao().apply { upsert(entity("u1", role = "admin")) }
        val repo = ScreenAccessRepositoryImpl(dao, FakeRemote(null, fails = true), uid("u1"))

        assertEquals(Role.ADMIN, repo.observeUserAccess().first().role)
        assertEquals(Role.ADMIN, repo.current().role)
        assertFalse(repo.refreshFromRemote())
        assertEquals("admin", dao.rows.value["u1"]?.role)
    }

    @Test
    fun `refreshFromRemote cachea el rol y clearCache lo borra`() = runTest {
        val dao = FakeDao()
        val repo = ScreenAccessRepositoryImpl(dao, FakeRemote(entity("u1", role = "admin")), uid("u1"))

        assertTrue(repo.refreshFromRemote())
        assertEquals(Role.ADMIN, repo.current().role)

        repo.clearCache("u1")
        assertEquals(Role.VENDEDOR, repo.current().role)
    }
}
