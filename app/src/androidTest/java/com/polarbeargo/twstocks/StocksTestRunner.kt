package com.polarbeargo.twstocks

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.polarbeargo.twstocks.data.StockRepository
import com.polarbeargo.twstocks.model.Stock
import com.polarbeargo.twstocks.model.Valuation
import java.math.BigDecimal

class StocksTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, FixtureApplication::class.java.name, context)
}

class FixtureApplication : StocksApplication() {
    override val repository: StockRepository = object : StockRepository {
        override suspend fun load(forceRefresh: Boolean): List<Stock> =
            listOf(
                stock("0050", "元大台灣50", "55", "50", "1.25"),
                stock("1101", "台泥", "30", "30", "0"),
                stock("2330", "台積電", "950", "1000", "-10"),
            )
    }

    private fun stock(code: String, name: String, close: String, average: String, change: String) = Stock(
        code = code,
        name = name,
        date = "1151118",
        opening = BigDecimal("960"),
        highest = BigDecimal("980"),
        lowest = BigDecimal("940"),
        closing = BigDecimal(close),
        change = BigDecimal(change),
        monthlyAverage = BigDecimal(average),
        transactions = BigDecimal("1000"),
        volume = BigDecimal("1234567"),
        value = BigDecimal("1234567890"),
        valuation = if (code == "0050") null else Valuation(
            BigDecimal("20.25"), BigDecimal("2.50"), BigDecimal("5.10"),
        ),
    )
}
