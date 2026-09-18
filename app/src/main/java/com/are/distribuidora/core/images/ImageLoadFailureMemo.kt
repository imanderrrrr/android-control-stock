package com.are.distribuidora.core.images

import android.util.Log

/**
 * Recuerda, SOLO durante esta ejecución del proceso, las URLs de imagen cuyo fallo es
 * permanente, para no volver a pedirlas en cada bind de la fila.
 *
 * Por qué hace falta: Glide no cachea los fallos. Una URL que devuelve 404 se vuelve a
 * pedir cada vez que el `RecyclerView` vuelve a enlazar la fila, y en una lista de 450
 * productos eso son decenas de peticiones muertas por sesión — exactamente lo que se ve
 * en los reportes de crash. Glide NO lo cubre con configuración (`.error()` pinta el
 * placeholder pero no evita la petición siguiente), así que este memo es lo mínimo que
 * hace falta; no es una capa de caché de imágenes, no guarda bytes ni toca disco.
 *
 * **Solo fallos permanentes (HTTP 4xx).** Un fallo transitorio — sin red, 5xx, timeout —
 * SÍ debe reintentarse: es lo que hace que la foto aparezca cuando el vendedor recupera
 * cobertura. Marcar esos como muertos dejaría el catálogo sin fotos hasta reiniciar la
 * app. La clasificación la hace `GlideFailures.isPermanent`.
 *
 * Se olvida al morir el proceso a propósito: si la foto se vuelve a subir, la URL de
 * descarga de Firebase Storage cambia (lleva un token nuevo), así que no hay riesgo de
 * quedarse pegado a un 404 viejo; y si alguien arregla el servidor, basta reabrir la app.
 */
object ImageLoadFailureMemo {

    private const val TAG = "ImageSync"

    /** Cota para que una sesión larga no acumule memoria sin límite. */
    private const val MAX_ENTRIES = 512

    private val failed = object : LinkedHashMap<String, Unit>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun isPermanentlyFailed(url: String?): Boolean {
        val key = url?.trim() ?: return false
        return failed.containsKey(key)
    }

    @Synchronized
    fun rememberPermanentFailure(url: String?) {
        val key = url?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (failed.put(key, Unit) == null) {
            Log.d(TAG, "ImageLoadFailureMemo: no se volverá a pedir en esta sesión: $key")
        }
    }

    /** Visible para tests. */
    @Synchronized
    fun reset() = failed.clear()

    /** Visible para tests. */
    @Synchronized
    fun size(): Int = failed.size
}
