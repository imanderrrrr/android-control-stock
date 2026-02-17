package com.are.distribuidora.data.storage

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseProductImageStorage @Inject constructor(
    private val firebaseStorage: FirebaseStorage
) : ProductImageStorage {

    override suspend fun uploadMainImage(productId: String, localFile: File): String {
        require(localFile.exists()) { "Archivo de imagen no existe: ${localFile.absolutePath}" }

        val path = "products/$productId/main.jpg"
        Log.d(TAG, "Uploading product image to $path from ${localFile.absolutePath}")

        val ref = firebaseStorage.reference.child(path)
        ref.putFile(Uri.fromFile(localFile)).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        Log.d(TAG, "Upload success productId=$productId url=$downloadUrl")
        return downloadUrl
    }

    private companion object {
        private const val TAG = "FirebaseProductImage"
    }
}
