package com.are.distribuidora.presentation.home.dialog

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import com.are.distribuidora.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddRouteDialog(
    private val onConfirm: (name: String, day: Int) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.dialog_add_route, null)
        
        val nameEditText = view.findViewById<EditText>(R.id.nameEditText)
        val daySpinner = view.findViewById<Spinner>(R.id.daySpinner)

        setupDaySpinner(daySpinner)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.route_create_action) { _, _ ->
                val name = nameEditText.text.toString().trim()
                val selectedDayIndex = daySpinner.selectedItemPosition // 0..6
                // Convertir índice (0=Lunes) a modelo (1=Lunes)
                val deliveryDay = selectedDayIndex + 1
                
                if (name.isNotEmpty()) {
                    onConfirm(name, deliveryDay)
                }
            }
            .setNegativeButton(R.string.route_cancel_action, null)
            .create()
    }

    private fun setupDaySpinner(spinner: Spinner) {
        val days = listOf(
            getString(R.string.day_monday),
            getString(R.string.day_tuesday),
            getString(R.string.day_wednesday),
            getString(R.string.day_thursday),
            getString(R.string.day_friday),
            getString(R.string.day_saturday),
            getString(R.string.day_sunday)
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, days)
        spinner.adapter = adapter
    }

    companion object {
        const val TAG = "AddRouteDialog"
    }
}
