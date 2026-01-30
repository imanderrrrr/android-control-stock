package com.are.distribuidora.data.repository.hybrid

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.remote.fake.FakeFirestoreProductDataSource
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HybridProductRepositoryTest {

    private lateinit var db: DistribuidoraDatabase

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
        val repo = ProductSyncRepositoryImpl(db.productDao(), remote)

        // CUANDO: sincronizamos (remoto -> local)
        repo.syncProductsRemoteToLocal()

        // ENTONCES: 450 productos en Room
        val all = db.productDao().observeProducts().first()
        assertEquals(450, all.size)

        fun assertProducto(idx: Int) {
            val id = "prod_$idx"
            val p = all.first { it.id == id }
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
