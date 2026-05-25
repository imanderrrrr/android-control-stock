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
                DistribuidoraMigrations.MIGRATION_8_9,
                DistribuidoraMigrations.MIGRATION_9_10,
                DistribuidoraMigrations.MIGRATION_10_11,
                DistribuidoraMigrations.MIGRATION_11_12,
                DistribuidoraMigrations.MIGRATION_12_13,
                DistribuidoraMigrations.MIGRATION_13_14,
                DistribuidoraMigrations.MIGRATION_14_15,
                DistribuidoraMigrations.MIGRATION_15_16,
                DistribuidoraMigrations.MIGRATION_16_17,
                DistribuidoraMigrations.MIGRATION_17_18,
                DistribuidoraMigrations.MIGRATION_18_19,
                DistribuidoraMigrations.MIGRATION_19_20,
                DistribuidoraMigrations.MIGRATION_20_21,
                DistribuidoraMigrations.MIGRATION_21_22,
                DistribuidoraMigrations.MIGRATION_22_23,
                DistribuidoraMigrations.MIGRATION_23_24,
                DistribuidoraMigrations.MIGRATION_24_25,
                DistribuidoraMigrations.MIGRATION_25_26,
                DistribuidoraMigrations.MIGRATION_26_27,
                DistribuidoraMigrations.MIGRATION_27_28,
                DistribuidoraMigrations.MIGRATION_28_29,
                DistribuidoraMigrations.MIGRATION_29_30,
                DistribuidoraMigrations.MIGRATION_30_31,
                DistribuidoraMigrations.MIGRATION_31_32,
                DistribuidoraMigrations.MIGRATION_32_33,
                DistribuidoraMigrations.MIGRATION_33_34,
            )
            .build()
}
