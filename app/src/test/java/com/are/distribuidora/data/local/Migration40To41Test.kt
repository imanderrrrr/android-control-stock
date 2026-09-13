package com.are.distribuidora.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migración 40 → 41: tablas del borrador del pedido.
 *
 * Comprueba que el SQL de la migración produce EXACTAMENTE el esquema que Room espera
 * de las entidades. Es la comprobación que importa: si divergen, Room aborta al abrir
 * la base con "Migration didn't properly handle" y la app no arranca para nadie que
 * actualice desde la 4.1.0.
 */
@RunWith(RobolectricTestRunner::class)
class Migration40To41Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(40) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME)
    }

    @Test
    fun `crea pedido_draft con las columnas y la clave primaria esperadas`() {
        DistribuidoraMigrations.MIGRATION_40_41.migrate(db)

        val columns = columnsOf("pedido_draft")
        assertEquals(
            mapOf(
                "vendedorId" to "TEXT",
                "routeId" to "TEXT",
                "clienteId" to "TEXT",
                "clienteNombre" to "TEXT",
                "clienteTelefono" to "TEXT",
                "clienteDireccion" to "TEXT",
                "clienteEsTemporal" to "INTEGER",
                "deliveryDate" to "TEXT",
                "ivaEnabled" to "INTEGER",
                "updatedAt" to "INTEGER",
            ),
            columns,
        )
        // La PK por vendedorId es lo que garantiza "un solo borrador por vendedor".
        assertEquals(listOf("vendedorId"), primaryKeyOf("pedido_draft"))
        assertEquals(
            setOf("vendedorId", "routeId", "clienteNombre", "clienteEsTemporal", "deliveryDate", "ivaEnabled", "updatedAt"),
            notNullColumnsOf("pedido_draft"),
        )
    }

    @Test
    fun `crea pedido_draft_items con PK compuesta, FK en cascada e indice`() {
        DistribuidoraMigrations.MIGRATION_40_41.migrate(db)

        assertEquals(
            mapOf(
                "vendedorId" to "TEXT",
                "productoId" to "TEXT",
                "nombre" to "TEXT",
                "precioUnitario" to "REAL",
                "cantidad" to "INTEGER",
                "descuentoAmount" to "REAL",
                "descuentoPercent" to "REAL",
                "descuentoType" to "TEXT",
                "notes" to "TEXT",
                "category" to "TEXT",
                "imageUrl" to "TEXT",
                "barcode" to "TEXT",
            ),
            columnsOf("pedido_draft_items"),
        )
        assertEquals(listOf("vendedorId", "productoId"), primaryKeyOf("pedido_draft_items"))

        db.query("PRAGMA foreign_key_list(`pedido_draft_items`)").use { c ->
            assertTrue("debe declarar una foreign key", c.moveToFirst())
            assertEquals("pedido_draft", c.getString(c.getColumnIndexOrThrow("table")))
            assertEquals("CASCADE", c.getString(c.getColumnIndexOrThrow("on_delete")))
        }

        val indices = mutableSetOf<String>()
        db.query("PRAGMA index_list(`pedido_draft_items`)").use { c ->
            while (c.moveToNext()) indices += c.getString(c.getColumnIndexOrThrow("name"))
        }
        assertTrue(
            "falta el índice por vendedorId, index_list=$indices",
            indices.contains("index_pedido_draft_items_vendedorId"),
        )
    }

    @Test
    fun `la migracion es aditiva y acepta datos del borrador`() {
        DistribuidoraMigrations.MIGRATION_40_41.migrate(db)

        db.execSQL(
            "INSERT INTO pedido_draft VALUES " +
                "('vend-1','ruta-1','cli-1','Tienda Ana',NULL,NULL,0,'2026-09-13',1,123)"
        )
        db.execSQL(
            "INSERT INTO pedido_draft_items VALUES " +
                "('vend-1','p1','Coca 600',10.5,3,2.5,0.0,'AMOUNT',NULL,NULL,NULL,NULL)"
        )

        db.query("SELECT clienteNombre FROM pedido_draft WHERE vendedorId='vend-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Tienda Ana", c.getString(0))
        }
        db.query("SELECT cantidad FROM pedido_draft_items WHERE productoId='p1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
        }
    }

    @Test
    fun `borrar la cabecera arrastra sus items`() {
        DistribuidoraMigrations.MIGRATION_40_41.migrate(db)
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL(
            "INSERT INTO pedido_draft VALUES " +
                "('vend-1','ruta-1','cli-1','Tienda Ana',NULL,NULL,0,'2026-09-13',0,123)"
        )
        db.execSQL(
            "INSERT INTO pedido_draft_items VALUES " +
                "('vend-1','p1','Coca 600',10.5,3,0.0,0.0,'PERCENTAGE',NULL,NULL,NULL,NULL)"
        )

        db.execSQL("DELETE FROM pedido_draft WHERE vendedorId='vend-1'")

        db.query("SELECT COUNT(*) FROM pedido_draft_items").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }


    @Test
    fun `el SQL de la migracion coincide con el esquema que Room crea desde las entidades`() {
        // Esta es la comprobación que de verdad protege el arranque: Room valida el
        // esquema al abrir la base y aborta si la migración dejó algo distinto de lo
        // que describen las entidades. Aquí se comparan las dos definiciones reales.
        DistribuidoraMigrations.MIGRATION_40_41.migrate(db)
        val migrated = tableSqlOf(db)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val roomDb = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val expected = try {
            // Fuerza la creación real de las tablas antes de leer sqlite_master.
            roomDb.openHelper.writableDatabase.let { tableSqlOf(it) }
        } finally {
            roomDb.close()
        }

        assertEquals(expected["pedido_draft"], migrated["pedido_draft"])
        assertEquals(expected["pedido_draft_items"], migrated["pedido_draft_items"])
        assertEquals(
            expected["index_pedido_draft_items_vendedorId"],
            migrated["index_pedido_draft_items_vendedorId"],
        )
    }

    /**
     * SQL de creación de las tablas/índices del borrador, normalizado.
     *
     * SQLite guarda el texto literal del CREATE TABLE, así que la migración (SQL
     * multilínea) y Room (una sola línea) difieren solo en espacios. Se colapsan
     * los blancos porque lo que debe coincidir es la definición, no el formato —
     * que es justo lo que compara el validador de esquema de Room.
     */
    private fun tableSqlOf(database: SupportSQLiteDatabase): Map<String, String> {
        val result = mutableMapOf<String, String>()
        database.query(
            "SELECT name, sql FROM sqlite_master WHERE name LIKE 'pedido_draft%' " +
                "OR name LIKE 'index_pedido_draft%'"
        ).use { c ->
            while (c.moveToNext()) {
                val sql = c.getString(1) ?: continue
                result[c.getString(0)] = sql
                    .replace(Regex("\\s+"), " ")
                    .replace("( ", "(")
                    .replace(" )", ")")
                    .trim()
            }
        }
        return result
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun columnsOf(table: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                result[c.getString(c.getColumnIndexOrThrow("name"))] =
                    c.getString(c.getColumnIndexOrThrow("type"))
            }
        }
        return result
    }

    private fun primaryKeyOf(table: String): List<String> {
        val pk = mutableListOf<Pair<Int, String>>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                val order = c.getInt(c.getColumnIndexOrThrow("pk"))
                if (order > 0) pk += order to c.getString(c.getColumnIndexOrThrow("name"))
            }
        }
        return pk.sortedBy { it.first }.map { it.second }
    }

    private fun notNullColumnsOf(table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) {
                if (c.getInt(c.getColumnIndexOrThrow("notnull")) == 1) {
                    result += c.getString(c.getColumnIndexOrThrow("name"))
                }
            }
        }
        return result
    }

    private companion object {
        const val DB_NAME = "migration-40-41-test.db"
    }
}
