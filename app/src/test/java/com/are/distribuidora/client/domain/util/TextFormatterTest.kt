package com.are.distribuidora.client.domain.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para TextFormatter.
 * Validan las reglas de negocio de formateo de clientes.
 */
class TextFormatterTest {

    @Test
    fun `capitalizeWords debe capitalizar cada palabra`() {
        assertEquals("Juan Pérez", TextFormatter.capitalizeWords("juan pérez"))
        assertEquals("María Del Carmen", TextFormatter.capitalizeWords("maría del carmen"))
        assertEquals("José Luis", TextFormatter.capitalizeWords("JOSÉ LUIS"))
        assertEquals("Ana", TextFormatter.capitalizeWords("ana"))
    }

    @Test
    fun `capitalizeWords debe manejar múltiples espacios`() {
        assertEquals("Juan  Pérez", TextFormatter.capitalizeWords("juan  pérez"))
        assertEquals("Ana   María", TextFormatter.capitalizeWords("ana   maría"))
    }

    @Test
    fun `capitalizeWords debe manejar texto vacío`() {
        assertEquals("", TextFormatter.capitalizeWords(""))
        assertEquals("   ", TextFormatter.capitalizeWords("   "))
    }

    @Test
    fun `formatPhoneNumber debe formatear 8 dígitos correctamente`() {
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("54428542"))
        assertEquals("1234 5678", TextFormatter.formatPhoneNumber("12345678"))
    }

    @Test
    fun `formatPhoneNumber debe normalizar números con espacios o guiones`() {
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("5442 8542"))
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("5442-8542"))
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("5 4 4 2 8 5 4 2"))
    }

    @Test
    fun `formatPhoneNumber debe truncar números más largos`() {
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("544285421"))
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("54428542999"))
    }

    @Test
    fun `formatPhoneNumber debe retornar null para números cortos`() {
        assertNull(TextFormatter.formatPhoneNumber("1234567"))
        assertNull(TextFormatter.formatPhoneNumber("123"))
        assertNull(TextFormatter.formatPhoneNumber(""))
    }

    @Test
    fun `formatPhoneNumber debe retornar null para null`() {
        assertNull(TextFormatter.formatPhoneNumber(null))
    }

    @Test
    fun `isValidPhoneNumber debe aceptar formato correcto`() {
        assertTrue(TextFormatter.isValidPhoneNumber("5442 8542"))
        assertTrue(TextFormatter.isValidPhoneNumber("12345678"))
    }

    @Test
    fun `isValidPhoneNumber debe rechazar formatos incorrectos`() {
        assertFalse(TextFormatter.isValidPhoneNumber("123"))
        assertFalse(TextFormatter.isValidPhoneNumber("1234567"))
        assertFalse(TextFormatter.isValidPhoneNumber("123456789"))
        assertFalse(TextFormatter.isValidPhoneNumber("5442-8542"))
    }

    @Test
    fun `isValidPhoneNumber debe aceptar null (campo opcional)`() {
        assertTrue(TextFormatter.isValidPhoneNumber(null))
        assertTrue(TextFormatter.isValidPhoneNumber(""))
        assertTrue(TextFormatter.isValidPhoneNumber("   "))
    }

    @Test
    fun `capitalizeWords debe preservar espacios al inicio y final`() {
        // Note: Por diseño, capitalizeWords NO hace trim, solo capitaliza
        assertEquals(" Juan ", TextFormatter.capitalizeWords(" juan "))
    }

    @Test
    fun `formatPhoneNumber debe manejar solo letras`() {
        assertNull(TextFormatter.formatPhoneNumber("abcdefgh"))
    }

    @Test
    fun `formatPhoneNumber debe extraer dígitos de texto mixto`() {
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("Tel: 5442-8542"))
        assertEquals("5442 8542", TextFormatter.formatPhoneNumber("(544) 2-8542"))
    }
}

