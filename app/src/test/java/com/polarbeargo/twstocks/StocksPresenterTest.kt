package com.polarbeargo.twstocks

import com.polarbeargo.twstocks.data.StockParser
import com.polarbeargo.twstocks.data.StockRepository
import com.polarbeargo.twstocks.model.SortOrder
import com.polarbeargo.twstocks.model.Stock
import com.polarbeargo.twstocks.presentation.StocksPresenter
import com.polarbeargo.twstocks.presentation.StocksView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StocksPresenterTest {
    private val stock = StockParser.parse("[${trade()}]", "[${average()}]", "[${valuation()}]").single()

    @Test fun `default is descending and newest sort wins`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = StocksPresenter(
            object : StockRepository {
                override suspend fun load(forceRefresh: Boolean) = listOf(stock.copy(code = "0050"), stock)
            }, dispatcher, dispatcher, reportError = { throw AssertionError(it) },
        )
        val view = FakeView()
        presenter.attach(view)
        advanceUntilIdle()
        assertEquals(listOf("2330", "0050"), view.stocks.map { it.code })
        presenter.sort(SortOrder.CODE_DESCENDING)
        presenter.sort(SortOrder.CODE_ASCENDING)
        advanceUntilIdle()
        assertEquals(listOf("0050", "2330"), view.stocks.map { it.code })
        assertFalse(view.loading)
        presenter.select(stock)
        assertEquals(stock, view.selected)
        presenter.destroy()
    }

    @Test fun `refresh failure keeps displayed data and reports error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val errors = mutableListOf<Throwable>()
        val presenter = StocksPresenter(
            object : StockRepository {
                override suspend fun load(forceRefresh: Boolean): List<Stock> {
                    if (forceRefresh) throw IOException("offline")
                    return listOf(stock)
                }
            }, dispatcher, dispatcher, reportError = errors::add,
        )
        val view = FakeView()
        presenter.attach(view)
        advanceUntilIdle()
        presenter.refresh()
        advanceUntilIdle()
        assertEquals(listOf(stock), view.stocks)
        assertEquals(true, view.errorHadStocks)
        assertEquals(1, errors.size)
        assertFalse(view.loading)
        presenter.destroy()
    }

    @Test fun `initial error allows retry and loading finishes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fail = true
        val presenter = StocksPresenter(
            object : StockRepository {
                override suspend fun load(forceRefresh: Boolean): List<Stock> {
                    if (fail) throw IOException("offline")
                    return listOf(stock)
                }
            }, dispatcher, dispatcher, reportError = {},
        )
        val view = FakeView()
        presenter.attach(view)
        advanceUntilIdle()
        assertEquals(false, view.errorHadStocks)
        assertFalse(view.loading)
        fail = false
        presenter.refresh()
        advanceUntilIdle()
        assertEquals(listOf(stock), view.stocks)
        presenter.destroy()
    }

    @Test fun `destroy cancels work and never calls detached view`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancelled = CompletableDeferred<Unit>()
        val presenter = StocksPresenter(
            object : StockRepository {
                override suspend fun load(forceRefresh: Boolean): List<Stock> {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            }, dispatcher, dispatcher, reportError = { throw AssertionError(it) },
        )
        val view = FakeView()
        presenter.attach(view)
        runCurrent()
        val before = view.callbacks
        presenter.destroy()
        advanceUntilIdle()
        assertTrue(cancelled.isCompleted)
        assertEquals(before, view.callbacks)
        assertNull(view.errorHadStocks)
    }

    @Test fun `replaced load cannot hide loading indicator or report cancellation as error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val response = CompletableDeferred<List<Stock>>()
        var calls = 0
        val presenter = StocksPresenter(
            object : StockRepository {
                override suspend fun load(forceRefresh: Boolean): List<Stock> {
                    calls++
                    return response.await()
                }
            }, dispatcher, dispatcher, reportError = { throw AssertionError(it) },
        )
        val view = FakeView()
        presenter.attach(view)
        runCurrent()
        presenter.refresh()
        presenter.sort(SortOrder.CODE_ASCENDING)
        runCurrent()
        assertEquals(2, calls)
        assertTrue(view.loading)
        assertEquals(0, view.stockRenders)
        response.complete(listOf(stock))
        advanceUntilIdle()
        assertFalse(view.loading)
        assertEquals(listOf(stock), view.stocks)
        assertNull(view.errorHadStocks)
        presenter.destroy()
    }

    private class FakeView : StocksView {
        var stocks = emptyList<Stock>()
        var loading = false
        var errorHadStocks: Boolean? = null
        var selected: Stock? = null
        var callbacks = 0
        var stockRenders = 0
        override fun showLoading(loading: Boolean) { callbacks++; this.loading = loading }
        override fun showStocks(stocks: List<Stock>, order: SortOrder) {
            callbacks++
            stockRenders++
            this.stocks = stocks
        }
        override fun showError(hasStocks: Boolean) { callbacks++; errorHadStocks = hasStocks }
        override fun showValuation(stock: Stock) { callbacks++; selected = stock }
    }
}
