package com.are.distribuidora.data.repository.hybrid

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Módulo de Hilt SOLO para androidTest.
 * Reemplaza el DatabaseModule de producción para que todo el grafo resuelva
 * exactamente la instancia de DB creada por el test.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DistribuidoraDatabase {
        return TestDbHolder.instance
            ?: throw IllegalStateException(
                "TestDbHolder.instance no inicializado. " +
                    "Crea la DB en @Before ANTES de hiltRule.inject() o antes de usar dependencias."
            )
    }
}

/**
 * Holder/fábrica para exponer la instancia de DB del test al módulo Hilt.
 *
 * Importante: en tests instrumentados, mezclar instancias distintas de Room (aunque apunten al mismo archivo)
 * puede dar síntomas raros de visibilidad. Por eso centralizamos aquí la creación y el 'reopen'.
 */
object TestDbHolder {
    private val lock = ReentrantLock()

    @Volatile
    var instance: DistribuidoraDatabase? = null
        private set

    // En instrumented tests, forzar un solo hilo para queries/transacciones reduce flakes.
    @Volatile
    private var executor: ExecutorService? = null

    private fun getOrCreateExecutor(): ExecutorService {
        return executor ?: Executors.newSingleThreadExecutor { r ->
            Thread(r, "room_test_single").apply { isDaemon = true }
        }.also { executor = it }
    }

    fun createOrRecreate(
        context: Context,
        dbName: String,
        journalMode: RoomDatabase.JournalMode = RoomDatabase.JournalMode.TRUNCATE,
    ): DistribuidoraDatabase = lock.withLock {
        // Cerrar cualquier instancia previa antes de re-crear.
        try {
            instance?.close()
        } catch (_: Exception) {
        }

        val ex = getOrCreateExecutor()

        val created = Room.databaseBuilder(context, DistribuidoraDatabase::class.java, dbName)
            .setJournalMode(journalMode)
            .setQueryExecutor(ex)
            .setTransactionExecutor(ex)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()

        instance = created
        return created
    }

    /**
     * Reabre la MISMA DB (mismo archivo) devolviendo una NUEVA instancia Room.
     * Importante: esto NO puede actualizar el grafo de Hilt una vez inyectado.
     * Por eso, este método se usa para obtener una referencia directa (no inyectada) en el test.
     */
    fun reopenDirect(
        context: Context,
        dbName: String,
        journalMode: RoomDatabase.JournalMode = RoomDatabase.JournalMode.TRUNCATE,
    ): DistribuidoraDatabase = lock.withLock {
        // No tocar instance: el grafo de Hilt ya está construido.
        val ex = getOrCreateExecutor()
        return Room.databaseBuilder(context, DistribuidoraDatabase::class.java, dbName)
            .setJournalMode(journalMode)
            .setQueryExecutor(ex)
            .setTransactionExecutor(ex)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }

    fun closeAndNull() = lock.withLock {
        try {
            instance?.close()
        } catch (_: Exception) {
        } finally {
            instance = null
        }

        // Cerramos el executor al final del test para no dejar threads vivos.
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        } finally {
            executor = null
        }
    }

    fun deleteDbFiles(dbFile: File) {
        // helper best-effort
        try { dbFile.delete() } catch (_: Exception) {}
        try { File(dbFile.path + "-shm").delete() } catch (_: Exception) {}
        try { File(dbFile.path + "-wal").delete() } catch (_: Exception) {}
    }
}
