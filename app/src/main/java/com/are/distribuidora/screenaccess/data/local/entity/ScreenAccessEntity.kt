package com.are.distribuidora.screenaccess.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache local (Room) de la configuración de acceso por pantalla de un usuario,
 * espejo de `userScreenAccess/{uid}` en Firestore. Permite que el control de
 * acceso funcione offline con la última configuración conocida.
 *
 * Cada bandera es NULLABLE a propósito: `null` = clave ausente = PERMITIDO
 * (regla default-allow); `false` = denegado; `true` = permitido explícito.
 */
@Entity(tableName = "screen_access")
data class ScreenAccessEntity(
    @PrimaryKey val uid: String,
    val inicio: Boolean?,
    val inventario: Boolean?,
    val pedidos: Boolean?,
    val clientes: Boolean?,
    val reportes: Boolean?,
    val cuentasPendientes: Boolean?,
    val updatedAtMillis: Long?,
    val updatedBy: String?,
)
