package com.are.distribuidora.presentation.product

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Guarda una imagen seleccionada (content://) en almacenamiento interno permanente.
 *
 * Regla: NO persistir content:// en BD; se convierte a un File interno y se guarda como local://<absolute_path>.
 */
object ProductImageLocalStore {

    private const val TAG = "ProductImageLocalStore"

    fun saveToInternalStorage(
        context: Context,
        sourceUri: Uri,
        productId: String,
    ): File {
        val dir = File(context.filesDir, "product_images")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val outFile = File(dir, "$productId.jpg")

        // Decodificar de forma eficiente (evita OOM).
        val resolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }

        val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight).coerceAtLeast(1)
        val targetMax = 1280
        val sampleSize = calculateInSampleSize(maxDim, targetMax)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = resolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalArgumentException("No se pudo leer la imagen seleccionada")

        // Re-encode a JPEG ~80
        FileOutputStream(outFile).use { fos ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            if (!ok) {
                throw IllegalStateException("No se pudo comprimir la imagen")
            }
        }

        Log.d(TAG, "Imagen guardada en internal storage: ${outFile.absolutePath} (sampleSize=$sampleSize)")
        return outFile
    }

    private fun calculateInSampleSize(maxDim: Int, targetMax: Int): Int {
        var inSampleSize = 1
        while (maxDim / inSampleSize > targetMax) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}

