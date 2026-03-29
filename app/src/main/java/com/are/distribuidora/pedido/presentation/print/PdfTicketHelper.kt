package com.are.distribuidora.pedido.presentation.print

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.are.distribuidora.domain.pedido.PedidoWithItems
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Genera un PDF de ticket usando [PdfDocument] + [android.graphics.Canvas] directamente.
 *
 * ¿Por qué NO WebView?
 *  - WebView tiene timing issues: onPageFinished no garantiza render completo.
 *  - El font rendering varía entre dispositivos, causando texto cortado.
 *  - Requiere postDelayed arbitrarios para esperar el re-layout.
 *
 * Con Canvas directo:
 *  - Síncrono y 100 % determinista.
 *  - [Paint.measureText] calibra el tamaño de fuente exacto en puntos PDF.
 *  - Todos los productos aparecen siempre — no hay "render incompleto".
 *  - No necesita permisos, WebView ni Handler.
 *
 * El texto viene de [PrintTicketHelper.buildTicketPreview], que produce
 * exactamente el mismo contenido que se envía a la impresora física.
 */
object PdfTicketHelper {

    /** Ancho del ticket 80 mm en puntos PDF (80 × 72 / 25.4 ≈ 226 pt). */
    private const val PAGE_WIDTH_PT = 226

    /** Padding horizontal izquierdo y derecho en puntos. */
    private const val PADDING_PT = 3f

    /**
     * Número de caracteres por línea del ticket.
     * Coincide con [PrintTicketHelper.TICKET_WIDTH] = 46.
     * Se usa para calibrar el tamaño de fuente con [Paint.measureText].
     */
    private const val CHARS_PER_LINE = 46

    /** Tamaño de fuente inicial en puntos (se ajusta automáticamente). */
    private const val BASE_TEXT_SIZE_PT = 7f

    // ─────────────────────────────────────────────────────────────────────────
    // API PÚBLICA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Genera el PDF y retorna un [Intent] ACTION_SEND para compartir.
     *
     * Las llamadas a [onProgress] ocurren en el dispatcher del caller
     * (Main cuando se llama desde [kotlinx.coroutines.CoroutineScope] del Fragment).
     *
     * @param onProgress 0‥100. Por defecto vacío para compatibilidad con callers existentes.
     */
    suspend fun createShareIntent(
        context: Context,
        pw: PedidoWithItems,
        vendorEmail: String,
        routeName: String,
        onProgress: (Int) -> Unit = {},
    ): Intent {
        onProgress(10)

        // ── 1. Obtener el texto del ticket ────────────────────────────────────
        // Misma función que usa buildEscPosBytes → contenido 100 % idéntico al impreso.
        val lines = PrintTicketHelper
            .buildTicketPreview(pw, vendorEmail, routeName)
            .split("\n")

        onProgress(30)

        // ── 2. Construir el PDF en memoria (CPU, Dispatchers.Default) ─────────
        // Canvas sobre PdfDocument es instantáneo incluso para 200+ líneas.
        val document = withContext(Dispatchers.Default) {
            buildDocument(lines)
        }

        onProgress(75)

        // ── 3. Escribir en disco (Dispatchers.IO) ─────────────────────────────
        val file = withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "pdf_tickets").also { it.mkdirs() }
            val safeClient = pw.pedido.clienteSnapshot.nombre
                .replace(Regex("[^a-zA-Z0-9_ -]"), "")
                .take(30)
                .trim()
            val out = File(cacheDir, "ticket_${safeClient}_${pw.pedido.id.take(8)}.pdf")
            FileOutputStream(out).use { document.writeTo(it) }
            document.close()
            out
        }

        onProgress(100)

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Ticket — ${pw.pedido.clienteSnapshot.nombre}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL DOCUMENTO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye el [PdfDocument] en memoria.
     *
     * Flujo:
     *  1. Configura [Paint] con [Typeface.MONOSPACE].
     *  2. Calibra [Paint.textSize] para que [CHARS_PER_LINE] chars llenen el ancho disponible.
     *  3. Calcula la altura de página según el número de líneas.
     *  4. Dibuja línea a línea con [android.graphics.Canvas.drawText].
     */
    private fun buildDocument(lines: List<String>): PdfDocument {

        // ── Configurar la fuente ──────────────────────────────────────────────
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface  = Typeface.MONOSPACE
            textSize  = BASE_TEXT_SIZE_PT
            color     = Color.BLACK
        }

        // ── Calibrar textSize ─────────────────────────────────────────────────
        // Medimos el ancho real de CHARS_PER_LINE caracteres a BASE_TEXT_SIZE_PT
        // y ajustamos para que quepan exactamente en el ancho disponible.
        // Así el resultado es correcto en cualquier dispositivo sin importar
        // las métricas internas de Typeface.MONOSPACE.
        val availableWidth = PAGE_WIDTH_PT - 2f * PADDING_PT   // 220 pt
        val measuredWidth  = paint.measureText("-".repeat(CHARS_PER_LINE))
        if (measuredWidth > 0f) {
            paint.textSize = BASE_TEXT_SIZE_PT * availableWidth / measuredWidth
        }

        // ── Calcular dimensiones de la página ─────────────────────────────────
        // fontSpacing = (descent - ascent + leading) = distancia entre baselines.
        // firstBaseline: desplazamos por (-ascent) para que el tope del texto
        //                coincida con PADDING_PT (ascent es negativo en Android).
        val lineSpacing   = paint.fontSpacing
        val firstBaseline = PADDING_PT - paint.ascent()
        val pageHeightPt  = (firstBaseline + lines.size * lineSpacing + PADDING_PT)
            .toInt()
            .coerceAtLeast(50)

        // ── Dibujar ───────────────────────────────────────────────────────────
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, pageHeightPt, 1).create()
        val page     = document.startPage(pageInfo)
        val canvas   = page.canvas

        var y = firstBaseline
        for (line in lines) {
            canvas.drawText(line, PADDING_PT, y, paint)
            y += lineSpacing
        }

        document.finishPage(page)
        return document
    }
}
