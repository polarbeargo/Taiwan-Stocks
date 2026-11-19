package com.polarbeargo.twstocks

import com.polarbeargo.twstocks.data.MarketDataException
import com.polarbeargo.twstocks.data.StockParser
import com.polarbeargo.twstocks.model.PriceTone
import com.polarbeargo.twstocks.model.SortOrder
import com.polarbeargo.twstocks.ui.StockFormatter
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class StockParserTest {
    @Test fun `joins by code rather than array position and keeps trades without valuations`() {
        val stocks = StockParser.parse(
            """[${trade("2330")},${trade("0050")}]""",
            """[${average("0050", "9")},${average("2330", "11")}]""",
            """[${valuation("2330")}]""",
        )
        assertEquals(2, stocks.size)
        assertEquals(BigDecimal("11"), stocks[0].monthlyAverage)
        assertEquals(BigDecimal("9"), stocks[1].monthlyAverage)
        assertNotNull(stocks[0].valuation)
        assertNull(stocks[1].valuation)
        assertEquals("0050", stocks[1].code)
    }

    @Test fun `prices and changes use exact decimal comparison`() {
        val stock = parse(close = "10.0001", avg = "10.0000", change = "-0.0001")
        assertEquals(PriceTone.UP, stock.closingTone)
        assertEquals(PriceTone.DOWN, stock.changeTone)
        assertEquals(PriceTone.NEUTRAL, parse(close = "10.00", avg = "10", change = "0").closingTone)
        assertEquals(PriceTone.NEUTRAL, parse(change = "0").changeTone)
        assertEquals(PriceTone.DOWN, parse(close = "9", avg = "10").closingTone)
        assertEquals(PriceTone.UP, parse(change = "+1.2").changeTone)
    }

    @Test fun `handles comma grouping unicode minus and unavailable fields without zero defaults`() {
        val stock = parse(close = "--", change = "−1.25")
        assertNull(stock.closing)
        assertEquals(PriceTone.NEUTRAL, stock.closingTone)
        assertEquals(BigDecimal("-1.25"), stock.change)
        assertEquals(BigDecimal("123456789012"), stock.value)
    }

    @Test fun `does not combine datasets from different trading dates`() {
        val stock = StockParser.parse(
            "[${trade()}]",
            """[{"Code":"2330","Date":"1151117","MonthlyAveragePrice":"1"}]""",
            """[{"Code":"2330","Date":"1151117","PEratio":"1"}]""",
        ).single()
        assertNull(stock.monthlyAverage)
        assertNull(stock.valuation)
        assertEquals(PriceTone.NEUTRAL, stock.closingTone)
    }

    @Test fun `sorts security identifiers preserving zeros and suffixes`() {
        val base = parse()
        val stocks = listOf("2330", "0050", "00632R", "1101").map { base.copy(code = it) }
        assertEquals(
            listOf("0050", "00632R", "1101", "2330"),
            SortOrder.CODE_ASCENDING.apply(stocks).map { it.code },
        )
        assertEquals("2330", SortOrder.CODE_DESCENDING.apply(stocks).first().code)
    }

    @Test fun `formatter keeps full grouped counts signs and ROC trading date`() {
        val formatter = StockFormatter("—")
        assertEquals("123,456,789,012", formatter.count(BigDecimal("123456789012")))
        assertEquals("+0.0001", formatter.change(BigDecimal("0.0001")))
        assertEquals("10.00", formatter.price(BigDecimal.TEN))
        assertEquals("—", formatter.price(null))
        assertEquals("2026/11/18", formatter.date("1151118"))
    }

    @Test(expected = MarketDataException::class)
    fun `rejects invalid numbers instead of showing invented data`() {
        parse(close = "not-a-price")
    }

    @Test(expected = MarketDataException::class)
    fun `rejects empty required datasets`() {
        StockParser.parse("[]", "[${average()}]", "[${valuation()}]")
    }

    @Test(expected = MarketDataException::class)
    fun `rejects duplicate security codes`() {
        StockParser.parse("[${trade()},${trade()}]", "[${average()}]", "[${valuation()}]")
    }

    private fun parse(close: String = "10", avg: String = "11", change: String = "1") =
        StockParser.parse("[${trade(close = close, change = change)}]", "[${average(avg = avg)}]", "[${valuation()}]").single()
}

internal fun trade(code: String = "2330", close: String = "10", change: String = "1") = """
    {"Date":"1151118","Code":"$code","Name":"台積電","OpeningPrice":"9","HighestPrice":"11",
     "LowestPrice":"8","ClosingPrice":"$close","Change":"$change","TradeVolume":"1,000",
     "TradeValue":"123,456,789,012","Transaction":"100"}
""".trimIndent()

internal fun average(code: String = "2330", avg: String = "11") =
    """{"Date":"1151118","Code":"$code","MonthlyAveragePrice":"$avg"}"""

internal fun valuation(code: String = "2330") =
    """{"Date":"1151118","Code":"$code","PEratio":"20.25","DividendYield":"2.00","PBratio":"5.10"}"""
