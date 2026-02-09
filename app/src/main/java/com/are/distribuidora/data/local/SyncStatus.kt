package com.are.distribuidora.data.local

enum class SyncStatus {
    /** Solo existe localmente y está pendiente de sincronizar. */
    PENDING,

    /** Pendiente de actualizar remotamente. */
    PENDING_UPDATE,

    /** Compatibilidad: alias histórico usado en algunos puntos del código para distinguir creación pendiente. */
    PENDING_CREATE,

    /** Intento de subida en progreso (aún no persistido en flujo actual). */
    SYNCING,

    /** Pendiente de eliminar remotamente. */
    PENDING_DELETE,

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
