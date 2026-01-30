package com.are.distribuidora.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

// Proveedor de dispatchers para pruebas, con control del Main mediante funciones inyectables.
class TestDispatchers(
    dispatcher: CoroutineDispatcher,
    private val setMainFn: ((CoroutineDispatcher) -> Unit)? = null,
    private val resetMainFn: (() -> Unit)? = null
) {
    val io: CoroutineDispatcher = dispatcher
    val default: CoroutineDispatcher = dispatcher
    val main: CoroutineDispatcher = dispatcher

    // Configura el dispatcher principal si se proveyó una función.
    fun setMainDispatcher() {
        setMainFn?.invoke(main)
    }

    // Restaura el dispatcher principal si se proveyó una función.
    fun resetMainDispatcher() {
        resetMainFn?.invoke()
    }
}
