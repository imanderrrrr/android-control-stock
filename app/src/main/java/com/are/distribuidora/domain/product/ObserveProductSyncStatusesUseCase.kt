package com.are.distribuidora.domain.product

import com.are.distribuidora.domain.core.SyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveProductSyncStatusesUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    operator fun invoke(): Flow<Map<String, SyncState>> {
        return repository.getSyncStatuses()
    }
}
