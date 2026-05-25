package com.are.distribuidora.crash.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.are.distribuidora.R
import com.are.distribuidora.crash.data.CrashReportRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Visualiza el contenido completo de un reporte de crash en monospace
 * con texto seleccionable + botón de compartir.
 */
@AndroidEntryPoint
class CrashReportViewerActivity : AppCompatActivity() {

    @Inject lateinit var repository: CrashReportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report_viewer)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            finish()
            return
        }

        val title = findViewById<TextView>(R.id.textTitle)
        val content = findViewById<TextView>(R.id.textContent)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnShare = findViewById<ImageButton>(R.id.btnShare)

        title.text = file.nameWithoutExtension.removePrefix("crash_")
        content.text = repository.read(file)

        btnBack.setOnClickListener { finish() }
        btnShare.setOnClickListener {
            val uri = repository.shareUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_reports_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.crash_reports_share)))
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "extra_file_path"

        fun newIntent(context: Context, file: File): Intent {
            return Intent(context, CrashReportViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
        }
    }
}
