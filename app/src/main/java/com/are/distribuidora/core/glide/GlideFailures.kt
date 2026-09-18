package com.are.distribuidora.core.glide

import com.bumptech.glide.load.HttpException
import com.bumptech.glide.load.engine.GlideException
import java.io.FileNotFoundException

/** Clasificación de los fallos de Glide en permanentes y transitorios. */
object GlideFailures {

    /**
     * True si no tiene sentido reintentar esta carga: el servidor contestó que el
     * recurso no está (HTTP 4xx) o el archivo local no existe.
     *
     * Todo lo demás — sin red, 5xx, timeout, DNS — es transitorio y SÍ se reintenta,
     * que es lo que hace que la foto aparezca cuando vuelve la cobertura.
     */
    fun isPermanent(e: GlideException?): Boolean {
        if (e == null) return false
        return e.rootCauses.any { cause ->
            when (cause) {
                is HttpException -> cause.statusCode in 400..499
                is FileNotFoundException -> true
                else -> false
            }
        }
    }
}
