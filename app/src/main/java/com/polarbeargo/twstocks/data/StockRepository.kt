package com.polarbeargo.twstocks.data

import com.polarbeargo.twstocks.model.Stock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface StockRepository {
    suspend fun load(forceRefresh: Boolean): List<Stock>
}

class TwseStockRepository(
    private val service: TwseService,
    private val ioDispatcher: CoroutineDispatcher,
    private val nanoTime: () -> Long = System::nanoTime,
) : StockRepository {
    private val mutex = Mutex()
    private var cached: List<Stock>? = null
    private var cachedAt = 0L

    override suspend fun load(forceRefresh: Boolean): List<Stock> = mutex.withLock {
        val now = nanoTime()
        cached?.let {
            if (!forceRefresh && now - cachedAt < CACHE_NANOS) return@withLock it
        }
        val stocks = withContext(ioDispatcher) {
            coroutineScope {
                val trades = async { service.get("STOCK_DAY_ALL") }
                val averages = async { service.get("STOCK_DAY_AVG_ALL") }
                val valuations = async { service.get("BWIBBU_ALL") }
                StockParser.parse(trades.await(), averages.await(), valuations.await())
            }
        }
        cached = stocks
        cachedAt = nanoTime()
        stocks
    }

    private companion object {
        const val CACHE_NANOS = 60_000_000_000L
    }
}
