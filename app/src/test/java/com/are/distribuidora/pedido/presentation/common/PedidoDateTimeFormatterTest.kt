package com.are.distribuidora.pedido.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/**
 * El nombre corto del mes lo decide el locale de la plataforma ("mar" en Android,
 * "mar." en el JDK con CLDR), así que se compara con una expresión regular que
 * admite ambos; lo que se verifica aquí es día, año, separador " · " y hora HH:mm.
 */
class PedidoDateTimeFormatterTest {

    // 2026-03-05T14:05:00Z
    private val epoch = 1_772_719_500_000L

    private fun formatter(tz: String) = PedidoDateTimeFormatter(Locale("es", "GT"), TimeZone.getTimeZone(tz))

    private fun assertMatches(regex: String, actual: String) =
        assertTrue("'$actual' no cumple /$regex/", Regex(regex).matches(actual))

    @Test
    fun `formato fecha y hora en UTC`() =
        assertMatches("""5 mar\.? 2026 · 14:05""", formatter("UTC").formatDateTime(epoch))

    @Test
    fun `la hora respeta la zona horaria de Guatemala`() =
        assertMatches("""5 mar\.? 2026 · 08:05""", formatter("America/Guatemala").formatDateTime(epoch))

    @Test
    fun `hora en 24h con cero a la izquierda`() =
        // 2026-03-05T03:07:00Z
        assertMatches("""5 mar\.? 2026 · 03:07""", formatter("UTC").formatDateTime(1_772_680_020_000L))

    @Test
    fun `formato solo fecha no incluye hora`() {
        val s = formatter("UTC").formatDate(epoch)
        assertMatches("""5 mar\.? 2026""", s)
        assertFalse(s.contains("·"))
    }

    @Test
    fun `fecha y hora comparten el prefijo de fecha`() {
        val f = formatter("UTC")
        assertEquals(f.formatDate(epoch), f.formatDateTime(epoch).substringBefore(" · "))
    }
}
