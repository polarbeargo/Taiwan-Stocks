package com.polarbeargo.twstocks.data

import com.polarbeargo.twstocks.model.Stock
import com.polarbeargo.twstocks.model.Valuation
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal

class MarketDataException(message: String) : IOException(message)

object StockParser {
    fun parse(tradesJson: String, averagesJson: String, valuationsJson: String): List<Stock> {
        val trades = rows(tradesJson)
        val averages = rows(averagesJson)
        val valuations = rows(valuationsJson)
        if (trades.isEmpty() || averages.isEmpty() || valuations.isEmpty()) {
            throw MarketDataException("A required TWSE dataset is empty")
        }
        return trades.map { (code, trade) ->
            // TWSE can publish endpoints at different times. Never join different trading dates.
            val average = averages[code]?.takeIf { sameDate(trade, it) }
            val valuation = valuations[code]?.takeIf { sameDate(trade, it) }
            Stock(
                code = code,
                name = trade.getString("Name").also {
                    if (it.isBlank()) throw MarketDataException("Missing name for $code")
                },
                date = trade.text("Date"),
                opening = trade.number("OpeningPrice"),
                highest = trade.number("HighestPrice"),
                lowest = trade.number("LowestPrice"),
                closing = trade.number("ClosingPrice"),
                change = trade.number("Change"),
                monthlyAverage = average?.number("MonthlyAveragePrice"),
                transactions = trade.number("Transaction"),
                volume = trade.number("TradeVolume"),
                value = trade.number("TradeValue"),
                valuation = valuation?.let {
                    Valuation(it.number("PEratio"), it.number("DividendYield"), it.number("PBratio"))
                },
            )
        }
    }

    private fun sameDate(left: JSONObject, right: JSONObject): Boolean =
        left.text("Date") == right.text("Date")

    private fun rows(json: String): Map<String, JSONObject> {
        val array = JSONArray(json)
        return buildMap(array.length()) {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                val code = row.getString("Code")
                if (code.isBlank() || put(code, row) != null) {
                    throw MarketDataException("Missing or duplicate security code: $code")
                }
            }
        }
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.number(key: String): BigDecimal? {
        val raw = text(key) ?: return null
        val normalized = raw.replace(",", "").replace("\u2212", "-").trim()
        if (normalized in setOf("--", "---", "-", "N/A", "不適用")) return null
        return normalized.toBigDecimalOrNull()
            ?: throw MarketDataException("Invalid numeric field $key: $raw")
    }
}
