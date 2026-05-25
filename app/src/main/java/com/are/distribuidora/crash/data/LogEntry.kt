package com.are.distribuidora.crash.data

/**
 * Una entrada individual del ring buffer de logs.
 *
 * Se guarda en memoria mientras la app esté viva y se vuelca al
 * reporte de crash cuando ocurre una excepción no manejada.
 */
data class LogEntry(
    /** Epoch ms del momento exacto en que se registró el log. */
    val timestampMs: Long,
    /** Nivel del log: D=Debug, I=Info, W=Warn, E=Error. */
    val level: Char,
    /** Tag del log (ej. "HOME_CHROME", "Sync"). */
    val tag: String,
    /** Mensaje principal. */
    val message: String,
    /** Stack trace del throwable asociado, si lo hubiera. */
    val throwableTrace: String? = null,
)
