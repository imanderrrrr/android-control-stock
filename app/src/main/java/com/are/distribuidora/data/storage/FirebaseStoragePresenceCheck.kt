package com.are.distribuidora.data.storage

// Este archivo existe solo para verificar si el SDK de Firebase Storage está presente en el classpath.
// Si compila, podemos implementar FirebaseProductImageStorage sin agregar dependencias.
@Suppress("unused")
internal object FirebaseStoragePresenceCheck {
    fun classpathCheck(): String = try {
        Class.forName("com.google.firebase.storage.FirebaseStorage")
        "present"
    } catch (e: ClassNotFoundException) {
        "missing"
    }
}

