package com.are.distribuidora.data.storage

// Firebase Storage SDK no está en el classpath de este proyecto actualmente.
// Mantener este archivo sin referencia directa evita romper la compilación.
@Suppress("unused")
internal object FirebaseStorageCompileCheck {
    fun note(): String = "firebase-storage-sdk-missing"
}
