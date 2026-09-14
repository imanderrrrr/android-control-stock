package com.are.distribuidora.core.images

import android.annotation.SuppressLint
import android.util.Log

/**
 * Validador que garantiza que NUNCA se escriba una ruta local en Firestore.
 *
 * Regla G: si el string comienza con local://, file://, content://
 * o contiene "/data/user/0" => NO escribir. Lanza error y loguea.
 *
 * Además centraliza la regla de "URL utilizable para mostrar" ([isUsableForDisplay]),
 * que es la que deciden todos los puntos del camino de la imagen: el mapeo del
 * producto remoto, el guard de preservación de `imageUrl` del downsync y los binds
 * de Glide. Antes cada sitio hacía su propio `startsWith("http")`, así que una URL
 * muerta pasaba por todos.
 */
object FirestoreImageUrlValidator {

    private const val TAG = "ImageSync"

    /**
     * Hosts que ya NO devuelven una imagen y por lo tanto no vale la pena pedir.
     *
     * `drive.google.com` está aquí porque 250 de los 450 productos guardan un
     * `imageUrl` con la forma `https://drive.google.com/uc?export=view&id=…`, un
     * endpoint que Google retiró: responde **404 siempre**. Se comprobaron también
     * `uc?export=download` (404) y `thumbnail?id=…` (200 pero con un text/html de
     * 918 KB, la misma página genérica para todos: no es una imagen). Esas fotos ya
     * no existen; no hay nada que migrar. `drive.usercontent.google.com` es el host
     * al que Drive redirige hoy y tampoco sirve contenido sin sesión.
     *
     * **Lista de BLOQUEADOS y no de permitidos, a propósito.** Una lista de
     * permitidos (solo `firebasestorage.googleapis.com`) sería más estricta, pero su
     * modo de fallo es el peor de los dos: esconder una foto que SÍ carga, en
     * silencio y sin rastro en logcat, en cuanto alguien sirva imágenes desde otro
     * sitio — un CDN, el bucket nuevo `*.firebasestorage.app`, `storage.googleapis.com`
     * o el propio panel web. El modo de fallo de la lista de bloqueados es que
     * aparezca un host muerto nuevo, y eso degrada exactamente al comportamiento de
     * hoy (placeholder + un reintento que el memo de fallos permanentes corta), no a
     * una regresión. Añadir un host a esta lista es una línea.
     */
    private val UNUSABLE_IMAGE_HOSTS = listOf(
        "drive.google.com",
        "drive.usercontent.google.com",
    )

    /**
     * Valida que la URL sea segura para escribir en Firestore.
     * @throws IllegalArgumentException si la URL contiene una ruta local.
     */
    @SuppressLint("SdCardPath")
    fun validateForFirestore(imageUrl: String?) {
        if (imageUrl == null) return

        val isLocalUrl = imageUrl.startsWith("local://") ||
                imageUrl.startsWith("file://") ||
                imageUrl.startsWith("content://") ||
                imageUrl.contains("/data/user/0") ||
                imageUrl.contains("/data/data/")

        if (isLocalUrl) {
            val errorMsg = "BLOCKED: Attempted to write local URI to Firestore: $imageUrl"
            Log.e(TAG, errorMsg)
            throw IllegalArgumentException(errorMsg)
        }
    }

    /**
     * Returns true if the URL is safe to persist remotely (null or https).
     */
    fun isSafeForRemote(imageUrl: String?): Boolean {
        if (imageUrl == null) return true
        return imageUrl.startsWith("http://") || imageUrl.startsWith("https://")
    }

    /**
     * True si vale la pena pedirle esta URL a la red para mostrarla.
     *
     * Exige http(s) — las fuentes locales (`local://`, `file://`, `content://`) se
     * resuelven por otra rama en los binds — y que el host no esté en
     * [UNUSABLE_IMAGE_HOSTS]. Es una función pura: el estado de "esta URL ya falló"
     * vive en [ImageLoadFailureMemo].
     */
    fun isUsableForDisplay(imageUrl: String?): Boolean {
        val url = imageUrl?.trim()
        if (url.isNullOrEmpty()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return UNUSABLE_IMAGE_HOSTS.none { host -> url.hostMatches(host) }
    }

    /**
     * Devuelve la URL ya recortada si [isUsableForDisplay], o null.
     *
     * Es el reemplazo de los `imageUrl?.trim()?.takeIf { it.startsWith("http") }`
     * repetidos por toda la capa de presentación y por el sync.
     */
    fun usableForDisplayOrNull(imageUrl: String?): String? =
        imageUrl?.trim()?.takeIf { isUsableForDisplay(it) }

    /**
     * True si la URL es http(s) pero apunta a un host que no sirve imágenes.
     * Sirve para loguear/contar el caso sin confundirlo con "no tiene imagen".
     */
    fun isUnusableHost(imageUrl: String?): Boolean {
        val url = imageUrl?.trim() ?: return false
        return UNUSABLE_IMAGE_HOSTS.any { host -> url.hostMatches(host) }
    }

    /**
     * Compara el HOST de la URL, no la cadena completa: `contains("drive.google.com")`
     * también daría positivo con `https://cdn.midominio.com/?ref=drive.google.com`.
     */
    private fun String.hostMatches(host: String): Boolean {
        val afterScheme = substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isEmpty()) return false
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostOnly = authority.substringAfterLast('@').substringBefore(':')
        return hostOnly.equals(host, ignoreCase = true)
    }
}
