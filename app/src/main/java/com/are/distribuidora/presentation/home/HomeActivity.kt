package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.are.distribuidora.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint

import androidx.activity.viewModels
import com.are.distribuidora.client.presentation.CreateClientFragment
import com.are.distribuidora.client.presentation.RouteClientsFragment

@AndroidEntryPoint
class HomeActivity : FragmentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private val tag = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNavPill = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bottomNavPill)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fab = findViewById<FloatingActionButton>(R.id.fabCreateOrder)

        fun updateChromeForFragment(fragment: Fragment?) {
            val hide = fragment is RouteClientsFragment ||
                fragment is CreateClientFragment

            bottomNavPill.visibility = if (hide) android.view.View.GONE else android.view.View.VISIBLE
            fab.visibility = if (hide) android.view.View.GONE else android.view.View.VISIBLE
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateChromeForFragment(supportFragmentManager.findFragmentById(R.id.fragmentContainer))
        }

        // Cargar fragment por defecto al iniciar.
        if (savedInstanceState == null) {
            openRootFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        // Estado inicial
        updateChromeForFragment(supportFragmentManager.findFragmentById(R.id.fragmentContainer))

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
                    openRootFragment(OrdersFragment())
                    true
                }

                R.id.nav_clients -> {
                    openRootFragment(ClientsFragment())
                    true
                }

                // Slot central, lo maneja el FAB.
                R.id.nav_placeholder -> false

                else -> false
            }
        }

        fab.setOnClickListener {
            // Acción placeholder: Crear Pedido
            Log.i(tag, "Crear pedido (+)")
            Toast.makeText(this, getString(R.string.nav_create_order), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openRootFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        val bottomNavPill = findViewById<com.google.android.material.card.MaterialCardView>(R.id.bottomNavPill)
        val fab = findViewById<FloatingActionButton>(R.id.fabCreateOrder)

        val hide = fragment is RouteClientsFragment ||
            fragment is CreateClientFragment

        bottomNavPill.visibility = if (hide) android.view.View.GONE else android.view.View.VISIBLE
        fab.visibility = if (hide) android.view.View.GONE else android.view.View.VISIBLE
    }
}
