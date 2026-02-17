package com.are.distribuidora.domain.product

import com.are.distribuidora.data.local.SyncStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveProductSyncStatusesUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    operator fun invoke(): Flow<Map<String, SyncStatus>> {
        return repository.getSyncStatuses()
    }
}
