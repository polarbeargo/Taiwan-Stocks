package com.polarbeargo.twstocks

import com.polarbeargo.twstocks.data.MarketDataException
import com.polarbeargo.twstocks.data.StockParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class StockParserContractTest {
    private val invalidStrings = listOf("null", "\"\"", "\"  \"", "123", "true", "[]", "{}")
    private val trade = """{"Code":"0050","Name":"ETF","ClosingPrice":"10.0001","Change":"-0.0001"}"""
    private val average = """{"Code":"0050","MonthlyAveragePrice":"10"}"""
    private val valuation = """{"Code":"0050","PEratio":"--","DividendYield":null,"PBratio":"1.25"}"""

    @Test fun requiredCodeIsStrictInEveryDataset() {
        for (dataset in 0..2) {
            for (invalid in invalidStrings + listOf<String?>(null)) {
                val rows = mutableListOf(trade, average, valuation)
                rows[dataset] = if (invalid == null) {
                    rows[dataset].replace("\"Code\":\"0050\",", "")
                } else {
                    rows[dataset].replace("\"Code\":\"0050\"", "\"Code\":$invalid")
                }
                assertRejected(rows, "dataset=$dataset Code=$invalid")
            }
        }
    }

    @Test fun requiredNameRejectsNullMissingBlankAndNonStrings() {
        for (invalid in invalidStrings + listOf<String?>(null)) {
            val row = if (invalid == null) trade.replace("\"Name\":\"ETF\",", "")
            else trade.replace("\"Name\":\"ETF\"", "\"Name\":$invalid")
            assertRejected(listOf(row, average, valuation), "Name=$invalid")
        }
    }

    @Test fun validStringsPrecisionAndUnavailableValuesHaveTheSameContract() {
        val stock = StockParser.parse("[$trade]", "[$average]", "[$valuation]").single()
        assertEquals("0050", stock.code)
        assertEquals("ETF", stock.name)
        assertEquals(BigDecimal("10.0001"), stock.closing)
        assertEquals(BigDecimal("-0.0001"), stock.change)
        val ratios = requireNotNull(stock.valuation)
        assertNull(ratios.peRatio)
        assertNull(ratios.dividendYield)
        assertEquals(BigDecimal("1.25"), ratios.pbRatio)
    }

    private fun assertRejected(rows: List<String>, message: String) {
        try {
            StockParser.parse("[${rows[0]}]", "[${rows[1]}]", "[${rows[2]}]")
            fail("Accepted invalid input: $message")
        } catch (_: MarketDataException) {
            // Both JSON runtimes must surface the application's validation error.
        }
    }
}
