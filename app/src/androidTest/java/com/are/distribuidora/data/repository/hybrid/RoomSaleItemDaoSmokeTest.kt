package com.are.distribuidora.data.repository.hybrid

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.entity.SaleEntity
import com.are.distribuidora.data.local.entity.SaleItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Smoke test para validar que Room persiste y consulta SaleItemEntity por saleId.
 */
class RoomSaleItemDaoSmokeTest {

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
    fun insert_and_query_items_by_saleId_returns_items() = runBlocking {
        val saleId = "1"
        val now = System.currentTimeMillis()

        db.saleDao().insert(
            SaleEntity(
                id = saleId,
                date = now,
                total = 1.0,
            )
        )

        db.saleItemDao().insert(
            SaleItemEntity(
                id = "$saleId-101",
                saleId = saleId,
                productId = "101",
                quantity = 2,
                price = 10.0,
            )
        )

        db.saleItemDao().insert(
            SaleItemEntity(
                id = "$saleId-202",
                saleId = saleId,
                productId = "202",
                quantity = 3,
                price = 5.5,
            )
        )

        val sale = db.saleDao().getById(saleId)
        assertNotNull(sale)

        val items = db.saleItemDao().getBySaleId(saleId)
        assertEquals(2, items.size)
    }
}
