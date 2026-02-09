package com.are.distribuidora.client

import com.are.distribuidora.client.fakes.FakeClientRemoteDataSource

/**
 * Alias de compatibilidad para tests legacy.
 *
 * Producción NO se toca: algunos tests todavía referencian `FakeClientRemote`.
 */
typealias FakeClientRemote = FakeClientRemoteDataSource
