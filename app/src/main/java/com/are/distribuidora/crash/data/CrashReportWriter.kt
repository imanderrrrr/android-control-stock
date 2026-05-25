package com.are.distribuidora.crash.data

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializa un crash + el snapshot del [LogBuffer] a un archivo de texto
 * dentro de `filesDir/crash_reports/`.
 *
 * Cada reporte queda en un archivo independiente nombrado
 * `crash_YYYYMMDD_HHmmss.txt` para ordenamiento alfabético = cronológico.
 *
 * Esta clase es 100% local: nunca toca red.
 */
@Singleton
class CrashReportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Carpeta donde viven los reportes. Se crea si no existe. */
    val reportsDir: File by lazy {
        File(context.filesDir, REPORTS_DIR_NAME).apply { mkdirs() }
    }

    /**
     * Escribe un reporte de crash al disco.
     *
     * @param throwable La excepción capturada por el UncaughtExceptionHandler.
     * @param thread Thread donde ocurrió la excepción.
     * @param logSnapshot Logs recientes del [LogBuffer] al momento del crash.
     * @return El archivo escrito, o null si falló (no debe romper el flujo de crash).
     */
    fun write(
        throwable: Throwable,
        thread: Thread,
        logSnapshot: List<LogEntry>,
    ): File? {
        return try {
            val now = System.currentTimeMillis()
            val fileName = "crash_${FILE_NAME_FORMAT.format(Date(now))}.txt"
            val file = File(reportsDir, fileName)

            file.bufferedWriter().use { writer ->
                writer.append(buildHeader(now, thread))
                writer.append("\n\n")
                writer.append(buildStackTrace(throwable))
                writer.append("\n\n")
                writer.append(buildLogsSection(logSnapshot))
            }

            // Limpieza retentiva: mantener solo los últimos MAX_REPORTS archivos.
            pruneOldReports()

            file
        } catch (e: Throwable) {
            // Nunca propagar — estamos dentro del crash handler.
            null
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers de formateo
    // ────────────────────────────────────────────────────────────────────

    private fun buildHeader(crashTimeMs: Long, thread: Thread): String {
        val ts = HUMAN_FORMAT.format(Date(crashTimeMs))
        return buildString {
            appendLine("=== Distribuidora CRASH REPORT ===")
            appendLine("Timestamp:     $ts")
            appendLine("Thread:        ${thread.name} (id=${thread.id})")
            appendLine("App package:   ${context.packageName}")
            appendLine("Android:       ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device:        ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Build:         ${Build.DISPLAY}")
            append("==================================")
        }
    }

    private fun buildStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        return buildString {
            appendLine("--- STACK TRACE ---")
            append(sw.toString().trim())
        }
    }

    private fun buildLogsSection(logs: List<LogEntry>): String {
        return buildString {
            appendLine("--- LOGS RECIENTES (${logs.size} entradas) ---")
            if (logs.isEmpty()) {
                append("(buffer vacío)")
                return@buildString
            }
            logs.forEach { entry ->
                val ts = LOG_LINE_FORMAT.format(Date(entry.timestampMs))
                append(ts)
                append(' ').append(entry.level)
                append(' ').append(entry.tag).append(": ")
                append(entry.message)
                appendLine()
                entry.throwableTrace?.let {
                    appendLine(it.prependIndent("    "))
                }
            }
        }
    }

    /** Mantiene solo los últimos [MAX_REPORTS] reportes para no llenar el almacenamiento. */
    private fun pruneOldReports() {
        try {
            val files = reportsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            files.drop(MAX_REPORTS).forEach { it.delete() }
        } catch (_: Throwable) {
            // best-effort
        }
    }

    companion object {
        const val REPORTS_DIR_NAME = "crash_reports"
        /** Cuántos reportes mantener antes de borrar los más viejos. */
        const val MAX_REPORTS = 30

        private val FILE_NAME_FORMAT =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val HUMAN_FORMAT =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val LOG_LINE_FORMAT =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}
