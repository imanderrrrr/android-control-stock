package com.are.distribuidora.core.firebase

import android.util.Log
import com.are.distribuidora.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Punto único para apuntar Auth y Firestore a los emuladores locales en builds DEBUG compilados
 * con `-PuseFirebaseEmulator=true`. En cualquier otro caso devuelve las instancias normales
 * (proyecto real). Idempotente: `useEmulator` solo puede llamarse antes del primer uso.
 *
 * Nota: hay código legacy que usa `FirebaseAuth.getInstance()` directamente (p. ej. el uid en
 * FirestorePedidoDataSource); como `useEmulator` se aplica a la instancia singleton, también queda
 * cubierto una vez que este objeto la inicializa (Hilt lo hace al arrancar).
 */
object FirebaseEmulators {
    private const val TAG = "FirebaseEmulators"
    @Volatile private var configured = false

    val enabled: Boolean get() = BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATOR

    fun firestore(): FirebaseFirestore {
        configure()
        return FirebaseFirestore.getInstance()
    }

    fun auth(): FirebaseAuth {
        configure()
        return FirebaseAuth.getInstance()
    }

    @Synchronized
    private fun configure() {
        if (configured || !enabled) return
        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        try {
            FirebaseAuth.getInstance().useEmulator(host, 9099)
            FirebaseFirestore.getInstance().useEmulator(host, 8080)
            Log.w(TAG, "⚠ Firebase apuntando a EMULADORES en $host (auth:9099, firestore:8080). No es producción.")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "useEmulator no aplicado (instancia ya en uso): ${e.message}")
        }
        configured = true
    }
}
