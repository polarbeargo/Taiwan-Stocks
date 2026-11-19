package com.polarbeargo.twstocks

import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import com.polarbeargo.twstocks.ui.StockAdapter
import com.polarbeargo.twstocks.ui.StocksActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StockHolderTest {
    @Before fun setup() = fixtureRepository().reset()

    @Test fun sameHolderResetsEveryValueAndColorAcrossUpDownNeutralAndMissing() {
        ActivityScenario.launch(StocksActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val adapter = StockAdapter {}
                val parent = FrameLayout(activity)
                val holder = adapter.onCreateViewHolder(parent, 0)
                val stocks = fixtureRepository().stocks
                val unavailable = activity.getString(R.string.unavailable)
                val cases = listOf(
                    Triple(stocks[0], R.color.price_up, "+1.25"),
                    Triple(stocks[2], R.color.price_down, "-10.00"),
                    Triple(stocks[1], R.color.text_primary, "0.00"),
                    Triple(stocks[0].copy(
                        name = "Missing", closing = null, change = null, monthlyAverage = null,
                        opening = null, highest = null, lowest = null, date = null,
                        transactions = null, volume = null, value = null,
                    ), R.color.text_primary, unavailable),
                    Triple(stocks[0], R.color.price_up, "+1.25"),
                )
                for ((stock, color, change) in cases) {
                    holder.bind(stock)
                    assertEquals(stock.code, holder.itemView.findViewById<TextView>(R.id.code).text.toString())
                    assertEquals(stock.name, holder.itemView.findViewById<TextView>(R.id.name).text.toString())
                    for (id in listOf(R.id.closing, R.id.change)) {
                        assertEquals(ContextCompat.getColor(activity, color),
                            holder.itemView.findViewById<TextView>(id).currentTextColor)
                    }
                    assertEquals(change, holder.itemView.findViewById<TextView>(R.id.change).text.toString())
                    assertEquals(stock.closing?.setScale(2)?.toPlainString() ?: unavailable,
                        holder.itemView.findViewById<TextView>(R.id.closing).text.toString())
                    if (stock.closing == null) {
                        for (id in listOf(R.id.opening, R.id.highest, R.id.lowest, R.id.closing,
                            R.id.monthlyAverage, R.id.transactions, R.id.volume, R.id.value)) {
                            assertEquals(unavailable, holder.itemView.findViewById<TextView>(id).text.toString())
                        }
                    } else {
                        assertEquals("960.00", holder.itemView.findViewById<TextView>(R.id.opening).text.toString())
                        assertEquals("1,000", holder.itemView.findViewById<TextView>(R.id.transactions).text.toString())
                        assertEquals("1,234,567", holder.itemView.findViewById<TextView>(R.id.volume).text.toString())
                        assertEquals("1,234,567,890", holder.itemView.findViewById<TextView>(R.id.value).text.toString())
                    }
                }
            }
        }
    }
}
