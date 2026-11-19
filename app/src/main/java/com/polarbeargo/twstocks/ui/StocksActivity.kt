package com.polarbeargo.twstocks.ui

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.polarbeargo.twstocks.R
import com.polarbeargo.twstocks.StocksApplication
import com.polarbeargo.twstocks.databinding.ActivityStocksBinding
import com.polarbeargo.twstocks.databinding.DialogValuationBinding
import com.polarbeargo.twstocks.databinding.SheetSortBinding
import com.polarbeargo.twstocks.model.SortOrder
import com.polarbeargo.twstocks.model.Stock
import com.polarbeargo.twstocks.presentation.StocksPresenter
import com.polarbeargo.twstocks.presentation.StocksView
import kotlinx.coroutines.Dispatchers

class StocksActivity : AppCompatActivity(), StocksView {
    private lateinit var binding: ActivityStocksBinding
    private lateinit var presenter: StocksPresenter
    private lateinit var adapter: StockAdapter
    private var activeDialog: Dialog? = null
    private var errorSnackbar: Snackbar? = null
    private var displayedOrder: SortOrder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityStocksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightNavigationBars =
            resources.getBoolean(R.bool.light_system_bars)
        ViewCompat.setAccessibilityHeading(binding.title, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val savedOrder = savedInstanceState?.getString(SORT_ORDER)
        val order = SortOrder.entries.firstOrNull { it.name == savedOrder } ?: SortOrder.CODE_DESCENDING
        presenter = StocksPresenter(
            (application as StocksApplication).repository,
            Dispatchers.Main.immediate,
            Dispatchers.Default,
            order,
        ) { Log.e("TWSE", "Unable to load market data", it) }
        adapter = StockAdapter(presenter::select)
        binding.stocks.adapter = adapter
        binding.refresh.setOnClickListener { presenter.refresh() }
        binding.retry.setOnClickListener { presenter.refresh() }
        binding.sort.setOnClickListener { showSortSheet() }
        presenter.attach(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SORT_ORDER, presenter.order.name)
        super.onSaveInstanceState(outState)
    }

    override fun showLoading(loading: Boolean) = with(binding) {
        refresh.isEnabled = !loading
        if (loading) {
            errorSnackbar?.dismiss()
            statusGroup.isVisible = adapter.currentList.isEmpty()
            progress.isVisible = true
            status.setText(R.string.loading)
            retry.isVisible = false
        } else {
            progress.isVisible = false
        }
    }

    override fun showStocks(stocks: List<Stock>, order: SortOrder) {
        val orderChanged = displayedOrder != null && displayedOrder != order
        displayedOrder = order
        adapter.submitList(stocks) {
            if (orderChanged) binding.stocks.scrollToPosition(0)
        }
        binding.statusGroup.isVisible = stocks.isEmpty()
        binding.status.setText(R.string.empty)
        binding.retry.isVisible = stocks.isEmpty()
        val formatter = StockFormatter(getString(R.string.unavailable))
        val dates = stocks.map(Stock::date).distinct()
        val date = if (dates.size == 1) formatter.date(dates.single()) else getString(R.string.mixed_dates)
        binding.summary.text = getString(R.string.market_summary, stocks.size, date)
        binding.sort.contentDescription = getString(
            if (order == SortOrder.CODE_DESCENDING) R.string.sort_descending else R.string.sort_ascending,
        )
    }

    override fun showError(hasStocks: Boolean) {
        if (hasStocks) {
            errorSnackbar = Snackbar.make(binding.root, R.string.refresh_error, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.retry) { presenter.refresh() }
                .also { it.show() }
        } else {
            binding.statusGroup.isVisible = true
            binding.status.setText(R.string.load_error)
            binding.retry.isVisible = true
        }
    }

    private fun showSortSheet() {
        activeDialog?.dismiss()
        val sheet = SheetSortBinding.inflate(layoutInflater)
        ViewCompat.setAccessibilityHeading(sheet.title, true)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        sheet.options.check(
            if (presenter.order == SortOrder.CODE_DESCENDING) R.id.descending else R.id.ascending,
        )
        sheet.options.setOnCheckedChangeListener { _, checkedId ->
            presenter.sort(
                if (checkedId == R.id.ascending) SortOrder.CODE_ASCENDING else SortOrder.CODE_DESCENDING,
            )
            dialog.dismiss()
        }
        activeDialog = dialog
        dialog.show()
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun showValuation(stock: Stock) {
        activeDialog?.dismiss()
        val content = DialogValuationBinding.inflate(layoutInflater)
        val formatter = StockFormatter(getString(R.string.unavailable))
        content.peRatio.text = formatter.price(stock.valuation?.peRatio)
        content.dividendYield.text = formatter.price(stock.valuation?.dividendYield)
        content.pbRatio.text = formatter.price(stock.valuation?.pbRatio)
        activeDialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.valuation_title, stock.code, stock.name))
            .setView(content.root)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    override fun onDestroy() {
        presenter.destroy()
        activeDialog?.dismiss()
        activeDialog = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        binding.stocks.adapter = null
        super.onDestroy()
    }

    private companion object {
        const val SORT_ORDER = "sort_order"
    }
}
