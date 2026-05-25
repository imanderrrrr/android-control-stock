package com.are.distribuidora.data.local

import com.are.distribuidora.domain.core.SyncState

/**
 * Mapeo de SyncStatus (data) a SyncState (domain).
 * Este mapeo vive en la capa data: es el único punto que conoce ambos tipos.
 */
fun SyncStatus.toSyncState(): SyncState = when (this) {
    SyncStatus.SYNCED                -> SyncState.SYNCED
    SyncStatus.SYNCING               -> SyncState.SYNCING
    SyncStatus.CONFLICT              -> SyncState.CONFLICT
    SyncStatus.FAILED, SyncStatus.ERROR -> SyncState.FAILED
    SyncStatus.PENDING,
    SyncStatus.PENDING_CREATE,
    SyncStatus.PENDING_UPDATE,
    SyncStatus.PENDING_DELETE        -> SyncState.PENDING
}

