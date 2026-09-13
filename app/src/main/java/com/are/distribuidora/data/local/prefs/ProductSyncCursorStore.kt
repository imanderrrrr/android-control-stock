package com.are.distribuidora.data.local.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watermark de BAJADA del catálogo de productos: el `updatedAt` de servidor hasta el cual este
 * teléfono ya descargó TODOS los documentos.
 *
 * Por qué no se deriva de la tabla `products` (que es lo que hacía 4.1 con
 * `MAX(updatedAt)` de las filas SYNCED):
 *
 *   `MAX(updatedAt)` responde "¿cuál es el sello más nuevo que tengo?", no "¿hasta dónde bajé?".
 *   Al SUBIR un producto, el teléfono adopta el `updatedAt` que el servidor acaba de asignarle
 *   (T3) para no envenenar el cursor con su reloj local. Pero si entre su última bajada (T1) y esa
 *   subida otro teléfono escribió un documento con sello intermedio (T2 — por ejemplo un vale de
 *   entrada que mueve `stock` con `FieldValue.increment`), el máximo salta de T1 a T3 **sin haber
 *   bajado nunca T2**, y la consulta `updatedAt >= T3` deja ese documento invisible para siempre:
 *   el stock nuevo no llega y el watermark ya no vuelve a moverse.
 *
 * Aquí el watermark solo lo mueve una bajada realmente aplicada, así que nunca puede adelantarse
 * a lo descargado. Cuando no hay valor guardado se arranca desde 0 (bajada completa del catálogo):
 * es barato —cientos de documentos— y cura de una vez a los teléfonos cuyo cursor ya saltó.
 */
interface ProductSyncCursorStore {

    /** Watermark guardado, o null si este teléfono todavía no completó ninguna bajada con cursor. */
    fun get(): Long?

    /** Avanza el watermark. Nunca retrocede. */
    fun advanceTo(value: Long)
}

@Singleton
class SharedPrefsProductSyncCursorStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProductSyncCursorStore {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun get(): Long? =
        if (prefs.contains(KEY_WATERMARK)) prefs.getLong(KEY_WATERMARK, 0L) else null

    override fun advanceTo(value: Long) {
        val current = get() ?: 0L
        if (value <= current) return
        prefs.edit().putLong(KEY_WATERMARK, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "product_sync"
        const val KEY_WATERMARK = "downsync_watermark"
    }
}

/** Implementación en memoria para tests y para árboles sin Context. */
class InMemoryProductSyncCursorStore(initial: Long? = null) : ProductSyncCursorStore {
    private var value: Long? = initial
    override fun get(): Long? = value
    override fun advanceTo(value: Long) {
        if (value > (this.value ?: 0L)) this.value = value
    }
}
