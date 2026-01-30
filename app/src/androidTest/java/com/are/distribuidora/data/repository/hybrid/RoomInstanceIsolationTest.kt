package com.are.distribuidora.data.repository.hybrid

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.entity.SaleEntity
import com.are.distribuidora.data.local.entity.SaleItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifica que dos referencias a db.saleItemDao() consultan la misma DB.
 */
class RoomInstanceIsolationTest {

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
    fun dao_instances_see_same_data() = runBlocking {
        val saleId = "1"
        val now = System.currentTimeMillis()

        db.saleDao().insert(
            SaleEntity(
                id = saleId,
                date = now,
                total = 1.0,
            )
        )

        val daoA = db.saleItemDao()
        val daoB = db.saleItemDao()

        daoA.insert(
            SaleItemEntity(
                id = "$saleId-101",
                saleId = saleId,
                productId = "101",
                quantity = 2,
                price = 10.0,
            )
        )

        assertEquals(1, daoB.getBySaleId(saleId).size)
    }
}
