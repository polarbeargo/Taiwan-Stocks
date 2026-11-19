package com.polarbeargo.twstocks.model

import java.math.BigDecimal

enum class PriceTone { UP, DOWN, NEUTRAL }

enum class SortOrder {
    CODE_DESCENDING, CODE_ASCENDING;

    fun apply(stocks: List<Stock>): List<Stock> = when (this) {
        CODE_DESCENDING -> stocks.sortedByDescending(Stock::code)
        CODE_ASCENDING -> stocks.sortedBy(Stock::code)
    }
}

data class Valuation(
    val peRatio: BigDecimal?,
    val dividendYield: BigDecimal?,
    val pbRatio: BigDecimal?,
)

data class Stock(
    val code: String,
    val name: String,
    val date: String?,
    val opening: BigDecimal?,
    val highest: BigDecimal?,
    val lowest: BigDecimal?,
    val closing: BigDecimal?,
    val change: BigDecimal?,
    val monthlyAverage: BigDecimal?,
    val transactions: BigDecimal?,
    val volume: BigDecimal?,
    val value: BigDecimal?,
    val valuation: Valuation?,
) {
    val closingTone: PriceTone
        get() = if (closing == null || monthlyAverage == null) {
            PriceTone.NEUTRAL
        } else {
            tone(closing.compareTo(monthlyAverage))
        }

    val changeTone: PriceTone
        get() = tone(change?.signum() ?: 0)
}

private fun tone(comparison: Int): PriceTone = when {
    comparison > 0 -> PriceTone.UP
    comparison < 0 -> PriceTone.DOWN
    else -> PriceTone.NEUTRAL
}
