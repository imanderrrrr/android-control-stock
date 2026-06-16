package com.are.distribuidora.pendingaccount.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.are.distribuidora.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Hoja de confirmación con botones reales (MaterialButton), no un MaterialAlertDialog.
 * Esto la hace fiable bajo automatización de pruebas (los botones positivos de
 * AlertDialog no responden de forma confiable a taps sintéticos en el emulador).
 */
class ConfirmActionBottomSheet : BottomSheetDialogFragment() {

    /** Acción a ejecutar al confirmar. Se asigna por el llamador antes de show(). */
    var onConfirm: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_confirm_action, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        view.findViewById<TextView>(R.id.confirmTitle).text = args.getString(ARG_TITLE)
        view.findViewById<TextView>(R.id.confirmMessage).text = args.getString(ARG_MESSAGE)

        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirm)
        btnConfirm.text = args.getString(ARG_CONFIRM_LABEL)
        if (args.getBoolean(ARG_DANGER, false)) {
            btnConfirm.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.danger_text)
        }
        btnConfirm.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dismiss() }
    }

    companion object {
        const val TAG = "ConfirmActionBottomSheet"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM_LABEL = "confirmLabel"
        private const val ARG_DANGER = "danger"

        fun newInstance(
            title: String,
            message: String,
            confirmLabel: String,
            danger: Boolean,
        ) = ConfirmActionBottomSheet().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_MESSAGE to message,
                ARG_CONFIRM_LABEL to confirmLabel,
                ARG_DANGER to danger,
            )
        }
    }
}
