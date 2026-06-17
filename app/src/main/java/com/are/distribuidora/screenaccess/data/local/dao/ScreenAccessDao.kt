package com.are.distribuidora.screenaccess.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenAccessDao {

    @Query("SELECT * FROM screen_access WHERE uid = :uid LIMIT 1")
    fun observeByUid(uid: String): Flow<ScreenAccessEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScreenAccessEntity)

    @Query("DELETE FROM screen_access WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)
}
