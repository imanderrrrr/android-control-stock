package com.are.distribuidora.data.core

import com.are.distribuidora.crash.data.LogBuffer
import com.are.distribuidora.crash.data.LogEntry
import com.are.distribuidora.domain.core.Logger
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

/**
 * Implementación de [Logger] que:
 *  1. Escribe al logcat de Android (como antes — compatibilidad total).
 *  2. Espeja cada mensaje al [LogBuffer] del módulo de crash, para que el
 *     reporte de un crash futuro incluya los logs previos.
 *
 * Importante: el código que sigue usando `android.util.Log.d/e` directamente
 * NO alimenta el buffer. Para que un módulo aporte contexto al reporte de
 * crash debe usar esta abstracción (Logger inyectado).
 */
class AndroidLogger @Inject constructor(
    private val logBuffer: LogBuffer,
) : Logger {
    override fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
        logBuffer.append(
            LogEntry(
                timestampMs = System.currentTimeMillis(),
                level = 'D',
                tag = tag,
                message = message,
            )
        )
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
        logBuffer.append(
            LogEntry(
                timestampMs = System.currentTimeMillis(),
                level = 'E',
                tag = tag,
                message = message,
                throwableTrace = throwable?.let { stackTraceAsString(it) },
            )
        )
    }

    private fun stackTraceAsString(t: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { t.printStackTrace(it) }
        return sw.toString().trim()
    }
}
