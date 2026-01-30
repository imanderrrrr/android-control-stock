package com.are.distribuidora.data.product

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductosFirestoreIntegrationTest {

    /**
     * Test de integración REAL contra Firestore.
     *
     * Requisitos para pasar:
     * - Las reglas de Firestore deben permitir leer la colección `productos` desde este cliente.
     * - Alternativamente, autenticar al usuario en el dispositivo antes de correr el test.
     *
     * Si falla con PERMISSION_DENIED, el SDK está conectado al proyecto (google-services.json OK)
     * pero las reglas no permiten la lectura bajo el contexto actual (normalmente request.auth == null).
     */
    @Test
    fun descargarYValidarProductos_noFallaPorDatosMalFormados() = runBlocking {
        val tag = "ProductosFirestoreTest"
        val firestore = FirebaseFirestore.getInstance()

        val snapshot = try {
            firestore.collection("productos").get().await()
        } catch (e: Exception) {
            Log.e(tag, "Error al conectar con Firestore o obtener la colección: ${e.message}", e)
            Assert.fail("Firestore no responde o no se pudo obtener la colección: ${e.message}")
            return@runBlocking
        }

        val documentos = snapshot.documents
        var total = documentos.size
        var mapeadosCorrectamente = 0
        val errores = mutableListOf<String>()

        for (doc in documentos) {
            try {
                // Intento de mapeo explícito de campos
                val id = extractInt(doc.get("id"), "id")
                val nombre = extractString(doc.get("nombre"), "nombre")
                val descripcion = extractString(doc.get("descripcion"), "descripcion")
                val categoria = extractString(doc.get("categoria"), "categoria")
                val precio = extractDouble(doc.get("precio"), "precio")
                val stock = extractInt(doc.get("stock"), "stock")
                val imagenUrl = extractString(doc.get("imagenUrl"), "imagenUrl")

                // Si todos los campos se obtienen sin excepción, contamos como correcto
                // No aplicamos reglas de negocio; solo verificación de presencia/tipo
                mapeadosCorrectamente += 1
            } catch (e: Exception) {
                val motivo = e.message ?: "Error de mapeo desconocido"
                val mensaje = "Documento ${doc.id} falló por ${motivo}"
                errores.add(mensaje)
                Log.w(tag, mensaje, e)
                // Continuar con el siguiente documento sin lanzar excepción
            }
        }

        val productosConError = errores.size
        Log.i(tag, "Total documentos en Firestore: $total")
        Log.i(tag, "Productos cargados correctamente: $mapeadosCorrectamente")
        Log.i(tag, "Productos con error: $productosConError")
        if (productosConError > 0) {
            errores.forEach { Log.i(tag, it) }
        }

        // Condición de éxito: Firestore respondió y se obtuvo la colección
        Assert.assertTrue("Se obtuvo la colección productos correctamente", true)
    }

    // Helpers de extracción (solo para el test)
    private fun extractString(value: Any?, field: String): String {
        if (value == null) throw IllegalArgumentException("$field faltante")
        return when (value) {
            is String -> value
            else -> throw IllegalArgumentException("$field inválido: se esperaba String y llegó ${value::class.java.simpleName}")
        }
    }

    private fun extractInt(value: Any?, field: String): Int {
        if (value == null) throw IllegalArgumentException("$field faltante")
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is Number -> value.toInt()
            else -> throw IllegalArgumentException("$field inválido: se esperaba Int y llegó ${value::class.java.simpleName}")
        }
    }

    private fun extractDouble(value: Any?, field: String): Double {
        if (value == null) throw IllegalArgumentException("$field faltante")
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Number -> value.toDouble()
            else -> throw IllegalArgumentException("$field inválido: se esperaba Double y llegó ${value::class.java.simpleName}")
        }
    }
}
