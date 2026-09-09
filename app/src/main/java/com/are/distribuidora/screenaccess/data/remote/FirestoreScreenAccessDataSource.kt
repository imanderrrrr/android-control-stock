package com.are.distribuidora.screenaccess.data.remote

import android.util.Log
import com.are.distribuidora.screenaccess.data.local.entity.ScreenAccessEntity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Lee/observa en tiempo real `userScreenAccess/{uid}` de Cloud Firestore.
 *
 * Forma EXACTA del documento (debe coincidir con el panel web):
 *   { role: "admin" | "vendedor",
 *     screens: { inicio, inventario, pedidos, clientes, reportes, cuentasPendientes : Boolean },
 *     updatedAt, updatedBy }
 *
 * Documento ausente o `role` ausente ⇒ `role = null` en la entidad ⇒ vendedor.
 * Bandera de pantalla ausente ⇒ `null` ⇒ no restringe. Solo `false` explícito bloquea.
 */
class FirestoreScreenAccessDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ScreenAccessRemoteDataSource {
    companion object {
        const val COLLECTION = "userScreenAccess"
        private const val TAG = "ScreenAccessRemote"
    }

    override fun observe(uid: String): Flow<ScreenAccessEntity> = callbackFlow {
        val registration = firestore.collection(COLLECTION).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listener de $COLLECTION/$uid falló: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot.toEntity(uid))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchOnce(uid: String): ScreenAccessEntity =
        firestore.collection(COLLECTION).document(uid).get().await().toEntity(uid)

    private fun DocumentSnapshot?.toEntity(uid: String): ScreenAccessEntity {
        // `screens.<id>` con notación de punto: null si el doc o la clave no existen.
        fun flag(id: String): Boolean? = this?.getBoolean("screens.$id")
        return ScreenAccessEntity(
            uid = uid,
            inicio = flag("inicio"),
            inventario = flag("inventario"),
            pedidos = flag("pedidos"),
            clientes = flag("clientes"),
            reportes = flag("reportes"),
            cuentasPendientes = flag("cuentasPendientes"),
            updatedAtMillis = this?.getTimestamp("updatedAt")?.toDate()?.time,
            updatedBy = this?.getString("updatedBy"),
            role = this?.getString("role"),
        )
    }
}
