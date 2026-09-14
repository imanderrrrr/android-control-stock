package com.are.distribuidora.core.images

import com.are.distribuidora.core.glide.GlideFailures
import com.bumptech.glide.load.HttpException
import com.bumptech.glide.load.engine.GlideException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException
import java.net.SocketTimeoutException

/**
 * La regla de "URL utilizable para mostrar" y el memo de fallos permanentes.
 *
 * Contexto: 250 de los 450 productos guardaban un `imageUrl` de
 * `https://drive.google.com/uc?export=view&id=…`, un endpoint retirado que devuelve 404
 * siempre. Cada bind de la fila lo volvía a pedir: decenas de peticiones muertas por
 * sesión, datos móviles y batería de los vendedores. Las 193 de
 * `firebasestorage.googleapis.com` SÍ funcionan y no se pueden tocar.
 */
class DisplayableProductImageTest {

    private val storageUrl =
        "https://firebasestorage.googleapis.com/v0/b/distribuidora-3f639.appspot.com/o/products%2Fp1.jpg?alt=media&token=abc"
    private val driveUrl = "https://drive.google.com/uc?export=view&id=1AbCdEf"

    @Before
    fun setUp() = ImageLoadFailureMemo.reset()

    @After
    fun tearDown() = ImageLoadFailureMemo.reset()

    // ── La regla estática ────────────────────────────────────────────────────────

    @Test
    fun `una URL de Firebase Storage es utilizable`() {
        assertTrue(FirestoreImageUrlValidator.isUsableForDisplay(storageUrl))
        assertEquals(storageUrl, FirestoreImageUrlValidator.usableForDisplayOrNull(storageUrl))
    }

    @Test
    fun `una URL de Google Drive NO es utilizable`() {
        assertFalse(FirestoreImageUrlValidator.isUsableForDisplay(driveUrl))
        assertNull(FirestoreImageUrlValidator.usableForDisplayOrNull(driveUrl))
        assertTrue(FirestoreImageUrlValidator.isUnusableHost(driveUrl))
    }

    @Test
    fun `las demas formas de Drive tampoco pasan`() {
        listOf(
            "https://drive.google.com/uc?export=download&id=1AbCdEf",
            "https://drive.google.com/thumbnail?id=1AbCdEf&sz=w1000",
            "http://drive.google.com/uc?export=view&id=1AbCdEf",
            "https://DRIVE.GOOGLE.COM/uc?export=view&id=1AbCdEf",
            "https://drive.usercontent.google.com/download?id=1AbCdEf",
        ).forEach { url ->
            assertFalse("debería bloquearse: $url", FirestoreImageUrlValidator.isUsableForDisplay(url))
        }
    }

    @Test
    fun `null vacio y esquemas no http no son utilizables para mostrar`() {
        listOf(null, "", "   ", "local:///data/user/0/p.jpg", "content://media/1", "file:///tmp/p.jpg", "ftp://x/p.jpg")
            .forEach { url ->
                assertFalse("no debería ser utilizable: $url", FirestoreImageUrlValidator.isUsableForDisplay(url))
            }
    }

    @Test
    fun `se compara el host y no la cadena completa`() {
        // Una URL de otro dominio que MENCIONA drive.google.com en la query sigue siendo válida:
        // por eso la regla mira el host y no hace contains().
        val cdn = "https://cdn.midominio.com/img/p1.jpg?origen=drive.google.com"
        assertTrue(FirestoreImageUrlValidator.isUsableForDisplay(cdn))
        assertFalse(FirestoreImageUrlValidator.isUnusableHost(cdn))
    }

    @Test
    fun `la URL utilizable se devuelve recortada`() {
        assertEquals(storageUrl, FirestoreImageUrlValidator.usableForDisplayOrNull("  $storageUrl  "))
    }

    @Test
    fun `isSafeForRemote no cambia - una URL de Drive sigue siendo escribible en Firestore`() {
        // La regla nueva es de PRESENTACIÓN. La vieja (no escribir rutas locales) no se toca:
        // el script del panel es quien limpia Firestore, no la app borrando a ciegas.
        assertTrue(FirestoreImageUrlValidator.isSafeForRemote(driveUrl))
        FirestoreImageUrlValidator.validateForFirestore(driveUrl) // no lanza
    }

    // ── El facade que usan los binds ─────────────────────────────────────────────

    @Test
    fun `remoteUrlOrNull deja pasar Storage y corta Drive`() {
        assertEquals(storageUrl, DisplayableProductImage.remoteUrlOrNull(storageUrl))
        assertNull(DisplayableProductImage.remoteUrlOrNull(driveUrl))
    }

    @Test
    fun `loadableSourceOrNull conserva las fuentes locales y corta las de Drive`() {
        assertEquals("local:///data/user/0/p.jpg", DisplayableProductImage.loadableSourceOrNull("local:///data/user/0/p.jpg"))
        assertEquals("/data/user/0/p.jpg", DisplayableProductImage.loadableSourceOrNull("/data/user/0/p.jpg"))
        assertEquals(storageUrl, DisplayableProductImage.loadableSourceOrNull(storageUrl))
        assertNull(DisplayableProductImage.loadableSourceOrNull(driveUrl))
        assertNull(DisplayableProductImage.loadableSourceOrNull(""))
    }

    // ── El memo de fallos permanentes ────────────────────────────────────────────

    @Test
    fun `una URL que fallo con 404 no se vuelve a pedir en esta sesion`() {
        assertEquals(storageUrl, DisplayableProductImage.remoteUrlOrNull(storageUrl))

        ImageLoadFailureMemo.rememberPermanentFailure(storageUrl)

        assertNull("no debe volver a pedirse", DisplayableProductImage.remoteUrlOrNull(storageUrl))
        assertNull(DisplayableProductImage.loadableSourceOrNull(storageUrl))
    }

    @Test
    fun `el memo esta acotado y no crece sin limite`() {
        repeat(600) { ImageLoadFailureMemo.rememberPermanentFailure("https://x.test/$it.jpg") }
        assertTrue("el memo debe estar acotado: ${ImageLoadFailureMemo.size()}", ImageLoadFailureMemo.size() <= 512)
    }

    @Test
    fun `un 404 es permanente y un timeout o un 500 no`() {
        assertTrue(GlideFailures.isPermanent(GlideException("x", listOf(HttpException("no", 404)))))
        assertTrue(GlideFailures.isPermanent(GlideException("x", listOf(HttpException("prohibido", 403)))))
        assertTrue(GlideFailures.isPermanent(GlideException("x", listOf(FileNotFoundException("falta")))))

        assertFalse(GlideFailures.isPermanent(GlideException("x", listOf(HttpException("server", 500)))))
        assertFalse(GlideFailures.isPermanent(GlideException("x", listOf(SocketTimeoutException("lento")))))
        assertFalse("sin red se REINTENTA: es lo que hace aparecer la foto al volver la cobertura",
            GlideFailures.isPermanent(GlideException("sin causas")))
        assertFalse(GlideFailures.isPermanent(null))
    }
}
