package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.are.distribuidora.R
import com.are.distribuidora.data.local.dao.ProductDao
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : FragmentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    @Inject lateinit var productDao: ProductDao

    private val tag = "HOME_CHROME"

    private lateinit var bottomNavPill: MaterialCardView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fab: FloatingActionButton

    private fun isTopLevel(fragment: Fragment?): Boolean {
        return fragment is HomeFragment ||
                fragment is InventoryFragment ||
                fragment is OrdersFragment ||
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
                R.id.nav_placeholder -> false
                else -> false
            }
        }

        fab.setOnClickListener {
            Toast.makeText(this, getString(R.string.nav_create_order), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // DEBUG opcional
        lifecycleScope.launch {
            try {
                val count = productDao.countAll()
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
