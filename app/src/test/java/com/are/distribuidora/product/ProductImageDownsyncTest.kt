package com.are.distribuidora.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.data.local.DistribuidoraDatabase
import com.are.distribuidora.data.local.SyncStatus
import com.are.distribuidora.data.local.dao.ProductDao
import com.are.distribuidora.data.local.entity.ProductEntity
import com.are.distribuidora.data.remote.model.RemoteProduct
import com.are.distribuidora.data.remote.product.ProductRemoteDataSource
import com.are.distribuidora.data.repository.ProductSyncRepositoryImpl
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El guard de preservación de `imageUrl` en la bajada de productos.
 *
 * El guard existe por una razón buena: si el sync de productos subió con `merge()` sin
 * enviar `imageUrl` mientras `UploadPendingProductImagesWorker` ya había puesto la URL
 * real en Room, un remoto sin imagen no debe borrarla.
 *
 * Pero tal como estaba (`existing?.imageUrl?.takeIf { it.startsWith("http") }`) también
 * conservaba las URLs MUERTAS de `drive.google.com`: poner `imageUrl` a null en Firestore
 * no servía de nada, porque cada bajada resucitaba la URL local y el teléfono seguía
 * pidiéndole 404 a Drive para siempre. Estas dos pruebas fijan las dos mitades.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductImageDownsyncTest {

    private val storageUrl =
        "https://firebasestorage.googleapis.com/v0/b/distribuidora-3f639.appspot.com/o/products%2Fp1.jpg?alt=media&token=abc"
    private val driveUrl = "https://drive.google.com/uc?export=view&id=1AbCdEf"

    private lateinit var db: DistribuidoraDatabase
    private lateinit var dao: ProductDao
    private lateinit var remote: FakeRemote
    private lateinit var syncRepo: ProductSyncRepositoryImpl

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DistribuidoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
        remote = FakeRemote()
        syncRepo = ProductSyncRepositoryImpl(
            remote = remote,
            local = dao,
            database = db,
            imageStorage = mockk(relaxed = true),
            pendingUploadDao = db.pendingUploadDao(),
            movementDao = db.stockMovementDao(),
            cursorStore = com.are.distribuidora.data.local.prefs.InMemoryProductSyncCursorStore(),
        )
    }

    @After
    fun tearDown() = db.close()

    // ── El guard sigue protegiendo el caso legítimo ──────────────────────────────

    @Test
    fun `remoto sin imagen y local de Storage valida - se conserva`() = runTest {
        dao.insert(syncedEntity(id = "p1", imageUrl = storageUrl, updatedAt = 1000L))
        remote.storage["p1"] = remoteProduct(id = "p1", imageUrl = null, updatedRemoteAt = 2000L, name = "P1-renombrado")

        syncRepo.syncDownstream()

        val after = dao.getById("p1")!!
        assertEquals(
            "un merge parcial sin imageUrl NO debe borrar la URL de Storage que subió el worker",
            storageUrl, after.imageUrl,
        )
        assertEquals("los demás campos sí se actualizan", "P1-renombrado", after.name)
    }

    // ── …pero ya no resucita una URL muerta ─────────────────────────────────────

    @Test
    fun `remoto sin imagen y local de Drive - se limpia`() = runTest {
        dao.insert(syncedEntity(id = "p2", imageUrl = driveUrl, updatedAt = 1000L))
        remote.storage["p2"] = remoteProduct(id = "p2", imageUrl = null, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        assertNull(
            "el script del panel pone imageUrl a null: el teléfono TIENE que dejar de pedirla",
            dao.getById("p2")!!.imageUrl,
        )
    }

    @Test
    fun `una URL de Drive que llega del remoto nunca entra en Room`() = runTest {
        remote.storage["p3"] = remoteProduct(id = "p3", imageUrl = driveUrl, updatedRemoteAt = 1000L)

        syncRepo.syncDownstream()

        assertNull("ni en el insert inicial", dao.getById("p3")!!.imageUrl)
    }

    @Test
    fun `una URL de Storage que llega del remoto se adopta`() = runTest {
        dao.insert(syncedEntity(id = "p4", imageUrl = null, updatedAt = 1000L))
        remote.storage["p4"] = remoteProduct(id = "p4", imageUrl = storageUrl, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        assertEquals(storageUrl, dao.getById("p4")!!.imageUrl)
    }

    @Test
    fun `el remoto reemplaza una URL muerta local por una de Storage`() = runTest {
        dao.insert(syncedEntity(id = "p5", imageUrl = driveUrl, updatedAt = 1000L))
        remote.storage["p5"] = remoteProduct(id = "p5", imageUrl = storageUrl, updatedRemoteAt = 2000L)

        syncRepo.syncDownstream()

        assertEquals("la foto re-subida gana", storageUrl, dao.getById("p5")!!.imageUrl)
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private fun syncedEntity(id: String, imageUrl: String?, updatedAt: Long, name: String = "P-$id") =
        ProductEntity(
            id = id, name = name, description = null, category = null, price = 10.0,
            imageUrl = imageUrl, imageLocalUri = null, barcode = null,
            stock = 10, isActive = true, isDeleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = updatedAt, updatedAt = updatedAt, lastSyncedAt = updatedAt,
        )

    private fun remoteProduct(
        id: String,
        imageUrl: String?,
        updatedRemoteAt: Long,
        name: String = "P-$id",
    ) = RemoteProduct(
        id = id, name = name, description = null, category = null, price = 10.0,
        imageUrl = imageUrl, barcode = null,
        stock = 10, isActive = true, isDeleted = false,
        createdRemoteAt = updatedRemoteAt, updatedRemoteAt = updatedRemoteAt,
    )

    private class FakeRemote : ProductRemoteDataSource {
        val storage = mutableMapOf<String, RemoteProduct>()

        override fun fetchProductsFlow(timestamp: Long, lastId: String?, batchSize: Long): Flow<List<RemoteProduct>> = flow {
            val list = storage.values
                .filter { (it.updatedRemoteAt ?: 0L) >= timestamp }
                .sortedWith(compareBy({ it.updatedRemoteAt ?: 0L }, { it.id }))
            if (list.isNotEmpty()) emit(list)
        }

        override suspend fun uploadProduct(product: RemoteProduct) {
            storage[product.id] = product
        }

        override suspend fun fetchProductById(id: String): RemoteProduct? = storage[id]

        override suspend fun softDeleteProduct(id: String, timestamp: Long) {
            storage[id]?.let { storage[id] = it.copy(isDeleted = true, updatedRemoteAt = timestamp) }
        }
    }
}
