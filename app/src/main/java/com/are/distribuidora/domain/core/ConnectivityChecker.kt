package com.are.distribuidora.domain.core

interface ConnectivityChecker {
    suspend fun isOnline(): Boolean
}
