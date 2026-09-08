package com.are.distribuidora.di

import com.are.distribuidora.core.auth.CurrentUserIdProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.domain.core.ConnectivityChecker
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.product.ProductRepository
import com.are.distribuidora.stockmovement.data.local.dao.StockMovementDao
import com.are.distribuidora.stockmovement.data.remote.StockMovementRemoteDataSource
import com.are.distribuidora.stockmovement.data.remote.firestore.FirestoreStockMovementDataSource
import com.are.distribuidora.stockmovement.data.repository.StockMovementRepositoryImpl
import com.are.distribuidora.stockmovement.domain.repository.StockMovementRepository
import com.are.distribuidora.stockmovement.domain.usecase.CreateStockVoucherUseCase
import com.are.distribuidora.stockmovement.domain.usecase.ObserveProductMovementsUseCase
import com.are.distribuidora.stockmovement.domain.usecase.SyncStockMovementsUseCase
import com.are.distribuidora.workers.ProductSyncScheduler
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wiring del libro de movimientos de inventario (DailyStock 4.1). */
@Module
@InstallIn(SingletonComponent::class)
object StockMovementModule {

    @Provides
    @Singleton
    fun provideStockMovementRemoteDataSource(firestore: FirebaseFirestore): StockMovementRemoteDataSource =
        FirestoreStockMovementDataSource(firestore)

    @Provides
    @Singleton
    fun provideStockMovementRepository(
        database: DistribuidoraDatabase,
        movementDao: StockMovementDao,
        productDao: ProductDao,
        remote: StockMovementRemoteDataSource,
        productSyncScheduler: ProductSyncScheduler,
    ): StockMovementRepository = StockMovementRepositoryImpl(
        database = database,
        movementDao = movementDao,
        productDao = productDao,
        remote = remote,
        // Un vale dispara el worker de productos: sube movimientos y luego baja el catálogo.
        onLocalChange = { source -> productSyncScheduler.scheduleOneTimeNow(source) },
    )

    @Provides
    fun provideCreateStockVoucherUseCase(
        movements: StockMovementRepository,
        products: ProductRepository,
        currentUser: CurrentUserIdProvider,
    ): CreateStockVoucherUseCase = CreateStockVoucherUseCase(movements, products, currentUser)

    @Provides
    fun provideObserveProductMovementsUseCase(
        repository: StockMovementRepository,
    ): ObserveProductMovementsUseCase = ObserveProductMovementsUseCase(repository)

    @Provides
    @Singleton
    fun provideSyncStockMovementsUseCase(
        repository: StockMovementRepository,
        connectivityChecker: ConnectivityChecker,
        logger: Logger,
    ): SyncStockMovementsUseCase = SyncStockMovementsUseCase(repository, connectivityChecker, logger)
}
