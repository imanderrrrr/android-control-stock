package com.are.distribuidora.core.images

import android.annotation.SuppressLint
import android.util.Log

/**
 * Validador que garantiza que NUNCA se escriba una ruta local en Firestore.
 *
 * Regla G: si el string comienza con local://, file://, content://
 * o contiene "/data/user/0" => NO escribir. Lanza error y loguea.
 */
object FirestoreImageUrlValidator {

    private const val TAG = "ImageSync"

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
}



