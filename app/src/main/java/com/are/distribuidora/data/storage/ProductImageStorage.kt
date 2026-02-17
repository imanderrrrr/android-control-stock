package com.are.distribuidora.data.storage

import java.io.File

interface ProductImageStorage {
    /**
     * Sube la imagen principal del producto y retorna el downloadUrl (https://...)
     */
    suspend fun uploadMainImage(productId: String, localFile: File): String
}

