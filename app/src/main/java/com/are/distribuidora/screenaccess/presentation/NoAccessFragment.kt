package com.are.distribuidora.screenaccess.presentation

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.are.distribuidora.R

/**
 * Pantalla "sin acceso": se muestra en lugar del contenido de una sección que el
 * administrador ha denegado para el usuario actual. El mensaje es obligatorio
 * (el dueño quiere que el usuario sepa por qué no ve la sección).
 *
 * Dos variantes:
 * - [newTabInstance]: ocupa una pestaña inferior (la barra de navegación sigue
 *   visible para cambiar a otra sección permitida). Sin flecha de retroceso.
 * - [newPushedInstance]: se llega "entrando" desde otra pantalla (p. ej. la tarjeta
 *   de Cuentas por cobrar). Muestra flecha de retroceso.
 */
class NoAccessFragment : Fragment(R.layout.fragment_no_access) {

    /** ¿Está sustituyendo a una pestaña de nivel superior? (mantiene la barra inferior visible) */
    fun isTabLevel(): Boolean = arguments?.getBoolean(ARG_TAB_LEVEL) ?: false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val back = view.findViewById<View>(R.id.noAccessBack)
        if (isTabLevel()) {
            back.visibility = View.GONE
        } else {
            back.visibility = View.VISIBLE
            back.setOnClickListener { parentFragmentManager.popBackStack() }
        }
    }

    companion object {
        private const val ARG_TAB_LEVEL = "tab_level"

        /** Sustituye a una pestaña inferior denegada. */
        fun newTabInstance(): NoAccessFragment =
            NoAccessFragment().apply { arguments = bundleOf(ARG_TAB_LEVEL to true) }

        /** Se muestra al intentar "entrar" a una sección denegada (con retroceso). */
        fun newPushedInstance(): NoAccessFragment =
            NoAccessFragment().apply { arguments = bundleOf(ARG_TAB_LEVEL to false) }
    }
}
