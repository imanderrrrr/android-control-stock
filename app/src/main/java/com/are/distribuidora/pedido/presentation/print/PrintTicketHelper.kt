package com.are.distribuidora.pedido.presentation.print

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.are.distribuidora.domain.pedido.PedidoWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Helper de impresión de tickets ESC/POS vía Bluetooth.
 *
 * Responsabilidades:
 * - Formatear el contenido del ticket (texto de ancho fijo, 32 columnas).
 * - Conectar con la impresora seleccionada por el usuario.
 * - Enviar los bytes ESC/POS.
 *
 * No tiene dependencias de UI ni de ViewModel: recibe todo lo que necesita
 * por parámetro, siguiendo el mismo principio de las capas existentes.
 */
object PrintTicketHelper {

    // UUID estándar SPP (Serial Port Profile) para impresoras Bluetooth
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Ancho del ticket en caracteres para impresora de 80mm
    // 80mm = 48 columnas en fuente estándar (12 cpi). Usamos 46 para dejar
    // un pequeño margen lateral y evitar que el último carácter se corte.
    private const val TICKET_WIDTH = 46

    // Tamaño de chunk al escribir en el socket para no saturar el buffer de la impresora
    private const val CHUNK_SIZE = 512

    // Milisegundos de espera entre chunks y tras el último flush
    private const val CHUNK_DELAY_MS = 20L
    private const val FINAL_FLUSH_DELAY_MS = 800L

