package com.are.distribuidora.presentation.home

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.are.distribuidora.R

/**
 * Reportes (Home) - fragment por defecto.
 * Placeholder minimalista (la UI real de reportes puede llegar luego).
 */
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return TextView(requireContext()).apply {
            text = getString(R.string.nav_home)
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
}
