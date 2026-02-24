package com.are.distribuidora.pedido.presentation.print

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.are.distribuidora.domain.pedido.PedidoWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Genera un PDF de factura con el mismo contenido que [PrintTicketHelper] y
 * devuelve un [Intent] de tipo ACTION_SEND listo para compartir por cualquier app.
 *
 * Responsabilidades (Clean Architecture – capa presentation/helper):
 * - Construir el documento PDF con los datos del pedido.
 * - Guardarlo en cache del app (no requiere permisos de almacenamiento).
 * - Retornar un Intent compartible usando FileProvider.
 *
 * No accede a ViewModel, repositorios ni Bluetooth.
 */
object PdfTicketHelper {

    // ── Dimensiones del "ticket" en puntos (pt) ───────────────────────────────
    // 1 pt = 1/72 pulgada. Para papel de 80 mm de ancho usamos 226 pt (~80 mm).
    private const val PAGE_WIDTH_PT  = 226
    private const val MARGIN_PT      = 12f

    // Fuentes y espaciados
    private const val TEXT_SIZE_NORMAL  = 8f
    private const val TEXT_SIZE_TITLE   = 10f
    private const val TEXT_SIZE_BOLD    = 9f
    private const val LINE_HEIGHT       = 12f
    private const val SECTION_GAP       = 6f

