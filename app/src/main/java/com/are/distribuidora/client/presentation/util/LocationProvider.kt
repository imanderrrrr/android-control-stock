package com.are.distribuidora.client.presentation.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

sealed class LocationResult {
    data class Success(val latitude: Double, val longitude: Double) : LocationResult()
    object PermissionDenied : LocationResult()
    object GpsDisabled : LocationResult()
    object Timeout : LocationResult()
    data class Error(val message: String) : LocationResult()
}

class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): LocationResult {
        // Verificar permisos (doble chequeo, aunque la UI debe garantizarlos)
        if (!hasLocationPermission()) {
            return LocationResult.PermissionDenied
        }

        return try {
            // Timeout de 10 segundos para obtener la ubicación
            withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine { continuation ->
                    val cancellationTokenSource = CancellationTokenSource()

                    try {
                        @SuppressLint("MissingPermission") // Ya chequeado arriba
                        val task: Task<Location> = fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.token
                        )

                        task.addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                continuation.resume(
                                    LocationResult.Success(
                                        location.latitude,
                                        location.longitude
                                    )
                                )
                            } else {
                                // location puede ser null si la ubicación está desactivada o nunca se ha calculado
                                continuation.resume(LocationResult.GpsDisabled)
                            }
                        }

                        task.addOnFailureListener { e ->
                            continuation.resume(LocationResult.Error(e.message ?: "Unknown location error"))
                        }

                        // Cancelar el token si la corrutina se cancela
                        continuation.invokeOnCancellation {
                            cancellationTokenSource.cancel()
                        }

                    } catch (e: SecurityException) {
                        continuation.resume(LocationResult.PermissionDenied)
                    } catch (e: Exception) {
                        continuation.resume(LocationResult.Error(e.message ?: "Error getting location"))
                    }
                }
            } ?: LocationResult.Timeout
        } catch (e: Exception) {
            LocationResult.Error(e.message ?: "Critical location error")
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return fineLocation == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                coarseLocation == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
