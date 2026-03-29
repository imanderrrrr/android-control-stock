package com.are.distribuidora.presentation.home.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.are.distribuidora.R
import com.are.distribuidora.presentation.home.model.PendingAccountUiModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

/**
 * Diálogo de detalle de una cuenta pendiente.
 * Muestra: cliente, ruta, monto, fecha límite, notas e imagen de factura ampliada.
 * Al pulsar la imagen se abre vista fullscreen con scroll.
 */
class AccountDetailDialog : DialogFragment() {

    var onMarkPaid: (() -> Unit)? = null
    var onDelete: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Usar el tema de la app para que colorPrimary (paleta verde) se resuelva correctamente
        val themedContext = android.view.ContextThemeWrapper(
            requireContext(),
            R.style.Theme_Distribuidora
        )
        val inflater = requireActivity().layoutInflater.cloneInContext(themedContext)
        val view = inflater.inflate(R.layout.dialog_account_detail, null)

        val clientName = requireArguments().getString(ARG_CLIENT_NAME, "")
        val routeName  = requireArguments().getString(ARG_ROUTE_NAME, "")
        val amount     = requireArguments().getString(ARG_AMOUNT, "")
        val dueDate    = requireArguments().getString(ARG_DUE_DATE, "")
        val notes      = requireArguments().getString(ARG_NOTES)
        val isOverdue  = requireArguments().getBoolean(ARG_IS_OVERDUE, false)
        val remoteUrl  = requireArguments().getString(ARG_REMOTE_URL)
        val localUri   = requireArguments().getString(ARG_LOCAL_URI)

        // ── Textos ────────────────────────────────────────────────────────────
        view.findViewById<TextView>(R.id.detailClientName).text = clientName
        view.findViewById<TextView>(R.id.detailRouteName).text  = routeName
        view.findViewById<TextView>(R.id.detailAmount).text     = amount
        view.findViewById<TextView>(R.id.detailDueDate).apply {
            text = dueDate
            setTextColor(
                if (isOverdue)
                    ContextCompat.getColor(requireContext(), R.color.palette_800)
                else
                    ContextCompat.getColor(requireContext(), R.color.palette_600)
            )
        }

        val notesSection = view.findViewById<View>(R.id.detailNotesSection)
        val notesText    = view.findViewById<TextView>(R.id.detailNotes)
        if (!notes.isNullOrBlank()) {
            notesSection.visibility = View.VISIBLE
            notesText.text = notes
        } else {
            notesSection.visibility = View.GONE
        }

        // ── Imagen de factura ─────────────────────────────────────────────────
        val imageView = view.findViewById<ShapeableImageView>(R.id.detailInvoiceImage)
        val imageSource: Any? = when {
            !remoteUrl.isNullOrBlank() -> remoteUrl
            !localUri.isNullOrBlank()  -> {
                if (localUri.startsWith("content://") || localUri.startsWith("file://")) localUri
                else File(localUri)
            }
            else -> null
        }

        if (imageSource != null) {
            imageView.visibility = View.VISIBLE
            Glide.with(requireContext())
                .load(imageSource)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(imageView)
            // Pulsar imagen abre fullscreen
            imageView.setOnClickListener { showFullscreenImage(imageSource) }
        } else {
            imageView.visibility = View.GONE
        }

        // ── Botones ───────────────────────────────────────────────────────────
        view.findViewById<MaterialButton>(R.id.detailBtnMarkPaid).setOnClickListener {
            onMarkPaid?.invoke()
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.detailBtnDelete).setOnClickListener {
            onDelete?.invoke()
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.detailBtnClose).setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setView(view)
            .create()
    }

    /**
     * Muestra la imagen en tamaño completo dentro de un ScrollView
     * para que el usuario pueda verla completa aunque sea grande.
     */
    private fun showFullscreenImage(source: Any) {
        val scrollView = ScrollView(requireContext()).apply {
            val displayMetrics = resources.displayMetrics
            val maxHeight = (displayMetrics.heightPixels * 0.8).toInt()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxHeight
            )
        }
        val fullView = ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(8, 8, 8, 8)
        }
        scrollView.addView(fullView)
        Glide.with(this)
            .load(source)
            .placeholder(R.drawable.ic_image_placeholder)
            .into(fullView)

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Distribuidora_Dialog_Light)
            .setView(scrollView)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    companion object {
        const val TAG = "AccountDetailDialog"

        private const val ARG_CLIENT_NAME = "clientName"
        private const val ARG_ROUTE_NAME  = "routeName"
        private const val ARG_AMOUNT      = "amount"
        private const val ARG_DUE_DATE    = "dueDate"
        private const val ARG_NOTES       = "notes"
        private const val ARG_IS_OVERDUE  = "isOverdue"
        private const val ARG_REMOTE_URL  = "remoteUrl"
        private const val ARG_LOCAL_URI   = "localUri"

        fun newInstance(account: PendingAccountUiModel) =
            AccountDetailDialog().apply {
                arguments = bundleOf(
                    ARG_CLIENT_NAME to account.clientName,
                    ARG_ROUTE_NAME  to account.routeName,
                    ARG_AMOUNT      to account.amountFormatted,
                    ARG_DUE_DATE    to account.dueDateFormatted,
                    ARG_NOTES       to account.notes,
                    ARG_IS_OVERDUE  to account.isOverdue,
                    ARG_REMOTE_URL  to account.invoiceRemoteUrl,
                    ARG_LOCAL_URI   to account.invoicePhotoUri,
                )
            }
    }
}
