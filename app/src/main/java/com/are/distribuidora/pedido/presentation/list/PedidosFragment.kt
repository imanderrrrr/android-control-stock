package com.are.distribuidora.pedido.presentation.list

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.are.distribuidora.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

/**
 * Pantalla principal de Pedidos con dos tabs:
 * 1. Mis Pedidos  — pedidos propios (flujo existente sin modificar)
 * 2. Otros Pedidos — headers de pedidos de otros vendedores con descarga bajo demanda
 *
 * Usa ViewPager2 + TabLayout para la navegación entre tabs.
 * Cada tab es un Fragment independiente que comparte el PedidosViewModel (activityViewModels).
 */
@AndroidEntryPoint
class PedidosFragment : Fragment(R.layout.fragment_pedidos) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inset superior para que el encabezado no quede bajo la barra de estado.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerPedidos)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutPedidos)

        viewPager.adapter = PedidosTabAdapter(this)
        viewPager.offscreenPageLimit = 1 // mantener ambos tabs en memoria

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0    -> getString(R.string.pedidos_tab_mis_pedidos)
                else -> getString(R.string.pedidos_tab_otros_pedidos)
            }
        }.attach()

        parentFragmentManager.setFragmentResultListener(
            RESULT_PEDIDO_CREATED, viewLifecycleOwner
        ) { _, _ -> /* no-op: Room notifica al Flow automáticamente */ }
    }

    companion object {
        const val RESULT_PEDIDO_CREATED = "pedido_created"
    }
}

/**
 * Adapter de tabs para el ViewPager2.
 * Tab 0 → MisPedidosListFragment (lista de rutas con pedidos propios)
 * Tab 1 → OtrosPedidosListFragment (lista de rutas con pedidos de otros vendedores)
 */
private class PedidosTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> MisPedidosListFragment()
        else -> OtrosPedidosListFragment()
    }
}
