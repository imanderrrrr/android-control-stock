package com.are.distribuidora.crash.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.are.distribuidora.R
import com.are.distribuidora.crash.data.CrashReportRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Pantalla de debug que lista los reportes de crash persistidos en
 * `filesDir/crash_reports/`. Permite:
 *  - Ver el contenido completo de un reporte ([CrashReportViewerActivity]).
 *  - Compartirlo por Intent.ACTION_SEND (WhatsApp/email/etc.) vía FileProvider.
 *  - Eliminar reportes individuales o todos.
 *
 * Acceso: desde la pantalla de Reportes (Home), botón de bug en el header.
 */
@AndroidEntryPoint
class CrashReportsActivity : AppCompatActivity() {

    @Inject lateinit var repository: CrashReportRepository

    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var btnDeleteAll: ImageButton

    private val adapter = CrashReportsAdapter(
        onClick = ::openReport,
        onShare = ::shareReport,
        onDelete = ::deleteReport,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_reports)

        recycler = findViewById(R.id.recyclerReports)
        textEmpty = findViewById(R.id.textEmpty)
        btnBack = findViewById(R.id.btnBack)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnDeleteAll.setOnClickListener { confirmDeleteAll() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = repository.list()
        adapter.submit(items)
        val empty = items.isEmpty()
        textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
        btnDeleteAll.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ────────────────────────────────────────────────────────────────────
    // Acciones por reporte
    // ────────────────────────────────────────────────────────────────────

    private fun openReport(summary: CrashReportRepository.CrashReportSummary) {
        startActivity(CrashReportViewerActivity.newIntent(this, summary.file))
    }

    private fun shareReport(summary: CrashReportRepository.CrashReportSummary) {
        val uri = repository.shareUri(summary.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_reports_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_reports_share)))
    }

    private fun deleteReport(summary: CrashReportRepository.CrashReportSummary) {
        if (repository.delete(summary.file)) {
            refresh()
        } else {
            Toast.makeText(this, "No se pudo eliminar el reporte", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_reports_delete_all_confirm_title)
            .setMessage(R.string.crash_reports_delete_all_confirm_message)
            .setPositiveButton(R.string.crash_reports_delete_all) { _, _ ->
                val n = repository.deleteAll()
                Toast.makeText(this, "Eliminados: $n", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
