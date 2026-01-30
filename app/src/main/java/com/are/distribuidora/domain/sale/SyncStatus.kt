package com.are.distribuidora.domain.sale

/**
 * Estado de sincronización de una venta en el modelo híbrido (Room → Firestore).
 *
 * Lectura: UI / use cases pueden leerlo.
 * Escritura: por diseño, solo el repositorio híbrido puede transicionarlo.
 */
enum class SyncStatus {
    /** Solo existe localmente. */
    PENDING,

    /** Intento de subida en progreso. */
    SYNCING,

    /** Confirmada por Firestore. */
    SYNCED,

    /** Fallo definitivo. */
    FAILED,
}
