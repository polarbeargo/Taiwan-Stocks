package com.polarbeargo.twstocks

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.polarbeargo.twstocks.ui.StocksActivity
import leakcanary.AppWatcher
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StocksLeakTest {
    @get:Rule val leaks = DetectLeaksAfterTestSuccess()

    @Before fun setup() = fixtureRepository().reset()

    @Test fun destroyedScreensAndAdaptersAreCollectibleAfterDialogsAndRecreation() {
        assertTrue("Leak watcher must be installed", AppWatcher.isInstalled)
        repeat(3) { exerciseLifecycle(it) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("leak_workload", "3 launch/recreate/finish cycles, open sheet and valuation dialog")
            putInt("explicitly_watched_objects", 18)
        })
    }

    private fun exerciseLifecycle(cycle: Int) {
        ActivityScenario.launch(StocksActivity::class.java).use { scenario ->
            awaitStock("2330")
            onView(withId(R.id.sort)).perform(click())
            scenario.onActivity { activity ->
                watchScreen(activity, "$cycle-before-recreate")
            }
            scenario.recreate()
            awaitStock("2330")
            onView(withId(R.id.stocks)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()),
            )
            onView(withId(R.id.peRatio)).inRoot(isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(withText("20.25")))
            scenario.onActivity { activity -> watchScreen(activity, "$cycle-before-finish") }
        }
    }

    private fun watchScreen(activity: StocksActivity, tag: String) {
        AppWatcher.objectWatcher.expectWeaklyReachable(activity, "Destroyed Activity $tag")
        val recycler = activity.findViewById<RecyclerView>(R.id.stocks)
        AppWatcher.objectWatcher.expectWeaklyReachable(recycler, "Destroyed RecyclerView $tag")
        AppWatcher.objectWatcher.expectWeaklyReachable(
            requireNotNull(recycler.adapter), "Detached adapter $tag",
        )
    }
}
