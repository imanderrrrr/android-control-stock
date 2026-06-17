package com.are.distribuidora.screenaccess.data.remote

import android.util.Log
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Lee/observa en tiempo real `userScreenAccess/{uid}` de Cloud Firestore.
 *
 * Forma EXACTA del documento (debe coincidir con el panel web):
 *   { screens: { inicio, inventario, pedidos, clientes, reportes, cuentasPendientes : Boolean },
 *     updatedAt, updatedBy }
 *
 * Default-allow: documento ausente o clave ausente ⇒ `null` en la entidad ⇒
 * PERMITIDO. Solo `false` explícito bloquea.
 */
class FirestoreScreenAccessDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    companion object {
        const val COLLECTION = "userScreenAccess"
        private const val TAG = "ScreenAccessRemote"
    }

    /**
     * Listener en tiempo real del documento del usuario. Emite una entidad lista
     * para cachear cada vez que el documento cambia. Cierra el flujo con error si
     * Firestore reporta uno (el repositorio lo trata como "sin red" y sigue con la
     * cache local).
     */
    fun observe(uid: String): Flow<ScreenAccessEntity> = callbackFlow {
        val registration = firestore.collection(COLLECTION).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listener de $COLLECTION/$uid falló: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                // `screens.<id>` con notación de punto: null si el doc o la clave no existen.
                fun flag(id: String): Boolean? = snapshot?.getBoolean("screens.$id")
                trySend(
                    ScreenAccessEntity(
                        uid = uid,
                        inicio = flag("inicio"),
                        inventario = flag("inventario"),
                        pedidos = flag("pedidos"),
                        clientes = flag("clientes"),
                        reportes = flag("reportes"),
                        cuentasPendientes = flag("cuentasPendientes"),
                        updatedAtMillis = snapshot?.getTimestamp("updatedAt")?.toDate()?.time,
                        updatedBy = snapshot?.getString("updatedBy"),
                    )
                )
            }
        awaitClose { registration.remove() }
    }
}
