package com.are.distribuidora.data

import androidx.room.migration.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.DistribuidoraMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DistribuidoraMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DistribuidoraDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate12To13() {
        // 1. Create DB in version 12
        var db = helper.createDatabase(TEST_DB, 12).apply {
            // Insert legacy product (schema v12 must match exactly what Room expects for v12)
            // v12 schema for products: id, name, price, stock
            execSQL("INSERT INTO products (id, name, price, stock) VALUES ('p1', 'Coca Cola', 15.0, 100)")
            close()
        }

        // 2. Run migration to 13
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            13,
            true, // validateDroppedTables
            DistribuidoraMigrations.MIGRATION_12_13
        )

        // 3. Verify data preservation and new columns
        val cursor = db.query("SELECT * FROM products WHERE id = 'p1'")
        if (cursor.moveToFirst()) {
            // Check legacy data
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
            val stock = cursor.getInt(cursor.getColumnIndexOrThrow("stock"))
            
            assert(name == "Coca Cola")
            assert(price == 15.0)
            assert(stock == 100)

            // Check new columns exist and are null (default)
            val descIndex = cursor.getColumnIndexOrThrow("description")
            val catIndex = cursor.getColumnIndexOrThrow("category")
            val imgIndex = cursor.getColumnIndexOrThrow("imageUrl")
            val barIndex = cursor.getColumnIndexOrThrow("barcode")
            val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
            val updatedIndex = cursor.getColumnIndexOrThrow("updatedAt")

            assert(cursor.isNull(descIndex))
            assert(cursor.isNull(catIndex))
            assert(cursor.isNull(imgIndex))
            assert(cursor.isNull(barIndex))
            assert(cursor.isNull(createdIndex))
            assert(cursor.isNull(updatedIndex))
        } else {
            throw AssertionError("Product 'p1' lost during migration")
        }
        cursor.close()
    }
    @Test
    @Throws(IOException::class)
    fun migrate14To15() {
        // 1. Create DB in version 14
        var db = helper.createDatabase(TEST_DB, 14).apply {
            // Insert product in v14 schema
            // v14 schema: id, name, description, category, price, imageUrl, barcode, stock, isActive, isDeleted, syncStatus, createdAt, updatedAt, lastSyncedAt
            execSQL("""
                INSERT INTO products (id, name, price, stock, isActive, isDeleted, syncStatus, createdAt, updatedAt) 
                VALUES ('p1', 'Coca Cola', 15.0, 100, 1, 0, 'SYNCED', 1000, 1000)
            """.trimIndent())
            close()
        }

        // 2. Run migration to 15
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            15,
            true, // validateDroppedTables
            DistribuidoraMigrations.MIGRATION_14_15
        )

        // 3. Verify product preservation
        val cursor = db.query("SELECT * FROM products WHERE id = 'p1'")
        if (cursor.moveToFirst()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            assert(name == "Coca Cola")
        } else {
            throw AssertionError("Product 'p1' lost during migration 14->15")
        }
        cursor.close()

        // 4. Verify product_conflicts table exists
        // Try to insert a conflict check FK constraint too (but manual SQL insert might bypass Room entity checks, 
        // however SQLite enforces FK if enabled. Room enables it by default in migration tests usually?)
        // Let's just check if we can insert into it.
        try {
            db.execSQL("""
                INSERT INTO product_conflicts (productId, remoteJson, remoteUpdatedAt, conflictDetectedAt)
                VALUES ('p1', '{}', 2000, 2000)
            """.trimIndent())
        } catch (e: Exception) {
            throw AssertionError("Failed to insert into product_conflicts table. Table might not exist or schema is wrong. Error: ${e.message}")
        }
    }
}
