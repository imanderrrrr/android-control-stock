package com.are.distribuidora.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migraciones de Room para mantener compatibilidad con bases existentes.
 */
object DistribuidoraMigrations {

    /**
     * v1 -> v2
     * - Agrega snapshot mínimo del cliente en ventas (sales):
     *   - clientName (NOT NULL)
     *   - clientAddress (NULL)
     *
     * Para ventas legacy, clientName se inicializa con un valor razonable.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // NOT NULL requiere DEFAULT para filas ya existentes.
            db.execSQL(
                "ALTER TABLE sales ADD COLUMN clientName TEXT NOT NULL DEFAULT 'Cliente sin nombre'"
            )
            db.execSQL(
                "ALTER TABLE sales ADD COLUMN clientAddress TEXT"
            )
        }
    }

    /**
     * v2 -> v3
     * - Agrega tablas del módulo orders:
     *   - orders (cabecera + estado de descarga local)
     *   - order_items (items finales)
     *   - order_items_staging (items temporales para validación)
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS orders (
                    orderId TEXT NOT NULL PRIMARY KEY,
                    routeId TEXT NOT NULL,
                    deliveryDate TEXT NOT NULL,
                    clientName TEXT NOT NULL,
                    clientAddress TEXT,
                    sellerName TEXT,
                    itemsCount INTEGER NOT NULL,
                    itemsDownloaded INTEGER NOT NULL DEFAULT 0,
                    totalAmount REAL,
                    downloadStatus TEXT NOT NULL,
                    failedReasonCode TEXT,
                    failedReasonMessage TEXT,
                    failedAttempts INTEGER NOT NULL DEFAULT 0,
                    lastAttemptAt INTEGER,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_routeId ON orders(routeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_deliveryDate ON orders(deliveryDate)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS order_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    orderId TEXT NOT NULL,
                    productId TEXT NOT NULL,
                    productName TEXT NOT NULL,
                    unitPrice REAL NOT NULL,
                    quantity INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_items_orderId ON order_items(orderId)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS order_items_staging (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    orderId TEXT NOT NULL,
                    productId TEXT NOT NULL,
                    productName TEXT NOT NULL,
                    unitPrice REAL NOT NULL,
                    quantity INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_items_staging_orderId ON order_items_staging(orderId)")
        }
    }

    /**
     * v3 -> v4
     * - Reconstruye tabla products para alinearla con el ProductEntity actual.
     *
     * Motivo:
     * En versiones anteriores la tabla products contenía más columnas (description, category,
     * imageUrl, etc.). Actualmente el entity se simplificó a:
     *   - id (PK)
     *   - name
     *   - price
     *   - stock
     *
     * Room verifica el identityHash del schema al abrir la DB. Si no migramos, la app crashea.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Creamos tabla temporal con el schema nuevo.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS products_new (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    price REAL NOT NULL,
                    stock INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            // 2) Copiamos datos desde products existente.
            //    price/stock pueden no existir o ser NULL en esquemas legacy; normalizamos.
            db.execSQL(
                """
                INSERT INTO products_new (id, name, price, stock)
                SELECT
                    id,
                    name,
                    COALESCE(price, 0.0) AS price,
                    COALESCE(stock, 0) AS stock
                FROM products
                """.trimIndent()
            )

            // 3) Reemplazamos tabla y recreamos índices requeridos por el entity.
            db.execSQL("DROP TABLE products")
            db.execSQL("ALTER TABLE products_new RENAME TO products")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
        }
    }

    /**
     * v4 -> v5
     * - Reconstruye tabla sales para alinearla con el SaleEntity actual:
     *   - id (PK)
     *   - date (INTEGER NOT NULL)
     *   - total (REAL NOT NULL)
     *
     * Algunos dispositivos vienen de un esquema legacy donde sales tenía columnas adicionales
     * (sellerId, clientName, subtotal, discount, syncStatus, etc.). Room valida el schema
     * tras la migración, y si sales no coincide exactamente, lanza IllegalStateException.
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Creamos tabla nueva con el schema esperado.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sales_new (
                    id TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    total REAL NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )

            // Copiamos datos. Si existen columnas, las usamos; si no, usamos defaults seguros.
            // Nota: En SQLite, seleccionar una columna inexistente falla. Por eso detectamos
            // columnas presentes con PRAGMA table_info.
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`sales`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }

            val hasDate = columns.contains("date")
            val hasTotal = columns.contains("total")
            val hasSubtotal = columns.contains("subtotal")
            val hasDiscount = columns.contains("discount")

            val dateExpr = if (hasDate) "date" else "0"
            val totalExpr = when {
                hasTotal -> "total"
                // Si no hay total pero sí subtotal/discount legacy: total = subtotal - discount
                hasSubtotal && hasDiscount -> "(COALESCE(subtotal, 0.0) - COALESCE(discount, 0.0))"
                hasSubtotal -> "COALESCE(subtotal, 0.0)"
                else -> "0.0"
            }

            db.execSQL(
                """
                INSERT INTO sales_new (id, date, total)
                SELECT id, $dateExpr AS date, $totalExpr AS total
                FROM sales
                """.trimIndent()
            )

            // Reemplazamos tabla.
            db.execSQL("DROP TABLE sales")
            db.execSQL("ALTER TABLE sales_new RENAME TO sales")

            // Índices: el schema esperado no define índices para sales. No creamos ninguno.
        }
    }

    /**
     * v5 -> v6
     * - Reconstruye tabla sale_items para alinearla con SaleItemEntity actual:
     *   - id (PK)
     *   - saleId (FK -> sales.id ON DELETE CASCADE)
     *   - productId
     *   - quantity
     *   - price
     *
     * En esquemas legacy, sale_items tenía columnas extra (productName, unitPrice, itemDiscount,
     * itemTotal, syncStatus, createdAt, updatedAt). Room requiere coincidencia exacta tras migrar.
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Creamos tabla nueva con FK e índices tal como lo define el entity actual.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sale_items_new (
                    id TEXT NOT NULL,
                    saleId TEXT NOT NULL,
                    productId TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    price REAL NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // 2) Detectamos columnas disponibles en sale_items actual.
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`sale_items`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }

            val hasPrice = columns.contains("price")
            val hasUnitPrice = columns.contains("unitPrice")

            val priceExpr = when {
                hasPrice -> "price"
                hasUnitPrice -> "unitPrice"
                else -> "0.0"
            }

            db.execSQL(
                """
                INSERT INTO sale_items_new (id, saleId, productId, quantity, price)
                SELECT
                    id,
                    saleId,
                    productId,
                    quantity,
                    $priceExpr AS price
                FROM sale_items
                """.trimIndent()
            )

            // 3) Reemplazamos tabla e índices.
            db.execSQL("DROP TABLE sale_items")
            db.execSQL("ALTER TABLE sale_items_new RENAME TO sale_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_productId ON sale_items(productId)")
        }
    }

    /**
     * v6 -> v7
     * - Agrega catálogo local de rutas.
     * - Agrega routeId nullable a clients para modelar relación 1:N (ruta -> clientes).
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS routes (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    deliveryDay INTEGER NOT NULL,
                    synced INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_routes_name ON routes(name)")

            // clients.routeId (nullable)
            db.execSQL("ALTER TABLE clients ADD COLUMN routeId TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_routeId ON clients(routeId)")
        }
    }

    /**
     * v7 -> v8
     * - En versiones actuales, routes ya incluye la columna synced desde la creación inicial.
     *   Por eso esta migración no debe intentar agregarla de nuevo.
     *
     * Actualización: algunos esquemas legacy crearon `routes` con `synced INTEGER NOT NULL DEFAULT 1`
     * y un índice `index_routes_name`. El Entity actual NO define ese DEFAULT ni el índice.
     * Para evitar el crash de validación de Room reconstruimos la tabla routes siguiendo
     * el schema del Entity y migramos los datos sin pérdida.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Crear tabla temporal con el schema EXACTO esperado por el Entity (sin DEFAULT y sin índices).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS routes_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    deliveryDay INTEGER NOT NULL,
                    synced INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // 2) Copiar todos los datos desde la tabla actual routes.
            //    Usamos COALESCE(synced, 0) por seguridad si existiera NULL inesperado.
            db.execSQL(
                """
                INSERT INTO routes_new (id, name, deliveryDay, synced, createdAt, updatedAt)
                SELECT id, name, deliveryDay, COALESCE(synced, 0) AS synced, createdAt, updatedAt FROM routes
                """.trimIndent()
            )

            // 3) Eliminar índice que no forma parte del Entity.
            db.execSQL("DROP INDEX IF EXISTS index_routes_name")

            // 4) Reemplazar la tabla antigua por la nueva.
            db.execSQL("DROP TABLE IF EXISTS routes")
            db.execSQL("ALTER TABLE routes_new RENAME TO routes")

            // Nota: no se crean índices adicionales porque el Entity no los define.
        }
    }

    /**
     * v8 -> v9
     * - Reemplaza `routes.synced` (INTEGER) por `routes.syncStatus` (TEXT) mapeando:
     *     1 -> 'SYNCED'
     *     0 -> 'PENDING'
     * - Reconstruye `clients` para que `routeId` sea NOT NULL y exista FK -> routes(id)
     *   (ON DELETE RESTRICT, ON UPDATE NO ACTION). Si existen clientes sin routeId se crea una
     *   ruta placeholder '__unassigned__' para preservar integridad referencial y datos.
     */
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1) Rebuild routes table with syncStatus TEXT NOT NULL
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS routes_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    deliveryDay INTEGER NOT NULL,
                    syncStatus TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // 2) Copy data from old routes, mapping synced -> syncStatus
            db.execSQL(
                """
                INSERT INTO routes_new (id, name, deliveryDay, syncStatus, createdAt, updatedAt)
                SELECT id, name, deliveryDay,
                    CASE WHEN COALESCE(synced, 0) = 1 THEN 'SYNCED' ELSE 'PENDING' END AS syncStatus,
                    createdAt, updatedAt
                FROM routes
                """.trimIndent()
            )

            // 3) Drop old routes and rename
            db.execSQL("DROP INDEX IF EXISTS index_routes_name")
            db.execSQL("DROP TABLE IF EXISTS routes")
            db.execSQL("ALTER TABLE routes_new RENAME TO routes")

            // 4) Ensure placeholder route exists if any client has NULL routeId or routeId missing
            var needsPlaceholder = false
            db.query("SELECT COUNT(*) AS cnt FROM clients WHERE routeId IS NULL").use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex("cnt")
                    if (idx >= 0 && cursor.getInt(idx) > 0) needsPlaceholder = true
                }
            }

            // Also handle case when clients table did not have routeId column previously
            var clientsHaveRouteIdColumn = false
            db.query("PRAGMA table_info(`clients`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "routeId") {
                        clientsHaveRouteIdColumn = true
                        break
                    }
                }
            }

            if (!clientsHaveRouteIdColumn) {
                // If clients had no routeId column, but there are rows, we need placeholder
                db.query("SELECT COUNT(*) AS cnt FROM clients").use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex("cnt")
                        if (idx >= 0 && cursor.getInt(idx) > 0) needsPlaceholder = true
                    }
                }
            }

            if (needsPlaceholder) {
                // Insert placeholder if not exists
                db.query("SELECT id FROM routes WHERE id = '__unassigned__'").use { cursor ->
                    if (!cursor.moveToFirst()) {
                        db.execSQL(
                            "INSERT INTO routes (id, name, deliveryDay, syncStatus, createdAt, updatedAt) VALUES ('__unassigned__', 'UNASSIGNED', 1, 'SYNCED', 0, 0)"
                        )
                    }
                }
            }

            // 5) Rebuild clients table to have routeId NOT NULL and FK -> routes(id)
            //    Create clients_new using COALESCE(routeId, '__unassigned__') when routeId column exists,
            //    otherwise set '__unassigned__' for all rows.
            val clientsColumns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`clients`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    clientsColumns += cursor.getString(nameIndex)
                }
            }

            val hasRouteId = clientsColumns.contains("routeId")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clients_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    address TEXT,
                    createdAt INTEGER NOT NULL,
                    routeId TEXT NOT NULL,
                    FOREIGN KEY(routeId) REFERENCES routes(id) ON DELETE RESTRICT ON UPDATE NO ACTION
                )
                """.trimIndent()
            )

            if (hasRouteId) {
                db.execSQL(
                    """
                    INSERT INTO clients_new (id, name, address, createdAt, routeId)
                    SELECT id, name, address, createdAt, COALESCE(routeId, '__unassigned__') AS routeId FROM clients
                    """.trimIndent()
                )
            } else {
                db.execSQL(
                    """
                    INSERT INTO clients_new (id, name, address, createdAt, routeId)
                    SELECT id, name, address, createdAt, '__unassigned__' AS routeId FROM clients
                    """.trimIndent()
                )
            }

            // 6) Replace clients table
            db.execSQL("DROP TABLE IF EXISTS clients")
            db.execSQL("ALTER TABLE clients_new RENAME TO clients")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_clients_routeId ON clients(routeId)")
        }
    }

    /**
     * v9 -> v10
     * - Reconstruye tabla `clients` para agregar nuevos campos:
     *   phone, latitude, longitude, creditLimit, isActive, syncStatus, updatedAt, createdBy, lastModifiedBy.
     *
     * - Mantiene FK a routes(id) y índice routeId.
     * - Asigna valores por defecto:
     *   phone=NULL, lat=NULL, long=NULL, creditLimit=0.0, isActive=1, syncStatus='SYNCED',
     *   updatedAt=createdAt, createdBy='system_migration', lastModifiedBy='system_migration'.
     */
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS clients_new (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT,
                    address TEXT,
                    latitude REAL,
                    longitude REAL,
                    maxOrderAmountInCents INTEGER,
                    isActive INTEGER NOT NULL,
                    routeId TEXT NOT NULL,
                    syncStatus TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    createdBy TEXT NOT NULL,
                    lastModifiedBy TEXT NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(routeId) REFERENCES routes(id)
                        ON DELETE RESTRICT
                        ON UPDATE NO ACTION
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO clients_new (
                    id, name, phone, address, latitude, longitude, maxOrderAmountInCents,
                    isActive, routeId, syncStatus, createdAt, updatedAt, createdBy, lastModifiedBy
                )
                SELECT
                    id,
                    name,
                    NULL AS phone,
                    address,
                    NULL AS latitude,
                    NULL AS longitude,
                    NULL AS maxOrderAmountInCents,
                    1 AS isActive,
                    routeId,
                    'SYNCED' AS syncStatus,
                    createdAt,
                    createdAt AS updatedAt,
                    'system_migration' AS createdBy,
                    'system_migration' AS lastModifiedBy
                FROM clients
                """.trimIndent()
            )

            db.execSQL("DROP TABLE IF EXISTS clients")
            db.execSQL("ALTER TABLE clients_new RENAME TO clients")

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_clients_routeId ON clients(routeId)"
            )
        }
    }

    /**
     * v10 -> v11
     * - Agrega columna lastSyncedAt a la tabla clients para soporte de delta sync.
     */
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Agregar columna lastSyncedAt a la tabla clients
            db.execSQL("ALTER TABLE clients ADD COLUMN lastSyncedAt INTEGER")
        }
    }

    /**
     * v11 -> v12
     * - Agrega columna isDeleted a la tabla clients para soporte de soft delete.
     * - Por defecto 0 (false) para registros existentes.
     */
    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Agregar columna isDeleted a la tabla clients, por defecto 0 (false)
            db.execSQL("ALTER TABLE clients ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v12 -> v13
     * - Expande la tabla products con nuevos campos para sincronización completa:
     *   description, category, imageUrl, barcode, createdAt, updatedAt.
     */
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE products ADD COLUMN description TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN category TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN imageUrl TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT")
            db.execSQL("ALTER TABLE products ADD COLUMN createdAt INTEGER")
            db.execSQL("ALTER TABLE products ADD COLUMN updatedAt INTEGER")
        }
    }

    /**
     * v13 -> v14
     * - Rebuilds `products` table to support full sync architecture (Client-like).
     * - Changes:
     *   - `isActive` added (default 1)
     *   - `isDeleted` added (default 0)
     *   - `syncStatus` added (default 'SYNCED')
     *   - `createdAt` / `updatedAt` enforced NOT NULL (default 0 if null)
     *   - `lastSyncedAt` added (nullable)
     * - Recreates index on `name`.
     */
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create new table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products_new` (
                    `id` TEXT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `description` TEXT, 
                    `category` TEXT, 
                    `price` REAL NOT NULL, 
                    `imageUrl` TEXT, 
                    `barcode` TEXT, 
                    `stock` INTEGER NOT NULL, 
                    `isActive` INTEGER NOT NULL DEFAULT 1, 
                    `isDeleted` INTEGER NOT NULL DEFAULT 0, 
                    `syncStatus` TEXT NOT NULL DEFAULT 'SYNCED', 
                    `createdAt` INTEGER NOT NULL DEFAULT 0, 
                    `updatedAt` INTEGER NOT NULL DEFAULT 0, 
                    `lastSyncedAt` INTEGER, 
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            // 2. Copy data with defaults
            db.execSQL(
                """
                INSERT INTO products_new (id, name, description, category, price, imageUrl, barcode, stock, createdAt, updatedAt)
                SELECT 
                    id, name, description, category, price, imageUrl, barcode, stock,
                    COALESCE(createdAt, 0),
                    COALESCE(updatedAt, 0)
                FROM products
                """.trimIndent()
            )

            // 3. Swap tables
            db.execSQL("DROP TABLE products")
            db.execSQL("ALTER TABLE products_new RENAME TO products")

            // 4. Recreate Index
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
        }
    }
    /**
     * v14 -> v15
     * - Crea tabla `product_conflicts` para manejo de conflictos de sincronización.
     */
    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `product_conflicts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `productId` TEXT NOT NULL, 
                    `remoteJson` TEXT NOT NULL, 
                    `remoteUpdatedAt` INTEGER NOT NULL, 
                    `conflictDetectedAt` INTEGER NOT NULL, 
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_conflicts_productId` ON `product_conflicts` (`productId`)")
        }
    }
}
