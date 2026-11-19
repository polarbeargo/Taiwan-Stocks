package com.polarbeargo.twstocks.presentation

import androidx.annotation.MainThread
import com.polarbeargo.twstocks.data.StockRepository
import com.polarbeargo.twstocks.model.SortOrder
import com.polarbeargo.twstocks.model.Stock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

interface StocksView {
    fun showLoading(loading: Boolean)
    fun showStocks(stocks: List<Stock>, order: SortOrder)
    fun showError(hasStocks: Boolean)
    fun showValuation(stock: Stock)
}

@MainThread
class StocksPresenter(
    private val repository: StockRepository,
    private val mainDispatcher: CoroutineDispatcher,
    private val computationDispatcher: CoroutineDispatcher,
    initialOrder: SortOrder = SortOrder.CODE_DESCENDING,
    private val reportError: (Throwable) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var view: StocksView? = null
    private var loadJob: Job? = null
    private var sortJob: Job? = null
    private var stocks = emptyList<Stock>()
    var order: SortOrder = initialOrder
        private set

    fun attach(view: StocksView) {
        this.view = view
        load(false)
    }

    fun refresh() = load(true)

    fun sort(order: SortOrder) {
        this.order = order
        if (stocks.isNotEmpty()) renderStocks()
    }

    fun select(stock: Stock) {
        view?.showValuation(stock)
    }

    fun destroy() {
        view = null
        scope.cancel()
    }

    private fun load(force: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            view?.showLoading(true)
            try {
                stocks = repository.load(force)
                renderStocks()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                failed(error)
            } catch (error: JSONException) {
                failed(error)
            } finally {
                if (currentCoroutineContext().isActive) view?.showLoading(false)
            }
        }
    }

    private fun failed(error: Throwable) {
        reportError(error)
        view?.showError(stocks.isNotEmpty())
    }

    private fun renderStocks() {
        sortJob?.cancel()
        val selectedOrder = order
        val snapshot = stocks
        sortJob = scope.launch {
            val sorted = withContext(computationDispatcher) { selectedOrder.apply(snapshot) }
            view?.showStocks(sorted, selectedOrder)
        }
    }
}
