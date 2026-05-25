package com.are.distribuidora.core.sync

/**
 * Utilidades de logging/trace para el flujo de sincronización.
 *
 * Nota: mantenemos todo aquí (sin dependencias Android) para poder reutilizarlo
 * desde repositorios y workers.
 */
object SyncDebug {
    const val TAG_SYNC: String = "SYNC"

    /** Unique name for periodic work that syncs products. */
    const val UNIQUE_WORK_NAME_PRODUCTS: String = "sync_products_periodic"

    /** Unique name for one-time work that syncs pending pedidos. */
    const val UNIQUE_WORK_NAME_PEDIDOS_ONE_TIME: String = "sync_pedidos_now"

    /** Key used to pass a run id through WorkManager input/output data. */
    const val KEY_RUN_ID: String = "sync_run_id"

    /**
     * Prefix to correlate logs from the same sync run.
     * Example: "[runId=abc123]" or "[runId=none]".
     */
    fun prefix(runId: String?): String = "[runId=${runId ?: "none"}]"
}