    // Formateadores — mismo locale que PrintTicketHelper
    private val currencyFmt: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "GT"))

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Crea el PDF en la carpeta de cache y retorna un Intent ACTION_SEND para
     * compartirlo. El Intent incluye el tipo MIME "application/pdf".
     *
     * @param context  Contexto (Activity o Fragment context).
     * @param pw       Datos completos del pedido (snapshot + items).
     * @param vendorEmail  Email del vendedor para el encabezado.
     * @param routeId  ID de la ruta para el encabezado.
     * @return Intent listo para pasarse a startActivity(chooser).
     * @throws Exception si falla la escritura del archivo.
     */
    fun createShareIntent(
        context: Context,
        pw: PedidoWithItems,
        vendorEmail: String,
        routeId: String,
    ): Intent {
        val pdfFile = buildPdf(context, pw, vendorEmail, routeId)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Factura — ${pw.pedido.clienteSnapshot.nombre}",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL PDF
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPdf(
        context: Context,
        pw: PedidoWithItems,
        vendorEmail: String,
        routeId: String,
    ): File {
        val pedido = pw.pedido

        // ── Calcular alto total necesario ─────────────────────────────────────
        val lines = collectLines(pw, vendorEmail, routeId)
        val pageHeight = (MARGIN_PT + lines.size * LINE_HEIGHT + MARGIN_PT + SECTION_GAP * 4).toInt()
            .coerceAtLeast(400)

        // ── Crear documento ───────────────────────────────────────────────────
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // ── Paints ────────────────────────────────────────────────────────────
        val paintNormal = Paint().apply {
            color     = Color.BLACK
            textSize  = TEXT_SIZE_NORMAL
            isAntiAlias = true
        }
        val paintBold = Paint().apply {
            color      = Color.BLACK
            textSize   = TEXT_SIZE_BOLD
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintTitle = Paint().apply {
            color      = Color.BLACK
            textSize   = TEXT_SIZE_TITLE
            isFakeBoldText = true
            isAntiAlias = true
            textAlign  = Paint.Align.CENTER
        }
        val paintSep = Paint().apply {
            color     = Color.DKGRAY
            strokeWidth = 0.5f
            style     = Paint.Style.STROKE
        }

        // ── Dibujar líneas ────────────────────────────────────────────────────
        var y = MARGIN_PT + LINE_HEIGHT

        for (line in lines) {
            when (line.type) {
                LineType.TITLE -> {
                    canvas.drawText(line.text, PAGE_WIDTH_PT / 2f, y, paintTitle)
                    y += LINE_HEIGHT
                }
                LineType.BOLD -> {
                    canvas.drawText(line.text, MARGIN_PT, y, paintBold)
                    y += LINE_HEIGHT
                }
                LineType.SEPARATOR -> {
                    y += 2f
                    canvas.drawLine(MARGIN_PT, y, PAGE_WIDTH_PT - MARGIN_PT, y, paintSep)
                    y += 4f
                }
                LineType.NORMAL -> {
                    canvas.drawText(line.text, MARGIN_PT, y, paintNormal)
                    y += LINE_HEIGHT
                }
                LineType.EMPTY -> {
                    y += LINE_HEIGHT / 2
                }
            }
        }

        document.finishPage(page)

        // ── Guardar en cache ──────────────────────────────────────────────────
        val cacheDir = File(context.cacheDir, "pdf_tickets").also { it.mkdirs() }
        val safeClient = pedido.clienteSnapshot.nombre
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .take(30)
            .trim()
        val fileName = "factura_${safeClient}_${pedido.id.take(8)}.pdf"
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return file
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MODELO DE LÍNEAS
    // ─────────────────────────────────────────────────────────────────────────

    private enum class LineType { TITLE, BOLD, NORMAL, SEPARATOR, EMPTY }
    private data class Line(val text: String, val type: LineType)

    private fun collectLines(
        pw: PedidoWithItems,
        vendorEmail: String,
        routeId: String,
    ): List<Line> {
        val pedido = pw.pedido
        val result = mutableListOf<Line>()

        // ── Encabezado ────────────────────────────────────────────────────────
        result += Line("DISTRIBUIDORA JIREH", LineType.TITLE)
        result += Line("TICKET DE PEDIDO",    LineType.TITLE)
        result += Line("", LineType.SEPARATOR)

        // ── Datos del pedido ──────────────────────────────────────────────────
        result += Line("Ruta: $routeId",                                   LineType.NORMAL)
        result += Line("Cliente: ${pedido.clienteSnapshot.nombre}",        LineType.NORMAL)
        if (!pedido.clienteSnapshot.direccion.isNullOrBlank()) {
            result += Line("Dirección: ${pedido.clienteSnapshot.direccion}", LineType.NORMAL)
        }
        if (!pedido.clienteSnapshot.telefono.isNullOrBlank()) {
            result += Line("Tel: ${pedido.clienteSnapshot.telefono}",      LineType.NORMAL)
        }
        result += Line("Vendedor: $vendorEmail",                           LineType.NORMAL)
        result += Line("Fecha: ${dateTimeFmt.format(Date(pedido.creadoEn))}",LineType.NORMAL)
        result += Line("", LineType.SEPARATOR)

        // ── Encabezado de columnas ────────────────────────────────────────────
        result += Line("PRODUCTO                 CANT  TOTAL", LineType.BOLD)
        result += Line("", LineType.SEPARATOR)

        // ── Ítems ─────────────────────────────────────────────────────────────
        for (item in pw.items) {
            val totalStr = currencyFmt.format(item.totalItem)
            val qtyStr   = item.cantidad.toString()
            // Nombre truncado para no salir del ancho
            val namePart = item.nombre.take(20).padEnd(20)
            val itemLine = "$namePart  $qtyStr  $totalStr"
            result += Line(itemLine, LineType.NORMAL)
            result += Line(
                "  ${currencyFmt.format(item.precioUnitario)} x ${item.cantidad}",
                LineType.NORMAL,
            )
            if (!item.notes.isNullOrBlank()) {
                result += Line("  Det: ${item.notes}", LineType.NORMAL)
            }
            if (item.descuentoItem > 0.0) {
                result += Line(
                    "  Desc. item: -${currencyFmt.format(item.descuentoItem)}",
                    LineType.NORMAL,
                )
            }
        }
        result += Line("", LineType.SEPARATOR)

        // ── Totales ───────────────────────────────────────────────────────────
        result += Line("Subtotal: ${currencyFmt.format(pedido.subtotal)}", LineType.NORMAL)
        if (pedido.descuentoGlobal > 0.0) {
            result += Line(
                "Desc. global: -${currencyFmt.format(pedido.descuentoGlobal)}",
                LineType.NORMAL,
            )
        }
        result += Line("TOTAL: ${currencyFmt.format(pedido.total)}", LineType.BOLD)
        result += Line("", LineType.SEPARATOR)

        // ── Pie ───────────────────────────────────────────────────────────────
        result += Line("Gracias por su compra", LineType.TITLE)
        result += Line("", LineType.EMPTY)

        return result
    }
}


