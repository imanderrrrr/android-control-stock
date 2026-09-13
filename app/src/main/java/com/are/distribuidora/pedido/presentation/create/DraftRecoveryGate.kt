package com.are.distribuidora.pedido.presentation.create

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evita que el diálogo de recuperación salga dos veces en la misma sesión.
 *
 * Vive mientras vive el PROCESO (es un `@Singleton` de Hilt), que es justo la
 * semántica pedida: si el vendedor ignora el diálogo tocando fuera, no vuelve a
 * aparecer en esa sesión, pero como el borrador sigue en Room se le vuelve a
 * ofrecer en el siguiente arranque. Si el proceso muere, el flag muere con él.
 *
 * No se persiste a propósito: persistirlo convertiría "lo ignoré una vez" en
 * "no me lo vuelvas a ofrecer nunca", que no es lo que se quiere.
 */
@Singleton
class DraftRecoveryGate @Inject constructor() {

    @Volatile
    private var offered = false

    /** true la primera vez que se llama en esta sesión; false después. */
    fun tryConsume(): Boolean {
        if (offered) return false
        offered = true
        return true
    }
}
