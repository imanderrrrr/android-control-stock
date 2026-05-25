package com.are.distribuidora.crash.data

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Acceso de solo-lectura/limpieza sobre los archivos generados por
 * [CrashReportWriter]. Lo usa la UI de debug para listar, leer, compartir
 * y eliminar reportes.
 */
@Singleton
class CrashReportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: CrashReportWriter,
) {

    /** Lista todos los reportes ordenados del más nuevo al más viejo. */
    fun list(): List<CrashReportSummary> {
        val files = writer.reportsDir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?: return emptyList()
        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                CrashReportSummary(
                    file = file,
                    displayName = formatDisplayName(file),
                    sizeBytes = file.length(),
                )
            }
    }

    /** Lee el contenido completo de un reporte como texto plano. */
    fun read(file: File): String = file.readText(Charsets.UTF_8)

    /** Borra un reporte específico. Devuelve true si tuvo éxito. */
    fun delete(file: File): Boolean = file.delete()

    /** Borra todos los reportes. Devuelve cuántos se eliminaron. */
    fun deleteAll(): Int {
        val files = writer.reportsDir.listFiles() ?: return 0
        var deleted = 0
        files.forEach { if (it.delete()) deleted++ }
        return deleted
    }

    /**
     * Convierte un File a un Uri que se puede mandar por Intent.ACTION_SEND
     * sin requerir permisos de almacenamiento (usa el FileProvider del manifest).
     */
    fun shareUri(file: File): android.net.Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun formatDisplayName(file: File): String {
        // Convierte "crash_20260524_143052.txt" -> "2026-05-24 14:30:52"
        return runCatching {
            val raw = file.nameWithoutExtension.removePrefix("crash_")
            val parsed = INPUT_FORMAT.parse(raw)
            if (parsed != null) DISPLAY_FORMAT.format(parsed) else file.name
        }.getOrDefault(file.name)
    }

    data class CrashReportSummary(
        val file: File,
        val displayName: String,
        val sizeBytes: Long,
    )

    companion object {
        private val INPUT_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val DISPLAY_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
