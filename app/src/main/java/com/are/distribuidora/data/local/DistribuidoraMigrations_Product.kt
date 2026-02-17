
package com.are.distribuidora.data.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "SYNC_PRODUCT"

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.d(TAG, "Running Product Migration 1→2")

        // Enforce foreign keys
        db.execSQL("PRAGMA foreign_keys=OFF")

        // 1. Create new table with updated schema
        // Note: isActive default 1, isDeleted default 0, syncStatus default 'SYNCED'
        // createdAt/updatedAt default 0 if null
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

        // 2. Copy data
        // Coalesce NULL timestamps to 0 to ensure non-null constraint
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

        // 3. Drop old table
        db.execSQL("DROP TABLE products")

        // 4. Rename new table
        db.execSQL("ALTER TABLE products_new RENAME TO products")

        // 5. Recreate Index
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
        
        db.execSQL("PRAGMA foreign_keys=ON")

        Log.d(TAG, "Product Migration 1→2 completed")
    }
}
