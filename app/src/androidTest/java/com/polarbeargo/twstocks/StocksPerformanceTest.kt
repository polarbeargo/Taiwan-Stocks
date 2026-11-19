package com.polarbeargo.twstocks

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import com.polarbeargo.twstocks.data.StockParser
import com.polarbeargo.twstocks.model.SortOrder
import com.polarbeargo.twstocks.ui.StocksActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil

class StocksPerformanceTest {
    @Before fun setup() = fixtureRepository().reset()
    @After fun cleanup() = fixtureRepository().reset()

    @Test fun measureParseJoinAndSortFor1391Securities() {
        val codes = (0 until 1391).map { it.toString().padStart(5, '0') }
        val trades = codes.joinToString(",", "[", "]") {
            """{"Code":"$it","Name":"Stock $it","Date":"1151118","ClosingPrice":"10.0001","Change":"-0.01","TradeVolume":"1000000"}"""
        }
        val averages = codes.reversed().joinToString(",", "[", "]") {
            """{"Code":"$it","Date":"1151118","MonthlyAveragePrice":"10"}"""
        }
        val valuations = codes.joinToString(",", "[", "]") {
            """{"Code":"$it","Date":"1151118","PEratio":"20.25","DividendYield":"2.50","PBratio":"1.24"}"""
        }
        fun runWorkload(): Long {
            val start = SystemClock.elapsedRealtimeNanos()
            val sorted = SortOrder.CODE_DESCENDING.apply(StockParser.parse(trades, averages, valuations))
            val elapsed = SystemClock.elapsedRealtimeNanos() - start
            assertEquals(codes.reversed(), sorted.map { it.code })
            assertTrue(sorted.all { it.monthlyAverage != null && it.valuation?.peRatio != null })
            return elapsed
        }
        repeat(5) { runWorkload() }
        val samples = List(30) { runWorkload() }.sorted()
        val p95 = percentile(samples, 0.95)
        report(Bundle().apply {
            putInt("parse_join_sort_securities", codes.size)
            putInt("parse_join_sort_samples", samples.size)
            putDouble("parse_join_sort_median_ms", percentile(samples, 0.5) / 1e6)
            putDouble("parse_join_sort_p95_ms", p95 / 1e6)
        })
        assertTrue("1391-row parse/join/sort p95 ${p95 / 1e6} ms exceeds 2000 ms smoke budget",
            p95 < 2_000_000_000L)
    }

    @Test fun measureScrollFrameDurationsFor1391Cards() {
        val repository = fixtureRepository()
        repository.controlled = true
        val frames = ConcurrentLinkedQueue<Long>()
        val droppedCallbacks = java.util.concurrent.atomic.AtomicInteger()
        val thread = HandlerThread("frame-metrics").apply { start() }
        val handler = Handler(thread.looper)
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
            droppedCallbacks.addAndGet(dropped)
        }
        try {
            ActivityScenario.launch(StocksActivity::class.java).use { scenario ->
                val base = repository.stocks.last()
                repository.nextRequest().response.complete((0 until 1391).map {
                    base.copy(code = it.toString().padStart(5, '0'))
                })
                awaitStock("01390")
                scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, handler) }
                try {
                    repeat(4) {
                        onView(withId(R.id.stocks)).perform(swipeUp())
                    }
                    repeat(4) {
                        onView(withId(R.id.stocks)).perform(swipeDown())
                    }
                } finally {
                    scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                }
            }
        } finally {
            thread.quitSafely()
            thread.join(5_000)
            assertFalse("Frame metrics thread did not terminate", thread.isAlive)
        }
        val samples = frames.toList().sorted()
        assertTrue("Expected at least ten measured frames, got ${samples.size}", samples.size >= 10)
        report(Bundle().apply {
            putInt("scroll_frame_samples", samples.size)
            putInt("scroll_dropped_metric_callbacks", droppedCallbacks.get())
            putDouble("scroll_frame_median_ms", percentile(samples, 0.5) / 1e6)
            putDouble("scroll_frame_p95_ms", percentile(samples, 0.95) / 1e6)
            putInt("scroll_frames_over_16_67_ms", samples.count { it > 16_666_667L })
        })
    }

    private fun percentile(sorted: List<Long>, fraction: Double) =
        sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceAtLeast(0)]

    private fun report(metrics: Bundle) {
        android.util.Log.i("StocksPerformance", metrics.toString())
        InstrumentationRegistry.getInstrumentation().sendStatus(0, metrics)
    }
}
