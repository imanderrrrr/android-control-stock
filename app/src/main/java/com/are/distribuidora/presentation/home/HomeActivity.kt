package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.WindowCompat
import com.are.distribuidora.R
import com.are.distribuidora.domain.product.GetProductCountUseCase
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.are.distribuidora.client.presentation.SelectClientFragment
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogFragment
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.are.distribuidora.pedido.presentation.list.PedidosFragment
import com.are.distribuidora.route.presentation.SelectRouteFragment
import com.are.distribuidora.screenaccess.domain.model.AppScreen
import com.are.distribuidora.screenaccess.domain.model.ScreenAccess
import com.are.distribuidora.screenaccess.presentation.NoAccessFragment
import com.are.distribuidora.screenaccess.presentation.ScreenAccessViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HomeActivity : FragmentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val createPedidoFlowViewModel: CreatePedidoFlowViewModel by viewModels()

    // Permisos de pantalla del usuario (compartido con los fragments vía activityViewModels()).
    private val screenAccessViewModel: ScreenAccessViewModel by viewModels()

    @Inject lateinit var getProductCount: GetProductCountUseCase

    private val tag = "HOME_CHROME"

    private lateinit var navBarContainer: View
    private lateinit var bottomNav: BottomNavigationView

    /** Pestaña inferior actualmente seleccionada (su contenido puede estar permitido o no). */
    private var currentTab: AppScreen = AppScreen.INICIO

    private fun isTopLevel(fragment: Fragment?): Boolean {
        return fragment is InicioFragment ||
                fragment is HomeFragment ||
                fragment is InventoryFragment ||
                fragment is PedidosFragment ||
                fragment is ClientsFragment ||
                // El placeholder "sin acceso" de una pestaña también es nivel superior:
                // así la barra inferior sigue visible y el usuario puede ir a otra sección.
                (fragment is NoAccessFragment && fragment.isTabLevel())
    }

    private fun currentFragment(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.fragmentContainer)

    private fun logState(source: String, fragment: Fragment?) {
        val name = fragment?.javaClass?.name ?: "null"
        val backCount = supportFragmentManager.backStackEntryCount
        Log.d(tag, "[$source] current=$name, isTopLevel=${isTopLevel(fragment)}, backStackCount=$backCount")
    }

    private fun updateChrome(fragment: Fragment?, source: String) {
        val show = isTopLevel(fragment)
        navBarContainer.visibility = if (show) View.VISIBLE else View.GONE
        logState(source, fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Iconos de barras de sistema oscuros (contenido claro "Bosque Pro").
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        navBarContainer = findViewById(R.id.navBarContainer)
        bottomNav = findViewById(R.id.bottomNavigation)

        supportFragmentManager.addOnBackStackChangedListener {
            updateChrome(currentFragment(), "OnBackStackChanged")
            // De vuelta a una pestaña raíz: re-evaluar el acceso por si cambió mientras
            // navegábamos en un sub-flujo. Se difiere para no anidar transacciones con el pop.
            if (supportFragmentManager.backStackEntryCount == 0) {
                navBarContainer.post {
                    if (supportFragmentManager.backStackEntryCount == 0) renderCurrentTab()
                }
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
            // Renderiza Inicio o, si está denegado, el placeholder "sin acceso".
            selectTab(AppScreen.INICIO)
        } else {
            // Estado restaurado (rotación). Deriva la pestaña actual del item seleccionado
            // y aplica chrome al fragment actual real.
            currentTab = tabForMenuId(bottomNav.selectedItemId) ?: AppScreen.INICIO
            updateChrome(currentFragment(), "onCreate-restore")
        }

        // --- Result Listener para Selección de Cliente (Crear Pedido) ---
        supportFragmentManager.setFragmentResultListener(
            SelectClientFragment.REQUEST_KEY_CLIENT_SELECTION,
            this
        ) { _, bundle ->
            val selection = bundle.getSerializable(
                SelectClientFragment.BUNDLE_KEY_SELECTION
            ) as? com.are.distribuidora.domain.pedido.model.ClienteSelection

            val routeId = bundle.getString(SelectClientFragment.BUNDLE_KEY_ROUTE_ID)

            if (selection == null || routeId.isNullOrBlank()) {
                Log.e(tag, "Resultado inválido de selección de cliente. selection=$selection routeId=$routeId")
                Toast.makeText(this, "No se pudo continuar: falta cliente o ruta", Toast.LENGTH_SHORT).show()
                return@setFragmentResultListener
            }

            Log.d(tag, "Cliente seleccionado para pedido: $selection (routeId=$routeId)")

            // Fecha de entrega: usa hoy como valor por defecto para el pedido creado en app.
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // Guardar estado de flujo (Activity-scoped)
            createPedidoFlowViewModel.setSelection(routeId = routeId, selection = selection, deliveryDate = todayDate)

            // Importante: SelectClientFragment hace popBackStack() justo después de setFragmentResult().
            // Para evitar pelear con esa transacción, diferimos la navegación al siguiente loop del main thread.
            Handler(Looper.getMainLooper()).post {
                logState("BeforeNavigateToCatalog", currentFragment())

                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
                    .replace(R.id.fragmentContainer, OrderCatalogFragment())
                    .addToBackStack("FLOW_CREATE_ORDER_CATALOG")
                    .commit()
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            val screen = tabForMenuId(item.itemId) ?: return@setOnItemSelectedListener false
            // El acceso se evalúa en selectTab: si la pestaña está denegada, se muestra
            // el placeholder "sin acceso" (pero la pestaña sigue seleccionable).
            selectTab(screen)
            true
        }

        // Observa los permisos del usuario: atenúa las pestañas denegadas y corrige la
        // pantalla raíz mostrada (allow→deny y deny→allow) cuando un admin cambia el acceso.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                screenAccessViewModel.access.collect { access ->
                    applyTabDimming(access)
                    if (supportFragmentManager.backStackEntryCount == 0) {
                        renderCurrentTab()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // DEBUG opcional
        lifecycleScope.launch {
            try {
                val count = getProductCount()
                Log.d(tag, "Local DB product count = $count")
            } catch (e: Exception) {
                Log.e(tag, "Error counting products", e)
            }
        }
        // Re-aplicar por seguridad al volver al foreground
        updateChrome(currentFragment(), "onResume")
    }

    /** Cambia de pestaña inferior (usado por las acciones rápidas del dashboard). */
    fun goToTab(itemId: Int) {
        bottomNav.selectedItemId = itemId
    }

    /** Inicia el flujo de crear pedido: primero se elige la ruta (pantalla 03). */
    fun startCreateOrder() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, SelectRouteFragment.newInstance())
            .addToBackStack("FLOW_CREATE_ORDER")
            .commit()
    }

    /** Abre la selección de ruta en modo "fijar ruta activa" (Elegir / Cambiar). */
    fun openSelectActiveRoute() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(
                R.id.fragmentContainer,
                SelectRouteFragment.newInstance(SelectRouteFragment.MODE_SET_ACTIVE)
            )
            .addToBackStack("FLOW_SELECT_ACTIVE_ROUTE")
            .commit()
    }

    /** Continúa la ruta activa: va directo a elegir cliente de esa ruta. */
    fun continueActiveRoute(routeId: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, SelectClientFragment.newInstance(routeId))
            .addToBackStack("FLOW_CONTINUE_ROUTE")
            .commit()
    }

    private fun openRootFragment(fragment: Fragment) {
        // commitNow: garantiza que el fragment ya sea el current cuando actualices chrome.
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()

        updateChrome(fragment, "openRootFragment")
    }

    // ---------------------------------------------------------------------------
    // Control de acceso por pantalla
    // ---------------------------------------------------------------------------

    /** Selecciona una pestaña y renderiza su contenido o el placeholder "sin acceso". */
    private fun selectTab(screen: AppScreen) {
        currentTab = screen
        renderCurrentTab()
    }

    /**
     * Muestra el fragment real de [currentTab] si está permitido; si no, el placeholder
     * "sin acceso" (nivel pestaña). Idempotente: no recrea el fragment si ya es el correcto,
     * por lo que puede llamarse desde el observador de acceso sin parpadeos.
     */
    private fun renderCurrentTab() {
        val screen = currentTab
        val allowed = screenAccessViewModel.access.value.isAllowed(screen)
        if (isShowingCorrectRoot(screen, allowed)) return
        openRootFragment(if (allowed) realFragmentFor(screen) else NoAccessFragment.newTabInstance())
    }

    private fun isShowingCorrectRoot(screen: AppScreen, allowed: Boolean): Boolean {
        val current = currentFragment() ?: return false
        return if (allowed) {
            current.javaClass == realFragmentClassFor(screen)
        } else {
            current is NoAccessFragment && current.isTabLevel()
        }
    }

    /** Atenúa el icono de las pestañas denegadas (sin deshabilitarlas: el tap muestra el mensaje). */
    private fun applyTabDimming(access: ScreenAccess) {
        setTabDim(R.id.nav_home, access.isAllowed(AppScreen.INICIO))
        setTabDim(R.id.nav_inventory, access.isAllowed(AppScreen.INVENTARIO))
        setTabDim(R.id.nav_orders, access.isAllowed(AppScreen.PEDIDOS))
        setTabDim(R.id.nav_clients, access.isAllowed(AppScreen.CLIENTES))
        setTabDim(R.id.nav_reportes, access.isAllowed(AppScreen.REPORTES))
    }

    private fun setTabDim(itemId: Int, allowed: Boolean) {
        bottomNav.menu.findItem(itemId)?.icon?.alpha = if (allowed) 255 else 97
    }

    private fun tabForMenuId(itemId: Int): AppScreen? = when (itemId) {
        R.id.nav_home -> AppScreen.INICIO
        R.id.nav_inventory -> AppScreen.INVENTARIO
        R.id.nav_orders -> AppScreen.PEDIDOS
        R.id.nav_clients -> AppScreen.CLIENTES
        R.id.nav_reportes -> AppScreen.REPORTES
        else -> null
    }

    private fun realFragmentFor(screen: AppScreen): Fragment = when (screen) {
        AppScreen.INICIO -> InicioFragment()
        AppScreen.INVENTARIO -> InventoryFragment()
        AppScreen.PEDIDOS -> PedidosFragment()
        AppScreen.CLIENTES -> ClientsFragment()
        AppScreen.REPORTES -> HomeFragment()
        // CUENTAS_PENDIENTES no es una pestaña; se accede desde Clientes.
        AppScreen.CUENTAS_PENDIENTES -> ClientsFragment()
    }

    private fun realFragmentClassFor(screen: AppScreen): Class<out Fragment> = when (screen) {
        AppScreen.INICIO -> InicioFragment::class.java
        AppScreen.INVENTARIO -> InventoryFragment::class.java
        AppScreen.PEDIDOS -> PedidosFragment::class.java
        AppScreen.CLIENTES -> ClientsFragment::class.java
        AppScreen.REPORTES -> HomeFragment::class.java
        AppScreen.CUENTAS_PENDIENTES -> ClientsFragment::class.java
    }
}
