package com.are.distribuidora.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Migración 38 → 39 (DailyStock 4.1) sobre una base creada con el schema JSON exportado de la v38:
 *  - `products` pierde `comprometido` y conserva el stock;
 *  - aparece `stock_movements` con sus índices;
 *  - `orders.editVersion` y `screen_access.role` con sus defaults;
 *  - Room valida el schema resultante contra el generado (si algo no cuadra, `build()`/primer uso lanza).
 */
@RunWith(RobolectricTestRunner::class)
class Migration38To39Test {

    private val dbName = "migration-38-39-test.db"
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `migra 38 a 39 conservando stock y creando el libro de movimientos`() {
        // 1) Base v38 real, construida con el createSql del schema exportado por Room.
        createV38Database { db ->
            db.execSQL(
                """INSERT INTO products (id, name, description, category, price, imageUrl, imageLocalUri, barcode, stock, comprometido,
                   isActive, isDeleted, syncStatus, createdAt, updatedAt, lastSyncedAt)
                   VALUES ('p1', 'Gaseosa', NULL, 'Bebidas', 12.5, NULL, NULL, '750', 48, 7, 1, 0, 'SYNCED', 1000, 2000, 2000)"""
            )
            db.execSQL(
                """INSERT INTO products (id, name, description, category, price, imageUrl, imageLocalUri, barcode, stock, comprometido,
                   isActive, isDeleted, syncStatus, createdAt, updatedAt, lastSyncedAt)
                   VALUES ('p2', 'Pan', NULL, NULL, 3.0, NULL, NULL, NULL, -2, 0, 1, 0, 'PENDING_UPDATE', 1000, 1500, NULL)"""
            )
            db.execSQL(
                """INSERT INTO orders (orderId, routeId, deliveryDate, clientName, clientAddress, sellerName, itemsCount, itemsDownloaded,
                   totalAmount, downloadStatus, failedReasonCode, failedReasonMessage, failedAttempts, lastAttemptAt, createdAt, updatedAt,
                   vendedorId, isDeleted, pendingUpload)
                   VALUES ('o1', 'r1', '2026-09-08', 'Tienda', NULL, 'Vendedor', 2, 2, 100.0, 'COMPLETED', NULL, NULL, 0, NULL, 1, 1, 'v1', 0, 1)"""
            )
            db.execSQL(
                """INSERT INTO screen_access (uid, inicio, inventario, pedidos, clientes, reportes, cuentasPendientes, updatedAtMillis, updatedBy)
                   VALUES ('u1', NULL, NULL, NULL, NULL, 0, NULL, 5, 'admin@x')"""
            )
        }

        // 2) Abrir con Room v39 + la migración: Room valida el schema tras migrar.
        val room = Room.databaseBuilder(context, DistribuidoraDatabase::class.java, dbName)
            .addMigrations(DistribuidoraMigrations.MIGRATION_38_39)
            .allowMainThreadQueries()
            .build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(39, db.version)

            // products: stock intacto (incluido un negativo), sin comprometido
            db.query("SELECT stock, syncStatus FROM products WHERE id = 'p1'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(48, c.getInt(0)); assertEquals("SYNCED", c.getString(1))
            }
            db.query("SELECT stock FROM products WHERE id = 'p2'").use { c -> assertTrue(c.moveToFirst()); assertEquals(-2, c.getInt(0)) }
            assertFalse("comprometido debe desaparecer", columns(db, "products").contains("comprometido"))
            assertTrue(indexNames(db, "products").contains("index_products_name"))

            // stock_movements + índices
            val mvCols = columns(db, "stock_movements")
            listOf("id", "productId", "productName", "type", "quantity", "reason", "orderId", "note",
                "createdBy", "createdByName", "createdAt", "syncStatus", "lastSyncedAt").forEach {
                assertTrue("falta columna $it", mvCols.contains(it))
            }
            val mvIdx = indexNames(db, "stock_movements")
            assertTrue(mvIdx.containsAll(listOf("index_stock_movements_productId", "index_stock_movements_orderId", "index_stock_movements_syncStatus")))

            // orders.editVersion default 0, pendingUpload conservado
            db.query("SELECT editVersion, pendingUpload FROM orders WHERE orderId = 'o1'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)); assertEquals(1, c.getInt(1))
            }
            // screen_access.role null (→ vendedor), reportes=0 conservado
            db.query("SELECT role, reportes FROM screen_access WHERE uid = 'u1'").use { c ->
                assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)); assertEquals(0, c.getInt(1))
            }

            // El DAO real funciona sobre la base migrada.
            kotlinx.coroutines.runBlocking {
                room.productDao().applyMovement("p1", -3)
                assertEquals(45, room.productDao().getById("p1")!!.stock)
                assertEquals(0, room.stockMovementDao().countPending())
            }
        } finally {
            room.close()
        }
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** Crea la base en v38 ejecutando los `createSql` del schema exportado `schemas/.../38.json`. */
    private fun createV38Database(seed: (SupportSQLiteDatabase) -> Unit) {
        val schemaFile = listOf(
            File("schemas/com.are.distribuidora.data.local.DistribuidoraDatabase/38.json"),
            File("app/schemas/com.are.distribuidora.data.local.DistribuidoraDatabase/38.json"),
        ).first { it.exists() }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val statements = mutableListOf<String>()
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            statements += e.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = e.getJSONArray("indices")
            for (j in 0 until indices.length()) {
                statements += indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) statements += setup.getString(i)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(38) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        statements.forEach { db.execSQL(it) }
                        // Identity hash de la v38 para que Room la reconozca como su propia base.
                        db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.getString("identityHash")}')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        helper.writableDatabase.use { db -> seed(db) }
        helper.close()
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
        }

    private fun indexNames(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA index_list(`$table`)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toSet()
        }
}
