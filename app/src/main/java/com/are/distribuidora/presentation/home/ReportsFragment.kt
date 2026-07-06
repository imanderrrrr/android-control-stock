package com.are.distribuidora.presentation.home

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.crash.presentation.CrashReportsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla principal de Reportes (rediseño "Bosque Pro").
 *
 * Muestra KPIs de ventas (7 y 14 días), un toggle de periodo que controla el
 * gráfico "Ventas por día", ventas por ruta, top/bottom productos y mejores
 * clientes. Los gráficos se dibujan con Views nativas (sin librería externa).
 *
 * Los datos provienen de Room (tabla pedidos + pedido_items) via [ReportsViewModel].
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_reports) {

    private val viewModel: ReportsViewModel by viewModels()

    /** Periodo activo del toggle (7 o 14 días). Controla el gráfico diario. */
    private var selectedPeriod = 7

    /** Última serie diaria recibida, para re-render al cambiar el periodo. */
    private var dailyCache: List<DailySalesUi> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Padding superior = alto del status bar (el header no debe quedar bajo el reloj).
        val basePaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            v.updatePadding(
                top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                bottom = basePaddingBottom,
            )
            insets
        }

        val progress       = view.findViewById<ProgressBar>(R.id.progressReports)
        val textEmpty       = view.findViewById<TextView>(R.id.textReportsEmpty)
        val layoutContent   = view.findViewById<LinearLayout>(R.id.layoutContent)
        val btnRefresh      = view.findViewById<View>(R.id.btnRefresh)
        val btnCrashReports = view.findViewById<View>(R.id.btnCrashReports)

        val textVentas7   = view.findViewById<TextView>(R.id.textVentas7)
        val textPedidos7  = view.findViewById<TextView>(R.id.textPedidos7)
        val textVentas14  = view.findViewById<TextView>(R.id.textVentas14)
        val textPedidos14 = view.findViewById<TextView>(R.id.textPedidos14)
        val textAvgDaily  = view.findViewById<TextView>(R.id.textAvgDaily)

        val sectionRoutes = view.findViewById<View>(R.id.sectionRoutes)

        val layoutTopProducts    = view.findViewById<LinearLayout>(R.id.layoutTopProducts)
        val layoutBottomProducts = view.findViewById<LinearLayout>(R.id.layoutBottomProducts)
        val layoutTopClients     = view.findViewById<LinearLayout>(R.id.layoutTopClients)

        val chip7  = view.findViewById<TextView>(R.id.chipPeriod7)
        val chip14 = view.findViewById<TextView>(R.id.chipPeriod14)

        btnRefresh.setOnClickListener { viewModel.refresh() }
        btnCrashReports.setOnClickListener {
            startActivity(Intent(requireContext(), CrashReportsActivity::class.java))
        }

        chip7.setOnClickListener { if (selectedPeriod != 7) { selectedPeriod = 7; renderPeriod() } }
        chip14.setOnClickListener { if (selectedPeriod != 14) { selectedPeriod = 14; renderPeriod() } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ReportsViewModel.UiState.Loading -> {
                            progress.visibility      = View.VISIBLE
                            textEmpty.visibility     = View.GONE
                            layoutContent.visibility = View.GONE
                        }
                        is ReportsViewModel.UiState.Error -> {
                            progress.visibility      = View.GONE
                            textEmpty.visibility     = View.VISIBLE
                            layoutContent.visibility = View.GONE
                            textEmpty.text = state.message
                        }
                        is ReportsViewModel.UiState.Success -> {
                            progress.visibility = View.GONE
                            val data = state.data

                            if (!data.hasData) {
                                textEmpty.visibility     = View.VISIBLE
                                layoutContent.visibility = View.GONE
                                return@collect
                            }

                            textEmpty.visibility     = View.GONE
                            layoutContent.visibility = View.VISIBLE

                            textVentas7.text   = data.totalVentas7Formatted
                            textPedidos7.text  = getString(R.string.reports_kpi_pedidos, data.totalPedidos7)
                            textVentas14.text  = data.totalVentas14Formatted
                            textPedidos14.text = getString(R.string.reports_kpi_pedidos, data.totalPedidos14)
                            textAvgDaily.text  = data.avgDailyFormatted

                            dailyCache = data.dailySales
                            renderPeriod()

                            if (data.routeSales.size >= 2) {
                                sectionRoutes.visibility = View.VISIBLE
                                buildRouteBars(view.findViewById(R.id.layoutRouteBars), data.routeSales)
                            } else {
                                sectionRoutes.visibility = View.GONE
                            }

                            buildRankingList(
                                container = layoutTopProducts,
                                items = data.topProducts.mapIndexed { i, p ->
                                    RankingItem(
                                        badgeText = "${i + 1}",
                                        badgeBg = R.color.brand_soft,
                                        badgeTextColor = R.color.success_text,
                                        name = p.productName,
                                        subtitle = getString(R.string.reports_product_units, p.totalQty),
                                        value = p.totalRevenueFormatted,
                                    )
                                },
                            )

                            buildRankingList(
                                container = layoutBottomProducts,
                                items = data.bottomProducts.mapIndexed { i, p ->
                                    RankingItem(
                                        badgeText = "${i + 1}",
                                        badgeBg = R.color.bg_muted,
                                        badgeTextColor = R.color.text_secondary,
                                        name = p.productName,
                                        subtitle = getString(R.string.reports_product_units, p.totalQty),
                                        value = p.totalRevenueFormatted,
                                    )
                                },
                            )

                            buildRankingList(
                                container = layoutTopClients,
                                items = data.topClients.map { c ->
                                    RankingItem(
                                        badgeText = initials(c.clientName),
                                        badgeBg = R.color.brand_soft,
                                        badgeTextColor = R.color.success_text,
                                        name = c.clientName,
                                        subtitle = getString(R.string.reports_kpi_pedidos, c.orderCount),
                                        value = c.totalSpentFormatted,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Toggle de periodo ───────────────────────────────────────────────────────

    private fun renderPeriod() {
        val v = view ?: return
        val chip7  = v.findViewById<TextView>(R.id.chipPeriod7)
        val chip14 = v.findViewById<TextView>(R.id.chipPeriod14)
        styleChip(chip7, selectedPeriod == 7)
        styleChip(chip14, selectedPeriod == 14)

        v.findViewById<TextView>(R.id.textReportsSubtitle).text =
            getString(R.string.reports_subtitle_range, selectedPeriod)

        buildDailyBars(v.findViewById(R.id.layoutDailyBars), dailyCache, selectedPeriod)
    }

    private fun styleChip(chip: TextView, active: Boolean) {
        if (active) {
            chip.setBackgroundResource(R.drawable.bg_pill)
            chip.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_inverse))
        } else {
            chip.setBackgroundResource(R.drawable.bg_pill_outline)
            chip.backgroundTintList = null
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    // ── Gráfico: Ventas por día (barras verticales nativas) ─────────────────────

    private fun buildDailyBars(container: LinearLayout, dailySales: List<DailySalesUi>, periodDays: Int) {
        container.removeAllViews()
        val items = if (periodDays == 7) dailySales.takeLast(7) else dailySales
        if (items.isEmpty()) return

        val density = resources.displayMetrics.density
        val maxAmount = items.maxOf { it.totalAmount }.coerceAtLeast(1.0)
        val maxBarHeightDp = 78
        val barWidthDp = if (items.size > 7) 12 else 22
        val ctx = requireContext()

        for (day in items) {
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }

            col.addView(TextView(ctx).apply {
                text = abbreviate(day.totalAmount)
                textSize = 10f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
            })

            val barHeightDp = (day.totalAmount / maxAmount * maxBarHeightDp).toInt().coerceAtLeast(3)
            col.addView(View(ctx).apply {
                setBackgroundResource(R.drawable.bg_report_bar)
                layoutParams = LinearLayout.LayoutParams(
                    (barWidthDp * density).toInt(),
                    (barHeightDp * density).toInt(),
                ).apply { topMargin = (4 * density).toInt() }
            })

            col.addView(TextView(ctx).apply {
                text = day.dayOfWeek
                textSize = 10f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = (6 * density).toInt() }
            })

            container.addView(col)
        }
    }

    // ── Gráfico: Ventas por ruta (barras horizontales nativas) ──────────────────

    private fun buildRouteBars(container: LinearLayout, routeSales: List<RouteSalesUi>) {
        container.removeAllViews()
        if (routeSales.isEmpty()) return

        val density = resources.displayMetrics.density
        val maxAmount = routeSales.maxOf { it.totalAmount }.coerceAtLeast(1.0)
        val ctx = requireContext()

        routeSales.forEachIndexed { index, route ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { if (index > 0) topMargin = (14 * density).toInt() }
            }

            val top = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            top.addView(TextView(ctx).apply {
                text = route.routeName
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            top.addView(TextView(ctx).apply {
                text = route.totalFormatted
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            })
            row.addView(top)

            val proportion = (route.totalAmount / maxAmount).toFloat().coerceIn(0.06f, 1f)
            val track = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
                setBackgroundResource(R.drawable.bg_pill)
                backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.bg_muted))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (8 * density).toInt(),
                ).apply { topMargin = (6 * density).toInt() }
            }
            track.addView(View(ctx).apply {
                setBackgroundResource(R.drawable.bg_pill)
                backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.brand_accent))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, proportion)
            })
            if (proportion < 1f) {
                track.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - proportion)
                })
            }
            row.addView(track)

            container.addView(row)
        }
    }

    // ── Rankings ────────────────────────────────────────────────────────────────

    private data class RankingItem(
        val badgeText: String,
        val badgeBg: Int,
        val badgeTextColor: Int,
        val name: String,
        val subtitle: String,
        val value: String,
    )

    private fun buildRankingList(container: LinearLayout, items: List<RankingItem>) {
        container.removeAllViews()
        val ctx = requireContext()

        if (items.isEmpty()) {
            container.addView(TextView(ctx).apply {
                text = "—"
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt())
            })
            return
        }

        val inflater = LayoutInflater.from(ctx)
        val density = resources.displayMetrics.density

        items.forEachIndexed { index, item ->
            if (index > 0) {
                container.addView(View(ctx).apply {
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_muted))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * density).toInt(),
                    )
                })
            }

            val itemView = inflater.inflate(R.layout.item_report_ranking, container, false)
            val badge = itemView.findViewById<TextView>(R.id.textRank)
            badge.text = item.badgeText
            badge.background?.mutate()?.setTint(ContextCompat.getColor(ctx, item.badgeBg))
            badge.setTextColor(ContextCompat.getColor(ctx, item.badgeTextColor))
            itemView.findViewById<TextView>(R.id.textName).text = item.name
            itemView.findViewById<TextView>(R.id.textSubtitle).text = item.subtitle
            itemView.findViewById<TextView>(R.id.textValue).text = item.value

            container.addView(itemView)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun abbreviate(amount: Double): String =
        if (amount >= 1000) "Q${String.format(Locale.US, "%.1f", amount / 1000)}k"
        else "Q${String.format(Locale.US, "%.0f", amount)}"

    private fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "—"
            words.size == 1 -> words[0].take(2).uppercase(Locale("es", "GT"))
            else -> (words[0].take(1) + words[1].take(1)).uppercase(Locale("es", "GT"))
        }
    }
}
