package com.are.distribuidora.presentation.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.are.distribuidora.R
import com.are.distribuidora.crash.presentation.CrashReportsActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla principal de Reportes.
 *
 * Muestra KPIs de ventas (7 y 14 días), gráfica de ventas por día,
 * gráfica de ventas por ruta, top productos, productos menos solicitados
 * y mejores clientes de la semana.
 *
 * Los datos provienen de Room (tabla pedidos + pedido_items) via [ReportsViewModel].
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_reports) {

    private val viewModel: ReportsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress      = view.findViewById<ProgressBar>(R.id.progressReports)
        val textEmpty      = view.findViewById<TextView>(R.id.textReportsEmpty)
        val layoutContent  = view.findViewById<LinearLayout>(R.id.layoutContent)
        val btnRefresh     = view.findViewById<ImageButton>(R.id.btnRefresh)
        val btnCrashReports = view.findViewById<ImageButton>(R.id.btnCrashReports)

        // KPIs
        val textVentas7    = view.findViewById<TextView>(R.id.textVentas7)
        val textPedidos7   = view.findViewById<TextView>(R.id.textPedidos7)
        val textVentas14   = view.findViewById<TextView>(R.id.textVentas14)
        val textPedidos14  = view.findViewById<TextView>(R.id.textPedidos14)
        val textAvgDaily   = view.findViewById<TextView>(R.id.textAvgDaily)

        // Charts
        val chartDaily     = view.findViewById<BarChart>(R.id.chartDailySales)
        val chartRoutes    = view.findViewById<HorizontalBarChart>(R.id.chartRouteSales)
        val cardRouteChart = view.findViewById<MaterialCardView>(R.id.cardRouteChart)

        // Ranking containers
        val layoutTopProducts    = view.findViewById<LinearLayout>(R.id.layoutTopProducts)
        val layoutBottomProducts = view.findViewById<LinearLayout>(R.id.layoutBottomProducts)
        val layoutTopClients     = view.findViewById<LinearLayout>(R.id.layoutTopClients)

        btnRefresh.setOnClickListener { viewModel.refresh() }
        btnCrashReports.setOnClickListener {
            startActivity(Intent(requireContext(), CrashReportsActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ReportsViewModel.UiState.Loading -> {
                            progress.visibility     = View.VISIBLE
                            textEmpty.visibility    = View.GONE
                            layoutContent.visibility = View.GONE
                        }
                        is ReportsViewModel.UiState.Error -> {
                            progress.visibility     = View.GONE
                            textEmpty.visibility    = View.VISIBLE
                            layoutContent.visibility = View.GONE
                            textEmpty.text = state.message
                        }
                        is ReportsViewModel.UiState.Success -> {
                            progress.visibility = View.GONE
                            val data = state.data

                            if (!data.hasData) {
                                textEmpty.visibility    = View.VISIBLE
                                layoutContent.visibility = View.GONE
                                return@collect
                            }

                            textEmpty.visibility    = View.GONE
                            layoutContent.visibility = View.VISIBLE

                            // KPIs
                            textVentas7.text  = data.totalVentas7Formatted
                            textPedidos7.text = getString(R.string.reports_kpi_pedidos, data.totalPedidos7)
                            textVentas14.text = data.totalVentas14Formatted
                            textPedidos14.text = getString(R.string.reports_kpi_pedidos, data.totalPedidos14)
                            textAvgDaily.text = data.avgDailyFormatted

                            // Chart: Ventas por día
                            setupDailyChart(chartDaily, data.dailySales)

                            // Chart: Ventas por ruta
                            if (data.routeSales.size >= 2) {
                                cardRouteChart.visibility = View.VISIBLE
                                setupRouteChart(chartRoutes, data.routeSales)
                            } else {
                                cardRouteChart.visibility = View.GONE
                            }

                            // Rankings
                            buildRankingList(
                                container = layoutTopProducts,
                                items = data.topProducts.map { p ->
                                    RankingItem(
                                        name = p.productName,
                                        subtitle = getString(R.string.reports_product_subtitle, p.totalQty, p.totalRevenueFormatted),
                                        value = p.totalQty.toDouble(),
                                        imageUrl = p.imageUrl,
                                        imageLocalUri = p.imageLocalUri,
                                    )
                                },
                            )

                            buildRankingList(
                                container = layoutBottomProducts,
                                items = data.bottomProducts.map { p ->
                                    RankingItem(
                                        name = p.productName,
                                        subtitle = getString(R.string.reports_product_subtitle, p.totalQty, p.totalRevenueFormatted),
                                        value = p.totalQty.toDouble(),
                                        imageUrl = p.imageUrl,
                                        imageLocalUri = p.imageLocalUri,
                                    )
                                },
                            )

                            buildRankingList(
                                container = layoutTopClients,
                                items = data.topClients.map { c ->
                                    RankingItem(
                                        name = c.clientName,
                                        subtitle = getString(R.string.reports_client_subtitle, c.orderCount, c.totalSpentFormatted),
                                        value = c.totalSpent,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Charts Setup ──────────────────────────────────────────────────────────

    private fun setupDailyChart(chart: BarChart, dailySales: List<DailySalesUi>) {
        if (dailySales.isEmpty()) {
            chart.clear()
            chart.setNoDataText("Sin datos")
            return
        }

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.app_primary)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.app_secondary)

        val entries = dailySales.mapIndexed { index, day ->
            BarEntry(index.toFloat(), day.totalAmount.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = primaryColor
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.palette_800)
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1000) {
                        "Q${String.format(Locale.US, "%.0f", value / 1000)}k"
                    } else {
                        "Q${String.format(Locale.US, "%.0f", value)}"
                    }
                }
            }
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(requireContext(), R.color.palette_700)
                textSize = 10f
                valueFormatter = IndexAxisValueFormatter(
                    dailySales.map { it.dateLabel }
                )
                labelRotationAngle = -30f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = secondaryColor
                textColor = ContextCompat.getColor(requireContext(), R.color.palette_600)
                textSize = 10f
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 1000) "Q${(value / 1000).toInt()}k" else "Q${value.toInt()}"
                    }
                }
            }

            axisRight.isEnabled = false
            setExtraOffsets(4f, 8f, 4f, 12f)
            animateY(600)
            invalidate()
        }
    }

    private fun setupRouteChart(chart: HorizontalBarChart, routeSales: List<RouteSalesUi>) {
        if (routeSales.isEmpty()) {
            chart.clear()
            return
        }

        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.palette_600),
            ContextCompat.getColor(requireContext(), R.color.palette_700),
            ContextCompat.getColor(requireContext(), R.color.palette_800),
            ContextCompat.getColor(requireContext(), R.color.palette_300),
            ContextCompat.getColor(requireContext(), R.color.palette_900),
        )

        val entries = routeSales.mapIndexed { index, route ->
            BarEntry(index.toFloat(), route.totalAmount.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            setColors(colors.take(routeSales.size))
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.palette_800)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 1000) {
                        "Q${String.format(Locale.US, "%.1f", value / 1000)}k"
                    } else {
                        "Q${String.format(Locale.US, "%.0f", value)}"
                    }
                }
            }
        }

        // Ajustar altura dinámica según cantidad de rutas
        val heightDp = (routeSales.size * 48).coerceIn(120, 400)
        chart.layoutParams = chart.layoutParams.apply {
            height = (heightDp * resources.displayMetrics.density).toInt()
        }

        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.5f }
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = ContextCompat.getColor(requireContext(), R.color.palette_700)
                textSize = 11f
                valueFormatter = IndexAxisValueFormatter(
                    routeSales.map { it.routeName }
                )
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.app_secondary)
                textColor = ContextCompat.getColor(requireContext(), R.color.palette_600)
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value >= 1000) "Q${(value / 1000).toInt()}k" else "Q${value.toInt()}"
                    }
                }
            }

            axisRight.isEnabled = false
            setExtraOffsets(4f, 8f, 16f, 4f)
            animateX(600)
            invalidate()
        }
    }

    // ── Ranking list builder ──────────────────────────────────────────────────

    private data class RankingItem(
        val name: String,
        val subtitle: String,
        val value: Double,
        val imageUrl: String? = null,
        val imageLocalUri: String? = null,
    )

    private fun buildRankingList(container: LinearLayout, items: List<RankingItem>) {
        container.removeAllViews()

        if (items.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "—"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.palette_600))
            }
            container.addView(empty)
            return
        }

        val maxValue = items.maxOfOrNull { it.value } ?: 1.0
        val inflater = LayoutInflater.from(requireContext())

        items.forEachIndexed { index, item ->
            val itemView = inflater.inflate(R.layout.item_report_ranking, container, false)

            itemView.findViewById<TextView>(R.id.textRank).text = "${index + 1}"
            itemView.findViewById<TextView>(R.id.textName).text = item.name
            itemView.findViewById<TextView>(R.id.textSubtitle).text = item.subtitle

            // Product image (only shown if there's an image source)
            val imageView = itemView.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imageProduct)
            val remoteUrl = item.imageUrl?.trim()
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val localUri = item.imageLocalUri?.trim()?.takeIf { it.isNotEmpty() }

            val imageSource: Any? = when {
                remoteUrl != null -> remoteUrl
                localUri != null -> java.io.File(localUri)
                else -> null
            }

            if (imageSource != null) {
                imageView.visibility = View.VISIBLE
                com.bumptech.glide.Glide.with(imageView)
                    .load(imageSource)
                    .override(120, 120)
                    .centerCrop()
                    .placeholder(R.drawable.bg_rank_badge)
                    .into(imageView)
            } else {
                imageView.visibility = View.GONE
            }

            // Bar proporción: ancho relativo al máximo (60dp max)
            val bar = itemView.findViewById<View>(R.id.barProportion)
            val maxBarWidthDp = 60
            val proportion = if (maxValue > 0) (item.value / maxValue) else 0.0
            val barWidthDp = (maxBarWidthDp * proportion).toInt().coerceAtLeast(4)
            val params = bar.layoutParams
            params.width = (barWidthDp * resources.displayMetrics.density).toInt()
            bar.layoutParams = params

            // Color del badge: los 3 primeros con tono especial
            val badgeBg = when (index) {
                0 -> R.color.palette_600
                1 -> R.color.palette_700
                2 -> R.color.palette_800
                else -> R.color.palette_300
            }
            itemView.findViewById<TextView>(R.id.textRank)
                .background.setTint(ContextCompat.getColor(requireContext(), badgeBg))

            container.addView(itemView)
        }
    }
}




