package com.are.distribuidora.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la regla de redondeo al múltiplo de Q 0.25 más cercano.
 *
 * Denominaciones circulantes en Guatemala: 0, 25, 50, 75 centavos.
 * Tabla de redondeo por bucket de centavos:
 *   0 – 12  → .00
 *  13 – 37  → .25
 *  38 – 62  → .50
 *  63 – 87  → .75
 *  88 – 99  → siguiente entero (.00 del próximo quetzal)
 */
class RoundToQuarterQuetzalUseCaseTest {

    // ── Casos proporcionados por el usuario ──────────────────────────────────

    @Test
    fun `34_18 redondea a 34_25`() {
        assertEquals(34.25, RoundToQuarterQuetzalUseCase(34.18), 1e-9)
    }

    @Test
    fun `35_41 redondea a 35_50`() {
        assertEquals(35.50, RoundToQuarterQuetzalUseCase(35.41), 1e-9)
    }

    @Test
    fun `38_89 redondea a 39_00`() {
        assertEquals(39.00, RoundToQuarterQuetzalUseCase(38.89), 1e-9)
    }

    // ── Fronteras de cada bucket ─────────────────────────────────────────────

    @Test
    fun `0_00 se mantiene en 0_00`() {
        assertEquals(0.00, RoundToQuarterQuetzalUseCase(0.00), 1e-9)
    }

    @Test
    fun `0_12 redondea a 0_00 (limite inferior del bucket_25)`() {
        assertEquals(0.00, RoundToQuarterQuetzalUseCase(0.12), 1e-9)
    }

    @Test
    fun `0_13 redondea a 0_25 (entra al bucket_25)`() {
        assertEquals(0.25, RoundToQuarterQuetzalUseCase(0.13), 1e-9)
    }

    @Test
    fun `0_37 redondea a 0_25 (limite superior del bucket_25)`() {
        assertEquals(0.25, RoundToQuarterQuetzalUseCase(0.37), 1e-9)
    }

    @Test
    fun `0_38 redondea a 0_50 (entra al bucket_50)`() {
        assertEquals(0.50, RoundToQuarterQuetzalUseCase(0.38), 1e-9)
    }

    @Test
    fun `0_62 redondea a 0_50 (limite superior del bucket_50)`() {
        assertEquals(0.50, RoundToQuarterQuetzalUseCase(0.62), 1e-9)
    }

    @Test
    fun `0_63 redondea a 0_75 (entra al bucket_75)`() {
        assertEquals(0.75, RoundToQuarterQuetzalUseCase(0.63), 1e-9)
    }

    @Test
    fun `0_87 redondea a 0_75 (limite superior del bucket_75)`() {
        assertEquals(0.75, RoundToQuarterQuetzalUseCase(0.87), 1e-9)
    }

    @Test
    fun `0_88 redondea a 1_00 (sube al siguiente entero)`() {
        assertEquals(1.00, RoundToQuarterQuetzalUseCase(0.88), 1e-9)
    }

    @Test
    fun `0_99 redondea a 1_00 (sube al siguiente entero)`() {
        assertEquals(1.00, RoundToQuarterQuetzalUseCase(0.99), 1e-9)
    }

    // ── Valores exactos (idempotencia) ───────────────────────────────────────

    @Test
    fun `valor exacto 0_25 no cambia`() {
        assertEquals(0.25, RoundToQuarterQuetzalUseCase(0.25), 1e-9)
    }

    @Test
    fun `valor exacto 0_50 no cambia`() {
        assertEquals(0.50, RoundToQuarterQuetzalUseCase(0.50), 1e-9)
    }

    @Test
    fun `valor exacto 0_75 no cambia`() {
        assertEquals(0.75, RoundToQuarterQuetzalUseCase(0.75), 1e-9)
    }

    @Test
    fun `valor exacto 45_75 no cambia`() {
        assertEquals(45.75, RoundToQuarterQuetzalUseCase(45.75), 1e-9)
    }

    // ── Casos con enteros grandes ────────────────────────────────────────────

    @Test
    fun `100_00 se mantiene en 100_00`() {
        assertEquals(100.00, RoundToQuarterQuetzalUseCase(100.00), 1e-9)
    }

    @Test
    fun `999_99 redondea a 1000_00`() {
        assertEquals(1000.00, RoundToQuarterQuetzalUseCase(999.99), 1e-9)
    }

    @Test
    fun `999_87 redondea a 999_75`() {
        assertEquals(999.75, RoundToQuarterQuetzalUseCase(999.87), 1e-9)
    }

    // ── Idempotencia: aplicar dos veces da el mismo resultado ────────────────

    @Test
    fun `redondeo es idempotente`() {
        val casos = doubleArrayOf(0.13, 0.37, 0.88, 34.18, 35.41, 38.89, 1234.56)
        for (caso in casos) {
            val unaVez = RoundToQuarterQuetzalUseCase(caso)
            val dosVeces = RoundToQuarterQuetzalUseCase(unaVez)
            assertEquals(
                "Redondear dos veces $caso debe dar el mismo resultado",
                unaVez,
                dosVeces,
                1e-9,
            )
        }
    }

    // ── Suma de múltiplos de 0.25 cae en múltiplo de 0.25 ────────────────────

    @Test
    fun `suma de valores redondeados sigue siendo multiplo de 0_25`() {
        val items = listOf(0.25, 0.50, 0.75, 1.00, 34.25, 35.50)
        val suma = items.sum()
        val redondeado = RoundToQuarterQuetzalUseCase(suma)
        // La diferencia debe ser cero (o un error de punto flotante despreciable)
        assertEquals(suma, redondeado, 1e-9)
    }
}
