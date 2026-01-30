package com.are.distribuidora.data.repository

import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductSyncRepositoryImplTest {

    private class FakeProductDao : ProductDao {
        val upserts = mutableListOf<ProductEntity>()

        override suspend fun insert(entity: ProductEntity) = Unit

        override suspend fun upsert(entity: ProductEntity) {
            upserts.add(entity)
        }

        override suspend fun update(entity: ProductEntity) = Unit

        override suspend fun getById(id: String): ProductEntity? = null

        @Suppress("OVERRIDE_DEPRECATION")
        override suspend fun getAll(): List<ProductEntity> = emptyList()

        override fun observeProducts(): Flow<List<ProductEntity>> = emptyFlow()

        override suspend fun delete(entity: ProductEntity) = Unit
    }

    private class FakeRemote(
        private val products: List<ProductRemoteDataSource.RemoteProduct>,
    ) : ProductRemoteDataSource {
        override suspend fun fetchAllProducts(): List<ProductRemoteDataSource.RemoteProduct> = products
    }

    @Test
    fun `no persiste stock negativo`() = runBlocking {
        val dao = FakeProductDao()
        val remote = FakeRemote(
            listOf(
                ProductRemoteDataSource.RemoteProduct(
                    id = "ok",
                    name = "Ok",
                    description = null,
                    category = null,
                    price = 1.0,
                    imageUrl = null,
                    barcode = null,
                    stock = 3,
                    comprometido = null,
                    createdRemoteAt = null,
                    updatedRemoteAt = null,
                ),
                ProductRemoteDataSource.RemoteProduct(
                    id = "bad",
                    name = "Bad",
                    description = null,
                    category = null,
                    price = 1.0,
                    imageUrl = null,
                    barcode = null,
                    stock = -5,
                    comprometido = null,
                    createdRemoteAt = null,
                    updatedRemoteAt = null,
                ),
            ),
        )

        val repo = ProductSyncRepositoryImpl(productDao = dao, remote = remote)
        repo.syncProductsRemoteToLocal()

        assertEquals(1, dao.upserts.size)
        assertEquals("ok", dao.upserts.single().id)
        assertEquals(3, dao.upserts.single().stock)
    }
}
