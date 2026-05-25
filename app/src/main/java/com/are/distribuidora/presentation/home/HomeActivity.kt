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
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.are.distribuidora.domain.product.GetProductCountUseCase
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.are.distribuidora.client.presentation.SelectClientFragment
import com.are.distribuidora.pedido.presentation.catalog.OrderCatalogFragment
import com.are.distribuidora.pedido.presentation.create.CreatePedidoFlowViewModel
import com.are.distribuidora.pedido.presentation.list.PedidosFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class HomeActivity : FragmentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val createPedidoFlowViewModel: CreatePedidoFlowViewModel by viewModels()

    @Inject lateinit var getProductCount: GetProductCountUseCase

    private val tag = "HOME_CHROME"

    private lateinit var bottomNavPill: MaterialCardView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton

    private fun isTopLevel(fragment: Fragment?): Boolean {
        return fragment is HomeFragment ||
                fragment is InventoryFragment ||
                fragment is PedidosFragment ||
                fragment is ClientsFragment
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

        bottomNavPill.visibility = if (show) View.VISIBLE else View.GONE
        fab.visibility = if (show) View.VISIBLE else View.GONE

        logState(source, fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bottomNavPill = findViewById(R.id.bottomNavPill)
        bottomNav = findViewById(R.id.bottomNavigation)
        fab = findViewById(R.id.fabCreateOrder)

        supportFragmentManager.addOnBackStackChangedListener {
            updateChrome(currentFragment(), "OnBackStackChanged")
        }

        if (savedInstanceState == null) {
            openRootFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        } else {
            // Estado restaurado (rotación). Aplicar chrome al fragment actual real.
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
                    .replace(R.id.fragmentContainer, OrderCatalogFragment())
                    // Usamos un nombre distinto para no duplicar FLOW_CREATE_ORDER.
                    .addToBackStack("FLOW_CREATE_ORDER_CATALOG")
                    .commit()
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    openRootFragment(HomeFragment())
                    true
                }
                R.id.nav_inventory -> {
                    openRootFragment(InventoryFragment())
                    true
                }
                R.id.nav_orders -> {
                    openRootFragment(PedidosFragment())
                    true
                }
                R.id.nav_clients -> {
                    openRootFragment(ClientsFragment())
                    true
                }
                R.id.nav_placeholder -> false
                else -> false
            }
        }

        fab.setOnClickListener {
            // Iniciar flujo: SelectClient (Ruta + Cliente en un solo screen)
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    SelectClientFragment.newInstance()
                )
                .addToBackStack("FLOW_CREATE_ORDER")
                .commit()
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

    private fun openRootFragment(fragment: Fragment) {
        // commitNow: garantiza que el fragment ya sea el current cuando actualices chrome.
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitNow()

        updateChrome(fragment, "openRootFragment")
    }
}
