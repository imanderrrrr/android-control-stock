package com.are.distribuidora.data.venta

import android.util.Log
import com.are.distribuidora.domain.model.Sale
import com.are.distribuidora.domain.sale.SaleRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio remoto (Firestore) para ventas usando el modelo soberano de dominio.
 *
 * ESTADO ACTUAL (2026-01):
 * - Este repositorio está intencionalmente **DESCONEXO** del grafo de DI.
 * - No debe usarse como fuente de verdad. La app consume ventas desde Room (ver `RoomSaleRepository`).
 *
 * POR QUÉ:
 * - Devolver vacío o simular comportamiento ocultaría errores (UI sin datos silenciosa).
 * - Preferimos fallar de forma explícita si alguien lo utiliza por accidente.
 *
 * FUTURO (sync Room -> Firestore):
 * - Este tipo puede evolucionar a adaptador remoto.
 * - Idealmente el flujo de sincronización vivirá en un componente explícito
 *   (p. ej. `SyncSalesUseCase` + `SaleSyncRepository`) que lea de Room y suba a Firestore.
 */
class SaleRepositoryFirestore(
    private val firestore: FirebaseFirestore,
) : SaleRepository {

    override fun getSales(): Flow<List<Sale>> {
        // Si esto aparece en logs, alguien inyectó/usó el repositorio remoto por error.
        Log.e(
            "SaleRepositoryFirestore",
            "USO ACCIDENTAL: getSales() no está implementado y este repositorio debe permanecer desconectado. " +
                "La fuente actual de ventas es RoomSaleRepository.",
        )

        // Fail-fast para evitar comportamiento silencioso.
        error(
            "SaleRepositoryFirestore.getSales() no está implementado. " +
                "No uses este repositorio como fuente de ventas hasta implementar la lectura/remoto. " +
                "Actualmente la app debe consumir ventas desde Room (RoomSaleRepository).",
        )
    }
}
