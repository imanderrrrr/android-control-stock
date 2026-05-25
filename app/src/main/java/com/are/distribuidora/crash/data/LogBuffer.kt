package com.are.distribuidora.crash.data

import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ring buffer thread-safe que mantiene los últimos [capacity] mensajes de log.
 *
 * Cuando se llena, el más antiguo se descarta. Está pensado para vaciarse
 * dentro de un reporte de crash, dándole al desarrollador el contexto exacto
 * de los segundos previos al fallo.
 *
 * NO escribe a disco — todo vive en memoria hasta que [snapshot] se llama.
 */
@Singleton
class LogBuffer @Inject constructor() {

    /** Tope por defecto: 300 entradas (~suficiente para cubrir 10-30s de actividad). */
    private val capacity: Int = DEFAULT_CAPACITY

    /**
     * Estructura interna. Sincronizada para tolerar logs simultáneos desde
     * múltiples coroutines / WorkManager workers.
     */
    private val deque = ArrayDeque<LogEntry>(capacity)
    private val lock = Any()

    /** Agrega una entrada nueva. Si se excede [capacity], descarta la más vieja. */
    fun append(entry: LogEntry) {
        synchronized(lock) {
            if (deque.size >= capacity) {
                deque.pollFirst()
            }
            deque.addLast(entry)
        }
    }

    /**
     * Devuelve una copia inmutable del buffer en este instante.
     * Se llama desde el handler de crash para volcar logs al reporte.
     */
    fun snapshot(): List<LogEntry> {
        synchronized(lock) {
            return deque.toList()
        }
    }

    /** Tamaño actual del buffer. Útil para diagnóstico/tests. */
    fun size(): Int = synchronized(lock) { deque.size }

    /** Limpia el buffer. Solo usado en tests. */
    fun clear() {
        synchronized(lock) { deque.clear() }
    }

    companion object {
        const val DEFAULT_CAPACITY = 300
    }
}
