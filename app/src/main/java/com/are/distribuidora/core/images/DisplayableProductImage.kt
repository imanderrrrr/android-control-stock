package com.are.distribuidora.core.images

/**
 * Único punto por el que la capa de presentación decide si le pide una URL a Glide.
 *
 * Junta la regla estática ([FirestoreImageUrlValidator.isUsableForDisplay]: http(s) y
 * host vivo) con el estado de la sesión ([ImageLoadFailureMemo]: ya falló con 4xx).
 * Los binds llaman a [remoteUrlOrNull] en vez de hacer `startsWith("http")` a mano.
 */
object DisplayableProductImage {

    /**
     * @return la URL remota lista para cargar, o null si NO se debe ni intentar
     *         (vacía, esquema no http, host muerto, o ya falló de forma permanente).
     */
    fun remoteUrlOrNull(imageUrl: String?): String? =
        FirestoreImageUrlValidator.usableForDisplayOrNull(imageUrl)
            ?.takeUnless { ImageLoadFailureMemo.isPermanentlyFailed(it) }

    /**
     * Variante para los binds que además aceptan fuentes locales (`local://`, `file://`,
     * `content://`) y resuelven el esquema ellos mismos.
     *
     * @return el string recortado si vale la pena intentarlo, o null si no (vacío, host
     *         muerto o fallo permanente ya conocido). Las fuentes locales pasan siempre:
     *         a un archivo del propio teléfono no se le pide nada a la red.
     */
    fun loadableSourceOrNull(imageUrl: String?): String? {
        val raw = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val isHttp = raw.startsWith("http://") || raw.startsWith("https://")
        if (isHttp && !FirestoreImageUrlValidator.isUsableForDisplay(raw)) return null
        // El memo también aplica a las fuentes locales: un archivo que ya no existe
        // (FileNotFoundException) tampoco va a aparecer por reintentarlo.
        return raw.takeUnless { ImageLoadFailureMemo.isPermanentlyFailed(it) }
    }
}
