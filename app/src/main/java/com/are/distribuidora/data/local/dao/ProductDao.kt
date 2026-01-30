package com.are.distribuidora.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.are.distribuidora.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProductEntity)

    @Update
    suspend fun update(entity: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProductEntity?

    /**
     * DEPRECATED: lectura puntual rompe la reactividad de UI.
     *
     * Migrar a [observeProducts] y consumir Flow en repository/use cases/ViewModel.
     */
    @Deprecated(
        message = "Evitar lectura puntual. Usar observeProducts() (Flow) para UI reactiva.",
        level = DeprecationLevel.WARNING,
    )
    @Query("SELECT * FROM products")
    suspend fun getAll(): List<ProductEntity>

    /**
     * Stream reactivo (solo lectura). Room emitirá automáticamente cuando cambie la tabla.
     */
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeProducts(): Flow<List<ProductEntity>>

    /**
     * Alias legacy. Mantener temporalmente para no romper consumidores existentes.
     */
    @Deprecated(
        message = "Renombrado a observeProducts()",
        replaceWith = ReplaceWith("observeProducts()"),
        level = DeprecationLevel.WARNING,
    )
    fun observeAll(): Flow<List<ProductEntity>> = observeProducts()

    @Delete
    suspend fun delete(entity: ProductEntity)
}
