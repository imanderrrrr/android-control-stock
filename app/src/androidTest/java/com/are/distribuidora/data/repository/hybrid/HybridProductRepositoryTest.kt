package com.are.distribuidora.data.repository.hybrid

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.remote.fake.FakeFirestoreProductDataSource
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import com.are.distribuidora.data.storage.ProductImageStorage
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HybridProductRepositoryTest {

    private lateinit var db: DistribuidoraDatabase
    private val imageStorage: ProductImageStorage = mockk()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `syncProducts guarda 450 productos integros y consistentes`() = runBlocking {
        // DADO: data source fake con 450 productos
        val remote = FakeFirestoreProductDataSource()
        val repo = ProductSyncRepositoryImpl(
            remote = remote,
            local = db.productDao(),
            database = db,
            imageStorage = imageStorage,
            pendingUploadDao = db.pendingUploadDao()
        )

        // CUANDO: sincronizamos (remoto -> local) usando nueva interfaz
        val products = repo.fetchRemoteProducts()
        repo.saveLocalProducts(products)

        // ENTONCES: 450 productos en Room
        val count = db.productDao().countAll()
        assertEquals(450, count)

        suspend fun assertProducto(idx: Int) {
            val id = "prod_$idx"
            val p = db.productDao().getById(id) ?: throw AssertionError("Producto $id no encontrado")
            assertEquals("Producto $idx", p.name)
            assertEquals(idx.toDouble(), p.price, 0.0)
            assertEquals(idx, p.stock)
        }

        // Muestra representativa
        assertProducto(1)
        assertProducto(50)
        assertProducto(200)
        assertProducto(450)
    }
}
