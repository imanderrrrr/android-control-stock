package com.are.distribuidora.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

// Implementación para tests sin dependencia directa de kotlinx-coroutines-test.
// Permite controlar el dispatcher principal y ejecución determinista inyectando funciones.
class TestDispatchers(
    dispatcher: CoroutineDispatcher,
    private val setMainFn: ((CoroutineDispatcher) -> Unit)? = null,
    private val resetMainFn: (() -> Unit)? = null
) {
    val io: CoroutineDispatcher = dispatcher
    val default: CoroutineDispatcher = dispatcher
    val main: CoroutineDispatcher = dispatcher

    fun setMainDispatcher() {
        setMainFn?.invoke(main)
    }

    fun resetMainDispatcher() {
        resetMainFn?.invoke()
    }
}
