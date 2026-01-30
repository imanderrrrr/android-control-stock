package com.are.distribuidora.data.local

enum class SyncStatus {
    /** Solo existe localmente y está pendiente de sincronizar. */
    PENDING,

    /** Intento de subida en progreso (aún no persistido en flujo actual). */
    SYNCING,

    /** Confirmada por Firestore. */
    SYNCED,

    /** Fallo definitivo. */
    FAILED,

    /**
     * Compatibilidad histórica: antes se usaba ERROR.
     * Mantenerlo evita romper Room (datos existentes) y código generado.
     */
    @Deprecated("Usar FAILED")
    ERROR,
}
