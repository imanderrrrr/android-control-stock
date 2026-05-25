package com.are.distribuidora.crash

import android.util.Log
import com.are.distribuidora.crash.data.CrashReportWriter
import com.are.distribuidora.crash.data.LogBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engancha un [Thread.UncaughtExceptionHandler] global que persiste un
 * reporte de crash en disco antes de delegar al handler original (que
 * normalmente termina el proceso).
 *
 * Llamar [install] UNA sola vez al inicio de [com.are.distribuidora.DistribuidoraApplication.onCreate].
 *
 * Diseñado para ser robusto durante el crash:
 * - Captura cualquier excepción interna del propio reporter.
 * - Siempre delega al handler previo (incluido el default de Android),
 *   manteniendo el dialog del sistema y el reporte de Play Console.
 */
@Singleton
class CrashReporter @Inject constructor(
    private val logBuffer: LogBuffer,
    private val writer: CrashReportWriter,
) {

    @Volatile
    private var installed = false

    /**
     * Instala el handler. Idempotente — llamarlo varias veces no apila handlers.
     */
    fun install() {
        if (installed) return
        installed = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 1) Intentamos persistir el reporte. SIEMPRE protegido en try/catch
            //    porque estamos dentro de un crash y no podemos romper más.
            try {
                val snapshot = logBuffer.snapshot()
                val file = writer.write(throwable, thread, snapshot)
                Log.e(
                    TAG,
                    "CRASH capturado en thread=${thread.name}. " +
                        "Reporte: ${file?.absolutePath ?: "FAILED_TO_WRITE"}",
                    throwable,
                )
            } catch (writerError: Throwable) {
                Log.e(TAG, "CrashReporter falló al escribir reporte", writerError)
            }

            // 2) Delegamos al handler previo (Android default).
            //    Esto asegura que el sistema muestre el ANR/crash dialog y que
            //    Play Console reciba el reporte si está habilitado.
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                // Sin handler previo: matamos el proceso explícitamente para no
                // dejar la app en estado inconsistente.
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }

        Log.i(TAG, "CrashReporter instalado. Reportes en filesDir/crash_reports/")
    }

    companion object {
        private const val TAG = "CrashReporter"
    }
}
