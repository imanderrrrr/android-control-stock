package com.are.distribuidora.pedido.presentation.catalog

import android.content.Context
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.are.distribuidora.domain.core.Logger
import com.are.distribuidora.domain.model.Product
import com.are.distribuidora.domain.valueobject.Money
import com.are.distribuidora.domain.valueobject.ProductId
import com.are.distribuidora.domain.valueobject.Quantity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

/**
 * Regresión del crash de producción "Inconsistency detected. Invalid item position"
 * en `recyclerViewCatalog`.
 *
 * La causa era que [OrderCatalogAdapter.submitCartQuantities] emitía
 * `notifyItemChanged` a mano sobre un `PagingDataAdapter`, cuyo differ ya es dueño
 * de las notificaciones. Dos fuentes sobre la misma contabilidad de posiciones →
 * al cambiar de golpe el tamaño de la lista (vaciar la búsqueda) los offsets
 * divergían y reventaba en el layout siguiente.
 *
 * Este test fija el invariante que lo impide: actualizar las cantidades del carrito
 * NO produce NINGUNA notificación al RecyclerView.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OrderCatalogAdapterNoNotifyTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `submitCartQuantities no notifica al RecyclerView`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val adapter = OrderCatalogAdapter(NoopLogger())
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        adapter.submitData(PagingData.from(listOf(product("p1"), product("p2"), product("p3"))))
        advanceUntilIdle()
        assertEquals("el adapter debe tener los 3 productos", 3, adapter.itemCount)

        val observer = CountingObserver()
        adapter.registerAdapterDataObserver(observer)

        // Agregar al carrito, incrementar y quitar: todas las transiciones que antes
        // disparaban notifyItemChanged(pos, PAYLOAD_QTY).
        adapter.submitCartQuantities(mapOf("p1" to 1))
        adapter.submitCartQuantities(mapOf("p1" to 2, "p2" to 5))
        adapter.submitCartQuantities(mapOf("p2" to 5))
        adapter.submitCartQuantities(emptyMap())
        advanceUntilIdle()

        assertEquals(
            "submitCartQuantities no puede notificar al RecyclerView: el differ de " +
                "Paging es la única fuente de notificaciones",
            0,
            observer.notifications,
        )

        adapter.unregisterAdapterDataObserver(observer)
        recycler.adapter = null
    }

    // Nota: el camino "una fila que se enlaza después toma la cantidad del mapa" no
    // se puede cubrir aquí porque crear el ViewHolder exige inflar el layout y la
    // suite corre con `isIncludeAndroidResources = false`. Se verifica en emulador.

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun product(id: String) = Product(
        id = ProductId.of(id),
        name = "Producto $id",
        price = Money.of(BigDecimal.valueOf(10.0)),
        stock = Quantity.of(5),
        createdAt = 0L,
        updatedAt = 0L,
    )

    /** Cuenta CUALQUIER notificación estructural o de contenido que reciba el RecyclerView. */
    private class CountingObserver : RecyclerView.AdapterDataObserver() {
        var notifications = 0
            private set

        override fun onChanged() { notifications++ }
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { notifications++ }
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { notifications++ }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { notifications++ }
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { notifications++ }
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) { notifications++ }
    }

    private class NoopLogger : Logger {
        override fun d(tag: String, message: String) = Unit
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
