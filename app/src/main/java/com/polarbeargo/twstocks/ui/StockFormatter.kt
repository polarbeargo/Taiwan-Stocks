package com.polarbeargo.twstocks.ui

import androidx.annotation.MainThread
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Owned by a main-thread view/adapter, never shared across threads.
@MainThread
class StockFormatter(private val unavailable: String) {
    private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).apply {
        maximumFractionDigits = 340
    }
    private val integerFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

    fun price(value: BigDecimal?): String = value?.let(priceFormat::format) ?: unavailable

    fun change(value: BigDecimal?): String =
        if (value != null && value.signum() > 0) "+${price(value)}" else price(value)

    fun count(value: BigDecimal?): String = value?.let(integerFormat::format) ?: unavailable

    fun date(rocDate: String?): String {
        if (rocDate == null || !rocDate.matches(Regex("\\d{7}"))) return rocDate ?: unavailable
        val year = rocDate.take(3).toInt() + 1911
        return "$year/${rocDate.substring(3, 5)}/${rocDate.substring(5, 7)}"
    }
}