    // ── Comandos ESC/POS básicos ──────────────────────────────────────────────
    private val ESC_INIT        = byteArrayOf(0x1B, 0x40)           // Inicializar
    private val ESC_ALIGN_LEFT  = byteArrayOf(0x1B, 0x61, 0x00)     // Alinear izquierda
    private val ESC_ALIGN_CENTER= byteArrayOf(0x1B, 0x61, 0x01)     // Alinear centro
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)     // Alinear derecha
    private val ESC_BOLD_ON     = byteArrayOf(0x1B, 0x45, 0x01)     // Negrita ON
    private val ESC_BOLD_OFF    = byteArrayOf(0x1B, 0x45, 0x00)     // Negrita OFF
    private val ESC_CUT         = byteArrayOf(0x1D, 0x56, 0x41, 0x03) // Corte parcial
    private val LF              = byteArrayOf(0x0A)                  // Salto de línea

    // ── Formatters ────────────────────────────────────────────────────────────
    private val currencyFmt: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")).also {
            it.currency = Currency.getInstance("GTQ")
        }
    }
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "GT"))

    // ─────────────────────────────────────────────────────────────────────────
    // API PÚBLICA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retorna la lista de impresoras Bluetooth ya emparejadas.
     * Requiere permiso BLUETOOTH_CONNECT (API 31+) o BLUETOOTH (API 30-).
     * Retorna lista vacía si no hay permiso o Bluetooth desactivado.
     */
    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()

        if (!hasBluetoothPermission(context)) return emptyList()

        @Suppress("MissingPermission")
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Construye el contenido del ticket en texto plano (sin ESC/POS) para previsualización.
     */
    fun buildTicketPreview(pw: PedidoWithItems, vendorEmail: String, routeName: String): String =
        buildTicketLines(pw, vendorEmail, routeName).joinToString("\n")

    /**
     * Imprime el ticket en la impresora [device].
     * Debe llamarse en un hilo de IO (ya usa withContext internamente).
     *
     * @throws SecurityException si falta permiso Bluetooth.
     * @throws Exception si la conexión o escritura falla.
     */
    suspend fun print(
        context: Context,
        device: BluetoothDevice,
        pw: PedidoWithItems,
        vendorEmail: String,
        routeName: String,
    ) = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission(context)) {
            throw SecurityException("Permiso Bluetooth no concedido")
        }

        val bytes = buildEscPosBytes(pw, vendorEmail, routeName)

        var socket: BluetoothSocket? = null
        var out: OutputStream? = null
        try {
            @Suppress("MissingPermission")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            // Cancelar discovery mejora la estabilidad de la conexión
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            @Suppress("MissingPermission")
            manager?.adapter?.cancelDiscovery()

            socket.connect()
            out = socket.outputStream

            // Escribir en chunks para no saturar el buffer interno de la impresora
            // y evitar que corte la impresión a la mitad
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + CHUNK_SIZE, bytes.size)
                out.write(bytes, offset, end - offset)
                out.flush()
                offset = end
                if (offset < bytes.size) {
                    delay(CHUNK_DELAY_MS)
                }
            }

            // Espera final para que la impresora procese todos los datos antes
            // de que cerremos el socket (evita truncado al final del ticket)
            delay(FINAL_FLUSH_DELAY_MS)
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Verifica si el dispositivo tiene los permisos Bluetooth necesarios.
     */
    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Solicita los permisos Bluetooth necesarios.
     */
    fun requestBluetoothPermissions(activity: Activity, requestCode: Int) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        ActivityCompat.requestPermissions(activity, perms, requestCode)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LÓGICA PRIVADA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Construye las líneas de texto del ticket.
     * Usado tanto para previsualización como para la generación ESC/POS.
     */
    private fun buildTicketLines(
        pw: PedidoWithItems,
        vendorEmail: String,
        routeName: String,
    ): List<String> {
        val pedido = pw.pedido
        val lines = mutableListOf<String>()
        val sep = "-".repeat(TICKET_WIDTH)

        // ── Encabezado ────────────────────────────────────────────────────────
        lines += centerPad("DISTRIBUIDORA JIREH")
        lines += centerPad("TICKET DE PEDIDO")
        lines += sep

        // ── Datos del pedido ──────────────────────────────────────────────────
        lines += labelValue("Ruta:", routeName)
        lines += labelValue("Cliente:", pedido.clienteSnapshot.nombre)
        if (!pedido.clienteSnapshot.direccion.isNullOrBlank()) {
            lines += labelValue("Dirección:", pedido.clienteSnapshot.direccion)
        }
        if (!pedido.clienteSnapshot.telefono.isNullOrBlank()) {
            lines += labelValue("Tel:", pedido.clienteSnapshot.telefono)
        }
        lines += labelValue("Vendedor:", vendorEmail)
        lines += labelValue("Completado:", dateTimeFmt.format(Date(pedido.creadoEn)))
        lines += sep

        // ── Cabecera de columnas ──────────────────────────────────────────────
        // Formato: PRODUCTO           CANT   TOTAL
        lines += colHeader()
        lines += sep

        // ── Ítems ─────────────────────────────────────────────────────────────
        for (item in pw.items) {
            // Nombre del producto (puede ser largo → truncar o envolver)
            val nameLines = wrapText(item.nombre, TICKET_WIDTH)
            nameLines.forEachIndexed { idx, nameLine ->
                if (idx == 0) {
                    // Primera línea: nombre + cantidad + total
                    lines += itemRow(
                        name     = nameLine,
                        qty      = item.cantidad,
                        total    = item.totalItem,
                        hasMore  = nameLines.size > 1,
                    )
                } else {
                    lines += "  $nameLine"
                }
            }
            // Detalle de precio unitario
            lines += "  ${currencyFmt.format(item.precioUnitario)} x ${item.cantidad}"
            // Notas / instrucciones especiales del ítem
            if (!item.notes.isNullOrBlank()) {
                lines += "  Det: ${item.notes}"
            }
            // Descuento por ítem si aplica
            if (item.descuentoItem > 0.0) {
                lines += "  Desc. item: -${currencyFmt.format(item.descuentoItem)}"
            }
        }
        lines += sep

        // ── Totales ───────────────────────────────────────────────────────────
        lines += labelValue("Subtotal:", currencyFmt.format(pedido.subtotal))
        if (pedido.descuentoGlobal > 0.0) {
            lines += labelValue("Desc. global:", "-${currencyFmt.format(pedido.descuentoGlobal)}")
        }
        if (pedido.ivaAmount > 0.0) {
            lines += labelValue("IVA (12%):", currencyFmt.format(pedido.ivaAmount))
        }
        lines += labelValue("TOTAL:", currencyFmt.format(pedido.total))
        lines += sep

        // ── Pie ───────────────────────────────────────────────────────────────
        lines += centerPad("Gracias por su compra")
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        lines += ""

        return lines
    }

    /**
     * Convierte las líneas del ticket en bytes ESC/POS listos para enviar.
     */
    private fun buildEscPosBytes(
        pw: PedidoWithItems,
        vendorEmail: String,
        routeName: String,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()

        fun w(b: ByteArray) = out.write(b)
        // wLine soporta texto con \n internos (p.ej. labelValue con valor largo)
        fun wLine(text: String) {
            text.split("\n").forEach { line ->
                out.write(line.toByteArray(Charsets.ISO_8859_1))
                w(LF)
            }
        }

        w(ESC_INIT)

        val pedido = pw.pedido
        val sep = "-".repeat(TICKET_WIDTH)

        // Re-construimos con ESC/POS para aplicar negrita/centrado en secciones clave
        w(ESC_ALIGN_CENTER)
        w(ESC_BOLD_ON)
        wLine("DISTRIBUIDORA JIREH")
        wLine("TICKET DE PEDIDO")
        w(ESC_BOLD_OFF)
        w(ESC_ALIGN_LEFT)
        wLine(sep)

        wLine(labelValue("Ruta:", routeName))
        wLine(labelValue("Cliente:", pedido.clienteSnapshot.nombre))
        if (!pedido.clienteSnapshot.direccion.isNullOrBlank())
            wLine(labelValue("Dirección:", pedido.clienteSnapshot.direccion))
        if (!pedido.clienteSnapshot.telefono.isNullOrBlank())
            wLine(labelValue("Tel:", pedido.clienteSnapshot.telefono))
        wLine(labelValue("Vendedor:", vendorEmail))
        wLine(labelValue("Completado:", dateTimeFmt.format(Date(pedido.creadoEn))))
        wLine(sep)
        wLine(colHeader())
        wLine(sep)

        for (item in pw.items) {
            val nameLines = wrapText(item.nombre, TICKET_WIDTH)
            nameLines.forEachIndexed { idx, nameLine ->
                if (idx == 0) wLine(itemRow(nameLine, item.cantidad, item.totalItem, nameLines.size > 1))
                else wLine("  $nameLine")
            }
            wLine("  ${currencyFmt.format(item.precioUnitario)} x ${item.cantidad}")
            if (!item.notes.isNullOrBlank())
                wLine("  Det: ${item.notes}")
            if (item.descuentoItem > 0.0)
                wLine("  Desc. item: -${currencyFmt.format(item.descuentoItem)}")
        }

        wLine(sep)
        wLine(labelValue("Subtotal:", currencyFmt.format(pedido.subtotal)))
        if (pedido.descuentoGlobal > 0.0)
            wLine(labelValue("Desc. global:", "-${currencyFmt.format(pedido.descuentoGlobal)}"))
        if (pedido.ivaAmount > 0.0)
            wLine(labelValue("IVA (12%):", currencyFmt.format(pedido.ivaAmount)))

        w(ESC_BOLD_ON)
        wLine(labelValue("TOTAL:", currencyFmt.format(pedido.total)))
        w(ESC_BOLD_OFF)
        wLine(sep)

        w(ESC_ALIGN_CENTER)
        wLine("Gracias por su compra")
        w(ESC_ALIGN_LEFT)
        // 5 saltos extra aseguran que el contenido suba lo suficiente
        // antes del corte, evitando que quede texto oculto bajo el cabezal
        w(LF); w(LF); w(LF); w(LF); w(LF)
        w(ESC_CUT)

        return out.toByteArray()
    }

    // ── Helpers de formato de texto ───────────────────────────────────────────

    private fun centerPad(text: String): String {
        if (text.length >= TICKET_WIDTH) return text
        val pad = (TICKET_WIDTH - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun labelValue(label: String, value: String): String {
        val minGap = 1
        val valueStart = label.length + minGap
        val maxValue = TICKET_WIDTH - valueStart
        return if (value.length <= maxValue) {
            val spaces = TICKET_WIDTH - label.length - value.length
            label + " ".repeat(spaces.coerceAtLeast(minGap)) + value
        } else {
            // El valor es demasiado largo: primera parte al lado de la etiqueta
            // y el resto envuelto en líneas siguientes con sangría
            val firstPart = value.take(maxValue)
            val rest = value.drop(maxValue).chunked(TICKET_WIDTH - 2)
                .joinToString("\n") { "  $it" }
            val spaces = TICKET_WIDTH - label.length - firstPart.length
            label + " ".repeat(spaces.coerceAtLeast(minGap)) + firstPart + "\n" + rest
        }
    }

    private fun colHeader(): String {
        // "PRODUCTO           CANT  TOTAL"
        val prod  = "PRODUCTO"
        val cant  = "CANT"
        val total = "TOTAL"
        val midSpace = TICKET_WIDTH - prod.length - cant.length - total.length
        return prod + " ".repeat((midSpace / 2).coerceAtLeast(1)) +
               cant + " ".repeat((midSpace - midSpace / 2).coerceAtLeast(1)) + total
    }

    private fun itemRow(name: String, qty: Int, total: Double, hasMore: Boolean): String {
        val totalStr = currencyFmt.format(total)
        val qtyStr   = qty.toString().padStart(3)
        // nombre + espacios + qty + espacio + total (más separación entre cant y total)
        val fixedRight = "  $qtyStr   $totalStr"
        val available  = TICKET_WIDTH - fixedRight.length
        val safeName   = if (name.length > available) name.take(available) else name
        val pad        = available - safeName.length
        return safeName + " ".repeat(pad.coerceAtLeast(0)) + fixedRight
    }

    private fun wrapText(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            if (current.isEmpty()) {
                current = word
            } else if (current.length + 1 + word.length <= width) {
                current += " $word"
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf(text) }
    }
}



