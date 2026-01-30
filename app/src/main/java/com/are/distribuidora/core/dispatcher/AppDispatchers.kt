package com.are.distribuidora.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Proveedor centralizado de dispatchers de corrutinas.
// Evita el uso directo de Dispatchers en el resto del código.
object AppDispatchers {
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
    val main: CoroutineDispatcher = Dispatchers.Main
}
