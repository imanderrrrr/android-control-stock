package com.are.distribuidora.di

import android.content.Context
import androidx.room.Room
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.DistribuidoraMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DistribuidoraDatabase =
        Room.databaseBuilder(
            context,
            DistribuidoraDatabase::class.java,
            "distribuidora.db"
        )
            .addMigrations(
                DistribuidoraMigrations.MIGRATION_1_2,
                DistribuidoraMigrations.MIGRATION_2_3,
                DistribuidoraMigrations.MIGRATION_3_4,
                DistribuidoraMigrations.MIGRATION_4_5,
                DistribuidoraMigrations.MIGRATION_5_6,
                DistribuidoraMigrations.MIGRATION_6_7,
                DistribuidoraMigrations.MIGRATION_7_8,
            )
            .build()
}
