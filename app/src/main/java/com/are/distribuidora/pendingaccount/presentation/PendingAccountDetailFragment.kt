package com.are.distribuidora.pendingaccount.presentation

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.pendingaccount.presentation.model.DueState
import com.are.distribuidora.pendingaccount.presentation.model.PendingAccountUiModel
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

/**
 * 10b · Detalle de cuenta. Muestra la cuenta en vivo y permite pagar, editar o eliminar.
 */
@AndroidEntryPoint
class PendingAccountDetailFragment : Fragment(R.layout.fragment_pending_account_detail) {

    private val viewModel: PendingAccountsViewModel by viewModels()
    private val accountId: String by lazy { requireArguments().getString(ARG_ID).orEmpty() }

    private var current: PendingAccountUiModel? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        view.findViewById<View>(R.id.btnBack).setOnClickListener { goBack() }
        view.findViewById<View>(R.id.btnEditTop).setOnClickListener { openEdit() }
        view.findViewById<MaterialButton>(R.id.btnEdit).setOnClickListener { openEdit() }
        view.findViewById<MaterialButton>(R.id.btnMarkPaid).setOnClickListener { current?.let { confirmMarkPaid(it) } }
        view.findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener { current?.let { confirmDelete(it) } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.observeAccount(accountId).collect { account ->
                        if (account == null) {
                            // La cuenta ya no existe (p. ej. eliminada en otro lado).
                            if (current == null) goBack()
                            return@collect
                        }
                        current = account
                        bind(view, account)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PendingAccountsViewModel.Event.Paid -> {
                                toast(R.string.cxc_toast_paid); goBack()
                            }
                            is PendingAccountsViewModel.Event.Deleted -> {
                                toast(R.string.cxc_toast_deleted); goBack()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun bind(view: View, account: PendingAccountUiModel) {
        view.findViewById<android.widget.TextView>(R.id.textClientName).text = account.clientName
        view.findViewById<android.widget.TextView>(R.id.textRouteName).text = account.routeName
        view.findViewById<android.widget.TextView>(R.id.textInitials).text = initialsOf(account.clientName)

        val amount = view.findViewById<android.widget.TextView>(R.id.textAmount)
        amount.text = account.amountFormatted
        amount.setTextColor(colorFor(if (account.isOverdue) R.color.danger_text else R.color.text_primary))

        val due = view.findViewById<android.widget.TextView>(R.id.textDueDate)
        val iconDue = view.findViewById<ImageView>(R.id.iconDue)
        val dueColor = colorFor(dueColorRes(account.dueState))
        due.text = account.dueLabel
        due.setTextColor(dueColor)
        iconDue.setColorFilter(dueColor)

        // Avatar tintado según estado
        val avatar = view.findViewById<View>(R.id.avatar)
        val initials = view.findViewById<android.widget.TextView>(R.id.textInitials)
        val (bgRes, textRes) = avatarColors(account.dueState)
        avatar.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bgRes)
        initials.setTextColor(colorFor(textRes))

        val pillStatus = view.findViewById<View>(R.id.pillStatus)
        pillStatus.visibility = if (account.isOverdue) View.VISIBLE else View.GONE

        // Notas
        val notesSection = view.findViewById<View>(R.id.notesSection)
        if (!account.notes.isNullOrBlank()) {
            notesSection.visibility = View.VISIBLE
            view.findViewById<android.widget.TextView>(R.id.textNotes).text = account.notes
        } else {
            notesSection.visibility = View.GONE
        }

        view.findViewById<android.widget.TextView>(R.id.textMeta).text =
            getString(R.string.cxc_detail_meta, account.createdAtFormatted)

        bindInvoice(view, account)
    }

    private fun bindInvoice(view: View, account: PendingAccountUiModel) {
        val image = view.findViewById<ImageView>(R.id.imageInvoice)
        val placeholder = view.findViewById<View>(R.id.invoicePlaceholder)
        val container = view.findViewById<MaterialCardView>(R.id.invoiceContainer)
        val source = invoiceSource(account)

        if (source != null) {
            image.isVisible = true
            placeholder.isVisible = false
            Glide.with(this).load(source).override(600, 600).centerCrop()
                .placeholder(R.drawable.ic_receipt_long).into(image)
            container.setOnClickListener { showFullscreenImage(source) }
        } else {
            image.isVisible = false
            placeholder.isVisible = true
            container.setOnClickListener(null)
        }
    }

    private fun invoiceSource(account: PendingAccountUiModel): Any? {
        val remote = account.invoiceRemoteUrl?.trim()?.takeIf { it.isNotEmpty() }
        val local = account.invoicePhotoUri?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            remote != null -> remote
            local != null ->
                if (local.startsWith("content://") || local.startsWith("file://")) local else File(local)
            else -> null
        }
    }

    private fun showFullscreenImage(source: Any) {
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.85).toInt(),
            )
        }
        val full = ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(8, 8, 8, 8)
        }
        scroll.addView(full)
        Glide.with(this).load(source).placeholder(R.drawable.ic_receipt_long).into(full)
        MaterialAlertDialogBuilder(requireContext())
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmMarkPaid(account: PendingAccountUiModel) {
        ConfirmActionBottomSheet.newInstance(
            title = getString(R.string.cxc_confirm_paid_title),
            message = getString(R.string.cxc_confirm_paid_msg, account.clientName),
            confirmLabel = getString(R.string.cxc_confirm_paid_yes),
            danger = false,
        ).apply { onConfirm = { viewModel.markAccountPaid(account.id) } }
            .show(childFragmentManager, ConfirmActionBottomSheet.TAG)
    }

    private fun confirmDelete(account: PendingAccountUiModel) {
        ConfirmActionBottomSheet.newInstance(
            title = getString(R.string.cxc_confirm_delete_title),
            message = getString(R.string.cxc_confirm_delete_msg, account.clientName, account.amountFormatted),
            confirmLabel = getString(R.string.cxc_confirm_delete_yes),
            danger = true,
        ).apply { onConfirm = { viewModel.deleteAccount(account.id) } }
            .show(childFragmentManager, ConfirmActionBottomSheet.TAG)
    }

    private fun openEdit() {
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.nav_enter, R.anim.nav_exit, R.anim.nav_pop_enter, R.anim.nav_pop_exit)
            .replace(R.id.fragmentContainer, PendingAccountFormFragment.newInstance(accountId))
            .addToBackStack(null)
            .commit()
    }

    private fun dueColorRes(state: DueState) = when (state) {
        DueState.OVERDUE -> R.color.danger_text
        DueState.SOON -> R.color.warning_text
        DueState.NORMAL -> R.color.text_secondary
    }

    private fun avatarColors(state: DueState): Pair<Int, Int> = when (state) {
        DueState.OVERDUE -> R.color.danger_bg to R.color.danger_text
        DueState.SOON -> R.color.warning_bg to R.color.warning_text
        DueState.NORMAL -> R.color.brand_soft to R.color.brand_soft_text
    }

    private fun colorFor(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }

    private fun goBack() = requireActivity().onBackPressedDispatcher.onBackPressed()

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(requireContext(), resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_ID = "accountId"
        fun newInstance(accountId: String) = PendingAccountDetailFragment().apply {
            arguments = bundleOf(ARG_ID to accountId)
        }
    }
}
