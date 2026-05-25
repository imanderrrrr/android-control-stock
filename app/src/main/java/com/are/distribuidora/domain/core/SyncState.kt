package com.are.distribuidora.domain.core

/**
 * Estado de sincronización del dominio.
 *
 * Esta es la representación soberana del estado de sync para el dominio.
 * La capa data mapea su propio SyncStatus a este tipo antes de retornarlo
 * al dominio o a presentation. Así el dominio no conoce detalles de Room/data.
 */
enum class SyncState {
    /** Confirmado por el servidor remoto. */
    SYNCED,

    /** Pendiente de sincronizar (creación, actualización o borrado aún no enviado). */
    PENDING,

    /** Subida en progreso. */
    SYNCING,

    /** Falló la sincronización. */
    FAILED,

    /** Conflicto detectado entre versión local y remota. */
    CONFLICT,
}

