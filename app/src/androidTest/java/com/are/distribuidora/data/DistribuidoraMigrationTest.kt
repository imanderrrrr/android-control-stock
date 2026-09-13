package com.are.distribuidora.data

import androidx.room.testing.MigrationTestHelper
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

    /**
     * Integración 4.0 + 4.1: la cadena 38 → 39 → 40 que ejecuta un teléfono que viene de 3.0.1.
     *
     * - 38 → 39 (4.0) agrega `screen_access.role`.
     * - 39 → 40 (4.1, renumerada al integrar) crea `stock_movements`, quita `products.comprometido`
     *   y agrega `orders.editVersion`. NO vuelve a crear `role`: ya existe desde la 39.
     *
     * Comprueba además que el stock y los pedidos existentes sobreviven al recreado de `products`.
     */
    @Test
    @Throws(IOException::class)
    fun migrate38To40() {
        helper.createDatabase(TEST_DB, 38).apply {
            execSQL(
                "INSERT INTO products (id, name, price, stock, comprometido, isActive, isDeleted, " +
                    "syncStatus, createdAt, updatedAt) " +
                    "VALUES ('p1', 'Coca Cola', 15.0, 42, 7, 1, 0, 'SYNCED', 1000, 2000)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            40,
            true,
            DistribuidoraMigrations.MIGRATION_38_39,
            DistribuidoraMigrations.MIGRATION_39_40,
        )

        // El stock sobrevive al recreado de la tabla; `comprometido` desapareció.
        db.query("SELECT stock FROM products WHERE id = 'p1'").use { c ->
            check(c.moveToFirst()) { "el producto p1 se perdió en la migración" }
            check(c.getInt(0) == 42) { "stock esperado 42, real ${c.getInt(0)}" }
        }
        db.query("PRAGMA table_info(products)").use { c ->
            val cols = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            check("comprometido" !in cols) { "products aún tiene la columna comprometido" }
        }

        // `role` existe UNA sola vez (la 39→40 no debe volver a crearla).
        db.query("PRAGMA table_info(screen_access)").use { c ->
            val roles = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                .count { it == "role" }
            check(roles == 1) { "screen_access.role aparece $roles veces" }
        }

        // Tabla nueva de 4.1 y columna nueva del pipeline B.
        db.query("SELECT COUNT(*) FROM stock_movements").use { c ->
            check(c.moveToFirst()) { "stock_movements no existe" }
        }
        db.query("PRAGMA table_info(orders)").use { c ->
            val cols = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            check("editVersion" in cols) { "orders no tiene editVersion" }
        }
    }
}
